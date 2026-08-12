package dev.omnitalk

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import java.util.concurrent.Executors

/**
 * HetPipe — the scheduler this project exists to demonstrate.
 *
 * The Snapdragon 720G / Dimensity 920 class of SoC is 2 big + 6 LITTLE. Naive
 * inference hands every thread to one model and wastes most of the chip. HetPipe
 * treats the clusters as two pools: ASR transcribes 2-second chunks on the LITTLE
 * cores WHILE the user is still speaking, the LLM prefills on the big cores
 * BEFORE they finish, and each finished sentence is spoken WHILE the model is
 * still decoding the next one.
 *
 * MEASURED on a Poco M2 Pro (2026-08-13):
 *   LLM alone on big cores        12.89 pp / 8.74 tg
 *   LLM on big + ASR on LITTLE    12.91 pp / 8.72 tg   -> 99.8%, overlap is free
 *
 *   threads (unpinned)   prefill   decode
 *     2                   12.75      9.09
 *     6                   18.74     10.95   <- decode optimum
 *     8                   20.48      4.57   <- 58% collapse, layer-barrier stall
 *
 * Hence: prefill wants 8 threads, decode wants 6, and 8 threads for decode is a
 * disaster. llama.cpp exposes both via llama_set_n_threads(t, tb).
 *
 * THREADING CONTRACT: one single-thread dispatcher per model, affinity set on
 * that thread BEFORE the model loads, and every later call for that model routed
 * through the same dispatcher. GGML workers inherit the creating thread's mask.
 */
class Pipeline(private val topo: Topology, var cfg: Config) {

    data class Config(
        /** false = NAIVE baseline: all-core, no overlap, KV reset each turn. */
        val turbo: Boolean = true,
        val nCtx: Int = 2048,           // NEVER default this: 131072 ctx OOMs the phone
        val asrChunkSec: Double = 2.0,
        val maxTokens: Int = 160
    )

    // Decode optimum measured at 6; prefill at 8. Clamp to what the device has.
    private val decodeThreads = if (cfg.turbo) minOf(6, topo.cores) else topo.cores
    private val prefillThreads = minOf(8, topo.cores)
    private val asrThreads = if (cfg.turbo) maxOf(2, topo.nLittle) else topo.cores

    private val llmExec = Executors.newSingleThreadExecutor { Thread(it, "ot-llm") }
    private val asrExec = Executors.newSingleThreadExecutor { Thread(it, "ot-asr") }
    private val llmDisp = llmExec.asCoroutineDispatcher()
    private val asrDisp = asrExec.asCoroutineDispatcher()

    private var llm = 0L
    private var asr = 0L
    var lastTimings: String = "{}"; private set

    val ready: Boolean get() = llm != 0L && asr != 0L

    suspend fun load(llmPath: String, asrPath: String): Boolean = coroutineScope {
        val a = async(llmDisp) {
            // The LLM is deliberately NOT pinned.
            //
            // The plan assumed decode belonged on the big cluster. Measurement said
            // otherwise: 2 threads pinned to the A76 pair gives 8.64 tok/s, while 6
            // unpinned threads give 10.95 — the LITTLE cores contribute real work at
            // this model size. Pinning also cannot be undone later: GGML fixes the
            // pool's affinity when the pool is created, so there is no way to "give
            // decode the whole chip" mid-turn. So we take the faster configuration
            // and keep only ASR pinned, which is what stops the two from fighting.
            llm = Native.llmLoad(llmPath, cfg.nCtx, decodeThreads, prefillThreads)
            llm != 0L
        }
        val b = async(asrDisp) {
            if (cfg.turbo) Native.setAffinity(topo.littleMask)
            asr = Native.asrLoad(asrPath)
            asr != 0L
        }
        a.await() && b.await()
    }

    /** O9 — prefill the static prefix during dead time (while the user picks an objective). */
    suspend fun prewarm(staticPrefix: String) = withContext(llmDisp) {
        Native.llmResetKv(llm)
        Native.llmPrefill(llm, staticPrefix)
    }

    suspend fun resetKv() = withContext(llmDisp) { Native.llmResetKv(llm) }

    /** Transcribe one buffer. Used by the naive path and by chunked capture. */
    suspend fun transcribe(pcm: FloatArray, lang: String): String = withContext(asrDisp) {
        Native.asrTranscribe(asr, pcm, lang, asrThreads)
    }

    /**
     * One conversational turn.
     *
     * @param audio      chunks of 16 kHz mono float32, emitted while the user holds the button
     * @param onPartial  live transcript for the UI
     * @param onSentence fires the moment a sentence is complete -> hand straight to TTS
     * @return the raw model output (JSON when [grammar] is supplied)
     */
    suspend fun runTurn(
        audio: Flow<FloatArray>,
        promptSuffix: (transcript: String) -> String,
        grammar: String?,
        lang: String,
        trace: Trace,
        onPartial: (String) -> Unit = {},
        onSentence: (String) -> Unit = {}
    ): String = coroutineScope {

        val transcript = StringBuilder()
        var speculated = 0
        var chunkCount = 0
        // Speculative prefills are fire-and-forget, but they MUST complete before
        // the final prefill or n_past and the KV cache disagree.
        val specJobs = mutableListOf<Job>()

        // ── OVERLAP 1: chunked ASR on the LITTLE cluster during capture ───────
        trace.mark("capture_start")
        val asrJob = launch(asrDisp) {
            audio.collect { chunk ->
                chunkCount++
                val piece = Native.asrTranscribe(asr, chunk, lang, asrThreads)
                android.util.Log.i("otpipe", "chunk $chunkCount (${chunk.size} samples) -> '${piece.take(60)}'")
                if (piece.isNotBlank()) {
                    transcript.append(piece)
                    onPartial(transcript.toString())

                    // ── OVERLAP 2: speculative prefill on the big cluster ─────
                    if (cfg.turbo) {
                        val text = transcript.toString()
                        if (text.length - speculated > 40) {
                            val delta = text.substring(speculated)
                            speculated = text.length
                            specJobs += launch(llmDisp) { Native.llmPrefill(llm, delta) }
                        }
                    }
                }
            }
        }
        asrJob.join()
        specJobs.joinAll()          // never let a speculative prefill outrun the final one
        trace.mark("end_of_speech")
        trace.mark("asr_done")

        val heard = transcript.toString().trim()
        android.util.Log.i("otpipe", "turn: chunks=$chunkCount transcript='${heard.take(120)}'")

        // If ASR heard nothing, DO NOT generate. Otherwise the model answers from
        // the pre-warmed prefix alone and emits the same opening line every time,
        // which looks like the app ignoring you.
        if (heard.isBlank()) {
            trace.mark("empty_input")
            return@coroutineScope ""
        }

        // Prefill only what speculation did not already cover (O6).
        val tail = if (cfg.turbo && speculated in 1..heard.length) heard.substring(speculated) else heard
        withContext(llmDisp) { Native.llmPrefill(llm, promptSuffix(tail)) }
        trace.mark("prefill_done")

        // ── OVERLAP 3: speak the question the instant it is complete (O4/O8) ──
        //
        // Only ever speak the VALUE of "q". Earlier this fed TTS whatever had
        // accumulated the moment any sentence-ending character appeared, which
        // meant the engine read the JSON aloud — quotes, braces, `q`, `g` and all.
        // Wait for the closing quote AND the comma: that proves the field is whole.
        val full = StringBuilder()
        var spoke = false
        val result = withContext(llmDisp) {
            Native.llmGenerate(llm, cfg.maxTokens, grammar, object : Native.TokenCb {
                override fun onToken(piece: String) {
                    full.append(piece)
                    if (!cfg.turbo || spoke) return
                    if (grammar != null) {
                        completedQ(full.toString())?.let { q ->
                            spoke = true
                            onSentence(q)          // <- first audio fires here
                        }
                    } else if (piece.any { it in SENTENCE_END }) {
                        // Live Intercom: no grammar, so plain sentence chunking.
                        val s = full.toString().trim()
                        if (s.isNotBlank()) { spoke = true; onSentence(s) }
                    }
                }
            })
        }
        trace.mark("decode_done")
        lastTimings = Native.llmTimings(llm)

        // NAIVE path speaks only once everything is decoded — the O4 baseline.
        // Still speak only the question, never the raw JSON.
        if (!cfg.turbo) {
            val s = if (grammar != null) completedQ(result) ?: "" else result
            if (s.isNotBlank()) onSentence(s)
        }

        result
    }

    fun close() {
        runCatching { if (llm != 0L) Native.llmFree(llm) }
        runCatching { if (asr != 0L) Native.asrFree(asr) }
        llmExec.shutdown(); asrExec.shutdown()
    }

    companion object {
        private val SENTENCE_END = charArrayOf('.', '?', '!', '।', '\n')

        // Requires the closing quote AND the following comma, so a partially
        // decoded value can never be spoken. `।` (danda) and Latin punctuation
        // inside the value are fine — they are part of the sentence, not delimiters.
        private val Q_COMPLETE = Regex("\"q\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"\\s*,")

        /** The finished value of "q", or null if it is still being decoded. */
        fun completedQ(raw: String): String? {
            val m = Q_COMPLETE.find(raw) ?: return null
            val s = m.groupValues[1]
                .replace("\\\"", "\"")
                .replace("\\n", " ")
                .replace("\\\\", "\\")
                .trim()
            return s.ifBlank { null }
        }
    }
}
