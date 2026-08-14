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
        dropPrefix()
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
     * Only the varying half of the prompt is re-sent. The passages and question
     * differ every time, but the system instructions do not, so those stay in the
     * KV cache between questions — see the note in the body. Anything that uses
     * the context for another purpose must call [dropPrefix] first, or a rewind
     * would keep the wrong tokens and silently poison the answer.
     */
    suspend fun answer(
        question: String,
        passages: List<Scored>,
        onToken: (String) -> Unit
    ): String = withContext(disp) {
        // KEEP THE SYSTEM PROMPT IN THE CACHE.
        //
        // It is byte-identical on every question and about a hundred tokens
        // long — roughly a quarter of the prefill for a typical query. At ~14 ms
        // per character that was several seconds of re-reading the same
        // instructions before every answer. Prefill it once, then rewind the
        // cache to just that prefix and send only what changed.
        // -1, NOT 0, WHEN THERE IS NO PREFIX.
        //
        // dropPrefix() sets systemTokens = 0 after any study or topic pass, which
        // is how those tell us the cache now holds something else. With 0 as the
        // sentinel, cached == systemTokens == 0, the guard below did not fire, and
        // the question was prefilled straight on top of the flashcard prompt with
        // the system turn never re-sent — buildUserTurn strips it. Ask a question,
        // make cards, ask again, and the second answer was written in the context
        // of generating flashcards. A sentinel has to be a value the thing it
        // guards can never legitimately take.
        val cached = if (systemTokens > 0) Native.llmRewindKv(llm, systemTokens) else -1
        if (cached != systemTokens) {
            Native.llmResetKv(llm)
            systemTokens = Native.llmPrefill(llm, SYSTEM_PROMPT)
        }
        Native.llmPrefill(llm, buildUserTurn(question, passages))
        val out = Native.llmGenerate(llm, maxAnswerTokens, null, object : Native.TokenCb {
            override fun onToken(piece: String) = onToken(piece)
        })
        lastTimings = Native.llmTimings(llm)
        out.trim()
    }

    /** Tokens the cached system prefix occupies, or 0 if it is not resident. */
    private var systemTokens = 0

    /**
     * The half of the prompt that never changes, so it can stay in the KV cache.
     *
     * Ends mid-turn, at the user header: everything after this point varies per
     * question and is sent fresh. Editing this string invalidates nothing on
     * disk — the prefix is re-prefilled whenever the token count does not match.
     */
    private val SYSTEM_PROMPT = buildString {
        append("<|begin_of_text|><|start_header_id|>system<|end_header_id|>\n\n")
        // "One SHORT sentence" plus greedy decoding produced one-WORD answers:
        // "what algorithm avoids deadlock" was answered "Avoidance" from a slide
        // that names the Banker's algorithm in its first line. Technically a
        // fragment of the truth and useless to revise from. The instruction now
        // asks for a complete sentence that names the thing, which costs a few
        // tokens and makes the answer stand on its own away from the slide.
        append("You answer questions about a student's lecture slides. ")
        append("Use only the excerpts given to you. ")
        append("Answer in one complete sentence that names the specific thing asked about, ")
        append("so the sentence makes sense on its own. Start with the answer, not a preamble. ")
        // DO NOT add grounding instructions here. Asking the model in words to
        // stop inventing was tried against the "John Coffman, 1972" fabrication:
        // it did not remove it, and it changed an unrelated verified answer from
        // "The Banker's algorithm." into "This is the correct answer, as the
        // Banker's algorithm is indeed..." — the preamble this prompt already
        // forbids two lines up. Greedy decoding on a 1B model is steered by what
        // it is given, not by what it is told about what it is given. Ungrounded
        // tails are removed after generation, in AppState.dropUngroundedTail.
        append("If the excerpts do not contain the answer, reply exactly: Not in these slides.")
        append("<|eot_id|><|start_header_id|>user<|end_header_id|>\n\n")
    }

    /** The per-question half: retrieved passages, the question, the assistant header. */
    private fun buildUserTurn(question: String, passages: List<Scored>): String =
        buildPrompt(question, passages).removePrefix(SYSTEM_PROMPT)

    /** Study and topic passes share the context, so they invalidate the prefix. */
    private fun dropPrefix() { systemTokens = 0 }

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
        sb.append(SYSTEM_PROMPT)
        // The first passage is marked as the best match rather than left as one
        // of an unordered pair. Rank is information the retriever has and the
        // model otherwise never sees, and when a second passage is present it is
        // by definition nearly as topical — so without the label the model has no
        // reason to prefer the one that actually scored highest.
        for ((i, p) in kept.withIndex()) {
            sb.append(if (i == 0) "[best match — slide " else "[also possibly relevant — slide ")
                .append(p.chunk.page).append("]\n")
                .append(p.chunk.text.trim()).append("\n\n")
        }
        sb.append("Question: ").append(question.trim())
        if (kept.size > 1) {
            sb.append("\n(Answer from the best match unless it plainly does not contain the answer.)")
        }
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

    /**
     * Generate flashcards or quiz questions about a topic.
     *
     * Retrieval comes first, exactly as it does for a question: the topic is a
     * query, and only slides that actually match it are shown to the model.
     * More passages than a normal answer, because study material should span a
     * topic rather than answer one narrow point.
     */
    suspend fun study(
        scope: Scope,
        count: Int,
        onToken: (String) -> Unit
    ): Pair<String, List<Chunk>> = withContext(disp) {
        val all = doc?.chunks.orEmpty()
        if (all.isEmpty()) return@withContext "" to emptyList()

        // A page range is an exact instruction, so it selects directly. A topic
        // is a guess, so it goes through retrieval. Whole-deck spreads the
        // budget across the document rather than clustering at the start.
        val candidates: List<Chunk> = when (scope) {
            is Scope.Pages -> all.filter { it.page in scope.from..scope.to }
            // Topics come from the deck's own headings, so the slides are known
            // exactly. No retrieval, no near-miss, no drifting off topic.
            is Scope.Topics -> all.filter { it.id in scope.chunkIds }
            Scope.WholeDeck -> spread(all, STUDY_TOP_K)
        }
        if (candidates.isEmpty()) return@withContext "" to emptyList()

        var used = 0
        val kept = ArrayList<Chunk>()
        for (c in candidates) {
            val room = STUDY_CHAR_BUDGET - used
            if (room < MIN_USEFUL_PASSAGE) break
            val text = c.text.take(minOf(room, TOP_PASSAGE_MAX))
            kept.add(c.copy(text = text))
            used += text.length
        }
        lastPromptChars = used

        dropPrefix()
        Native.llmResetKv(llm)
        Native.llmPrefill(llm, StudyPrompt.build(scope, kept, count))
        val out = Native.llmGenerate(llm, count * TOKENS_PER_ITEM, null, object : Native.TokenCb {
            override fun onToken(piece: String) = onToken(piece)
        })
        lastTimings = Native.llmTimings(llm)
        out to kept
    }

    /** Evenly spaced passages, so "whole deck" is not just the first few slides. */
    private fun spread(all: List<Chunk>, n: Int): List<Chunk> {
        if (all.size <= n) return all
        val step = all.size.toDouble() / n
        return (0 until n).map { all[(it * step).toInt().coerceIn(all.indices)] }
    }

    /** Slides the reader is most likely to want to ask about, for suggested questions. */
    fun keyChunks(n: Int): List<Chunk> = spread(doc?.chunks.orEmpty(), n)

    /**
     * Read the deck once and name its topics.
     *
     * Headings are free and instant, but only work when the deck actually has
     * titles. Continuous prose, scanned notes and papers give none, and the
     * topic list degenerates to "Slide 1, Slide 2, ...", which is no help in
     * deciding what to revise.
     *
     * So this is offered as a deliberate one-time pass: the deck is read in
     * small batches, the model names what each batch is about, and the result is
     * saved with the deck and never recomputed. It costs about a minute once,
     * and every flashcard afterwards is built from a topic the model itself
     * recognised, together with the exact slides it came from.
     */
    /**
     * The model announcing itself rather than naming a topic.
     *
     * "Just the lines, please" is a request, not a guarantee, and a 1B model
     * often opens with "Here are the main topics:". That line is the right
     * length and word count to pass every other filter, so it became a topic in
     * the picker — one that maps to real slides and produces cards about nothing
     * in particular.
     */
    private fun isPreamble(s: String): Boolean {
        val t = s.lowercase().trimEnd(':')
        return t.endsWith("topics") || t.endsWith("notes") ||
            t.startsWith("here are") || t.startsWith("here is") ||
            t.startsWith("the main") || t.startsWith("sure") || t.startsWith("of course")
    }

    suspend fun generateTopics(onProgress: (Int, Int) -> Unit): List<Topic> = withContext(disp) {
        val all = doc?.chunks.orEmpty()
        if (all.isEmpty()) return@withContext emptyList()

        val batches = all.chunked(TOPIC_BATCH)
        val out = ArrayList<Topic>()
        for ((i, batch) in batches.withIndex()) {
            onProgress(i + 1, batches.size)
            val sb = StringBuilder()
            sb.append("<|begin_of_text|><|start_header_id|>system<|end_header_id|>\n\n")
            sb.append("You are a friendly study assistant who helps students organise their notes.")
            sb.append("<|eot_id|><|start_header_id|>user<|end_header_id|>\n\n")
            for (c in batch) {
                sb.append(c.text.take(TOPIC_CHARS_PER_CHUNK).trim()).append("\n\n")
            }
            sb.append(
                "What are the main topics in these notes? Give me at most 2, " +
                "each a short phrase of two to five words, one per line. Just the lines, please."
            )
            sb.append("<|eot_id|><|start_header_id|>assistant<|end_header_id|>\n\n")

            dropPrefix()
            Native.llmResetKv(llm)
            Native.llmPrefill(llm, sb.toString())
            val raw = Native.llmGenerate(llm, TOPIC_TOKENS, null, null)

            val names = raw.lineSequence()
                .map { it.trim().trim('-', '*', '.', ' ').replace(Regex("^\\d+[.)]\\s*"), "") }
                .filter { it.length in 3..60 && it.count { c -> c == ' ' } <= 6 }
                .filterNot { isPreamble(it) }
                .take(2).toList()

            for (n in names) {
                out.add(Topic(n, batch.minOf { it.page }, batch.maxOf { it.page }, batch.map { it.id }))
            }
            if (names.isEmpty()) {
                out.add(Topic("Slides ${batch.minOf { it.page }}-${batch.maxOf { it.page }}",
                    batch.minOf { it.page }, batch.maxOf { it.page }, batch.map { it.id }))
            }
        }
        // Same title from two batches means one topic spanning both.
        out.groupBy { it.title.lowercase() }.map { (_, g) ->
            Topic(g.first().title, g.minOf { it.fromPage }, g.maxOf { it.toPage },
                g.flatMap { it.chunkIds }.distinct())
        }
    }

    /** Which slide a generated card most likely came from, for citation. */
    fun pageFor(text: String): Int {
        val hits = index?.search(text, 1).orEmpty()
        return hits.firstOrNull()?.chunk?.page ?: 0
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
        // A ceiling, not a target: generation stops at EOS, so a short answer
        // still costs a short answer. 80 only ever bound the answers that needed
        // to be long, and it cut them mid-word — "What are the four Coffman
        // conditions?" ended at "4. **Circular Wait**: there exists a set of".
        // That is the same failure as the 400-char passage budget, at the other
        // end of the pipeline: tuned for latency, paid for in correctness. Time
        // to first word, which is what we actually measure, does not move.
        // 160 fits the longest real answer in the deck (the four conditions run
        // ~130 tokens) without leaving a whole spare sentence for the model to
        // fill with invention. Whatever fragment remains at the cap is cut back
        // to the last completed sentence, so the padding never reaches the user.
        const val MAX_ANSWER_TOKENS = 160
        /** No saved value may cut a list-shaped answer in half again. */
        const val MAX_ANSWER_TOKENS_MIN = 160
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
        // 0.80, not 0.55. A second passage is only worth sending when it is
        // nearly tied with the winner; below that it is a distractor, and a 1B
        // model cannot be relied on to ignore a distractor that looks topical.
        //
        // Measured failure: "what algorithm avoids deadlock" retrieved the
        // Banker's algorithm slide first and the "Deadlock handling strategies"
        // slide second at ~60% of the score. The strategies slide lists
        // prevention, avoidance, detection and recovery, and the ostrich
        // algorithm one after another, and the model answered "Detection and
        // recovery" — a phrase from the distractor, while the correct answer sat
        // in the first line of the top passage. Retrieval was never wrong here;
        // it was out-voted.
        const val RUNNER_UP_RATIO = 0.80
        /** The best passage may take this much before anything else is considered. */
        const val TOP_PASSAGE_MAX = 850
        /** Below this a second passage is fragments, not context. */
        const val MIN_USEFUL_PASSAGE = 220
        // Study material spans a topic rather than answering one point, so it
        // reads more slides and accepts a longer wait — it is a deliberate
        // "make me something" action with a progress indicator, not a question
        // the reader is waiting on.
        const val STUDY_TOP_K = 4
        const val STUDY_CHAR_BUDGET = 1800
        // 70, not 46. The model narrates around each card even when asked not
        // to, and a budget that only just fits perfect output produces one card
        // and a truncated second. Overshooting costs nothing when it stops early.
        const val TOKENS_PER_ITEM = 70
        // Small batches: the model names a handful of slides accurately, but
        // asked to summarise the whole deck at once it produces vague headings
        // and blows the context.
        const val TOPIC_BATCH = 4
        const val TOPIC_CHARS_PER_CHUNK = 420
        const val TOPIC_TOKENS = 40

        private val CALIBRATION_TEXT = buildString {
            append("Calibration passage. ")
            repeat(24) { append("The system measures prefill throughput on this device. ") }
        }
    }
}
