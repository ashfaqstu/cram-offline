package dev.omnitalk.rag

import dev.omnitalk.Native
import dev.omnitalk.Topology
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors

/**
 * Retrieval-augmented answering over one document.
 *
 * THREADING: llama.cpp is driven from a single dedicated thread, because GGML's
 * worker pool inherits the affinity of whoever created it and the pool is built
 * at load time. Every native call for the model must come from this dispatcher.
 *
 * THREAD COUNTS come from the sweep in bench/results: decode peaks at 6 threads
 * and *collapses 58%* at 8, while prefill keeps scaling to 8. llama.cpp exposes
 * both, so we use each at its own optimum.
 */
class RagEngine {

    private val exec = Executors.newSingleThreadExecutor { Thread(it, "sift-llm") }
    private val disp = exec.asCoroutineDispatcher()

    private var llm = 0L
    private var index: Bm25? = null
    var doc: Document? = null; private set
    var lastTimings: String = "{}"; private set
    var lastRetrievalMs: Long = 0; private set
    /** Passage characters actually sent to the model on the last question. */
    var lastPromptChars: Int = 0; private set

    val ready: Boolean get() = llm != 0L

    /** Measured prefill throughput, tokens/sec. Drives the prompt budget. */
    var prefillTps: Double = 0.0; private set
    /** Characters of passage text this phone can afford. Calibrated, then user-adjustable. */
    var charBudget: Int = PASSAGE_CHAR_BUDGET_DEFAULT
    var maxAnswerTokens: Int = MAX_ANSWER_TOKENS
    var decodeThreads: Int = 6; private set
    var prefillThreads: Int = 8; private set
    var threadsUsed: String = ""; private set

    suspend fun load(
        modelPath: String,
        topo: Topology,
        nCtx: Int = N_CTX,
        decodeOverride: Int? = null,
        prefillOverride: Int? = null
    ): Boolean = withContext(disp) {
        if (llm != 0L) return@withContext true

        // Thread counts from the device, not from constants tuned to one phone.
        // Measured on 2 big + 6 LITTLE: decode peaks at 6 and collapses 58% at 8,
        // because every layer ends in a barrier and the fast cores wait on the
        // slow ones. Leaving two cores out avoids that cliff on any big.LITTLE
        // part; prefill is compute-bound and does scale, so it gets everything.
        val decode = decodeOverride
            ?: if (topo.cores >= 8) topo.cores - 2 else maxOf(1, topo.cores - 1)
        val prefill = prefillOverride ?: topo.cores.coerceAtLeast(1)
        decodeThreads = decode
        prefillThreads = prefill
        threadsUsed = "$decode decode / $prefill prefill"

        llm = Native.llmLoad(modelPath, nCtx, decode, prefill)
        if (llm == 0L) return@withContext false

        calibrate()
        true
    }

    /**
     * Time a short prefill, then size the prompt so the wait stays near
     * [TARGET_FIRST_WORD_MS].
     *
     * Hard-coded budgets are wrong on any phone but the one they were tuned on.
     * A device with i8mm (where KleidiAI's kernels actually load) prefills far
     * faster and can afford more context; a weaker one must send less or the user
     * waits half a minute. Measuring costs about a second, once, at startup.
     */
    private fun calibrate() {
        val probe = CALIBRATION_TEXT
        Native.llmResetKv(llm)
        val t0 = System.nanoTime()
        val tokens = Native.llmPrefill(llm, probe)
        val ms = (System.nanoTime() - t0) / 1e6
        Native.llmResetKv(llm)

        if (tokens > 0 && ms > 0) {
            prefillTps = tokens * 1000.0 / ms
            // ~3.6 chars/token for English prose; exact counting would need a
            // tokenizer round-trip per candidate and buys nothing here.
            val affordableTokens = prefillTps * (TARGET_FIRST_WORD_MS / 1000.0)
            charBudget = (affordableTokens * 3.6).toInt()
                .coerceIn(PASSAGE_CHAR_BUDGET_MIN, PASSAGE_CHAR_BUDGET_MAX)
        }
    }

    fun setDocument(d: Document) {
        doc = d
        index = Bm25(d.chunks)
    }

    fun retrieve(question: String, topK: Int = TOP_K): List<Scored> {
        val idx = index ?: return emptyList()
        val t0 = System.nanoTime()
        val hits = idx.search(question, topK)
        lastRetrievalMs = (System.nanoTime() - t0) / 1_000_000
        return hits
    }

    /**
     * Answer from the retrieved passages, streaming tokens as they arrive.
     *
     * The KV cache is cleared per question deliberately: each question gets a
     * different set of passages, so there is no shared prefix worth keeping and a
     * stale cache would silently poison the answer.
     */
    suspend fun answer(
        question: String,
        passages: List<Scored>,
        onToken: (String) -> Unit
    ): String = withContext(disp) {
        Native.llmResetKv(llm)
        val prompt = buildPrompt(question, passages)
        Native.llmPrefill(llm, prompt)
        val out = Native.llmGenerate(llm, maxAnswerTokens, null, object : Native.TokenCb {
            override fun onToken(piece: String) = onToken(piece)
        })
        lastTimings = Native.llmTimings(llm)
        out.trim()
    }

    /**
     * Passage budget is enforced in characters rather than tokens: exact counting
     * would need a tokenizer round-trip per candidate, and ~3.6 chars/token is
     * close enough for Llama on English prose. Overshooting n_ctx is the one
     * failure that produces no answer at all, so we leave real headroom.
     */
    private fun buildPrompt(question: String, passages: List<Scored>): String {
        val sb = StringBuilder()
        var used = 0
        val kept = ArrayList<Scored>()

        // GREEDY, NOT EVEN.
        //
        // Splitting the budget equally between two passages truncated the best
        // one: asked for the four Coffman conditions, the top slide was cut
        // after the third and the model invented "4. All of the above". The
        // best match has earned the space, so it gets as much as it can use and
        // the runner-up takes what is left — or nothing.
        val first = passages.first()
        val firstText = Trim.toQuery(first.chunk.text, question, minOf(charBudget, TOP_PASSAGE_MAX))
        kept.add(Scored(first.chunk.copy(text = firstText), first.score))
        used += firstText.length

        // A second passage only earns its prefill cost when it is genuinely
        // competitive and there is room left for something useful.
        val runnerUp = passages.getOrNull(1)
        val remaining = charBudget - used
        if (runnerUp != null && remaining >= MIN_USEFUL_PASSAGE &&
            first.score > 0 && runnerUp.score / first.score >= RUNNER_UP_RATIO
        ) {
            val text = Trim.toQuery(runnerUp.chunk.text, question, remaining)
            kept.add(Scored(runnerUp.chunk.copy(text = text), runnerUp.score))
            used += text.length
        }
        lastPromptChars = used

        // LLAMA 3.2 CHAT TEMPLATE, not a bare completion prompt.
        //
        // This is an instruction-tuned model. Given plain text ending in
        // "Answer:" it does not answer — it continues the pattern, inventing
        // further "[slide 9] ... Answer: 4" blocks that look like exam questions.
        // Wrapped in the header/eot markers it was actually trained on, it
        // answers and then stops cleanly at <|eot_id|>.
        //
        // Brevity is a latency feature, not a style preference: at ~9 tok/s a
        // 220-token answer takes 24 s, and the questions this is built for have
        // one-sentence answers.
        sb.append("<|begin_of_text|><|start_header_id|>system<|end_header_id|>\n\n")
        sb.append("You answer questions about a student's lecture slides. ")
        sb.append("Use ONLY the excerpts given to you. ")
        sb.append("Answer in one short sentence, with no preamble. ")
        sb.append("If the excerpts do not contain the answer, reply exactly: Not in these slides.")
        sb.append("<|eot_id|><|start_header_id|>user<|end_header_id|>\n\n")
        for (p in kept) {
            sb.append("[slide ").append(p.chunk.page).append("] ")
                .append(p.chunk.text.trim()).append("\n\n")
        }
        sb.append("Question: ").append(question.trim())
        sb.append("<|eot_id|><|start_header_id|>assistant<|end_header_id|>\n\n")
        return sb.toString()
    }

    /**
     * Character ranges in a passage that matched the query, so the UI can
     * highlight them the moment retrieval returns — roughly 5 ms after the user
     * hits send, and long before the model produces a token.
     */
    fun highlights(text: String, query: String): List<IntRange> {
        val terms = Chunk.tokenize(query).toSet()
        if (terms.isEmpty()) return emptyList()
        val out = ArrayList<IntRange>()
        Regex("\\p{L}[\\p{L}\\p{N}]*").findAll(text).forEach { m ->
            if (m.value.lowercase() in terms) out.add(m.range)
        }
        return out
    }

    fun close() {
        if (llm != 0L) runCatching { Native.llmFree(llm) }
        llm = 0
        exec.shutdown()
    }

    companion object {
        const val N_CTX = 2048              // NEVER default: Llama 3.2 trains at 131072
        const val DECODE_THREADS = 6        // measured optimum; 8 is 58% slower
        const val PREFILL_THREADS = 8       // prefill scales where decode does not
        // RETRIEVE MANY, SEND FEW.
        //
        // Prefill runs at only about twice decode speed on this class of CPU, so
        // context is expensive in a way it is not on a desktop: roughly 14 ms per
        // character sent. A 3200-char prompt measured **34.5 s to first word**.
        //
        // The UI shows the top 3 matches (retrieval is 1 ms and free); the model
        // receives one or two, trimmed to the sentences that matched. The user
        // sees everything; the model reads only what it needs.
        const val TOP_K = 3                        // shown to the user
        const val MAX_ANSWER_TOKENS = 80           // ~7 s at 9 tok/s
        // 8 s, not 5. Tuning purely for speed produced fast wrong answers: the
        // budget got tight enough to cut a numbered list in half, and a confident
        // wrong answer at 2 a.m. is worse than waiting three more seconds.
        const val TARGET_FIRST_WORD_MS = 8000.0
        const val PASSAGE_CHAR_BUDGET_DEFAULT = 900
        // 900, not 350. Calibrating purely on speed drove the budget down to
        // ~400 characters, which cannot hold a four-item list — so the model
        // answered fast and invented two of the four Coffman conditions. A
        // wrong answer is not a faster answer, it is a broken product.
        const val PASSAGE_CHAR_BUDGET_MIN = 900
        const val PASSAGE_CHAR_BUDGET_MAX = 2400   // fast phones may use more
        /** Second hit is "close" above this fraction of the top score. */
        const val RUNNER_UP_RATIO = 0.55
        /** The best passage may take this much before anything else is considered. */
        const val TOP_PASSAGE_MAX = 850
        /** Below this a second passage is fragments, not context. */
        const val MIN_USEFUL_PASSAGE = 220

        private val CALIBRATION_TEXT = buildString {
            append("Calibration passage. ")
            repeat(24) { append("The system measures prefill throughput on this device. ") }
        }
    }
}
