package dev.omnitalk

/**
 * JNI surface. See native/otjni.cpp.
 *
 * THREADING CONTRACT — every LLM call must be made from the same thread that
 * called [setAffinity] and then [llmLoad]; likewise for ASR. GGML worker threads
 * inherit the creating thread's affinity mask, and the pool is built at load
 * time, so pinning after the fact does nothing. Pipeline.kt enforces this with
 * one single-thread dispatcher per model. Do not call these from arbitrary
 * coroutines.
 */
object Native {
    init { System.loadLibrary("otjni") }

    /** Pin the CALLING thread. Pass a bitmask of CPU ids; <= 0 means no pinning. */
    external fun setAffinity(mask: Long): Int

    /** Topology + ISA features as JSON. Drives runtime adaptation on any Arm phone. */
    external fun cpuInfo(): String

    // ── LLM ──────────────────────────────────────────────────────────────────
    /** @param nCtx ALWAYS pass this. Defaulting uses Llama 3.2's 131072 ctx and OOMs the phone. */
    external fun llmLoad(path: String, nCtx: Int, nThreads: Int, nThreadsBatch: Int): Long

    /** Retune per pipeline phase without reloading (O3/O5). */
    external fun llmSetThreads(h: Long, nThreads: Int, nThreadsBatch: Int)

    /** Prefill ONLY new text since the last call — the KV cache is retained (O6). */
    external fun llmPrefill(h: Long, text: String): Int

    /** Streams pieces to [cb] so TTS can start at the first sentence (O4/O8). */
    external fun llmGenerate(h: Long, maxTokens: Int, grammar: String?, cb: TokenCb?): String

    external fun llmResetKv(h: Long)
    /** Truncate the KV cache to [keep] tokens; returns what was actually kept. */
    external fun llmRewindKv(h: Long, keep: Int): Int
    external fun llmTimings(h: Long): String
    external fun llmFree(h: Long)

    // ── ASR ──────────────────────────────────────────────────────────────────
    external fun asrLoad(path: String): Long

    /** @param pcm 16 kHz mono float32 in [-1,1]. Any other rate yields confident nonsense. */
    external fun asrTranscribe(h: Long, pcm: FloatArray, lang: String, nThreads: Int): String
    external fun asrFree(h: Long)

    interface TokenCb { fun onToken(piece: String) }
}

/** Parsed result of [Native.cpuInfo]. */
data class Topology(
    val cores: Int,
    val bigMask: Long,
    val littleMask: Long,
    val nBig: Int,
    val nLittle: Int,
    val maxKhz: Long,
    val dotprod: Boolean,
    val i8mm: Boolean,
    val sve: Boolean,
    val sme: Boolean
) {
    /** True when KleidiAI's int4/int8 microkernels can actually load. See docs/OPTIMIZATION.md O1. */
    val kleidiCapable: Boolean get() = i8mm || sme

    fun describe() = buildString {
        append("$nBig big + $nLittle LITTLE @ ${maxKhz / 1000} MHz")
        append(" · dotprod=${if (dotprod) "yes" else "no"}")
        append(" · i8mm=${if (i8mm) "yes" else "no"}")
        if (!kleidiCapable) append(" · KleidiAI inert")
    }

    companion object {
        fun read(): Topology {
            val j = Native.cpuInfo()
            fun num(k: String): Long =
                Regex("\"$k\":(-?\\d+)").find(j)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
            fun bool(k: String): Boolean =
                Regex("\"$k\":(true|false)").find(j)?.groupValues?.get(1) == "true"
            return Topology(
                cores = num("cores").toInt(),
                bigMask = num("big_mask"),
                littleMask = num("little_mask"),
                nBig = num("n_big").toInt(),
                nLittle = num("n_little").toInt(),
                maxKhz = num("max_khz"),
                dotprod = bool("dotprod"),
                i8mm = bool("i8mm"),
                sve = bool("sve"),
                sme = bool("sme")
            )
        }
    }
}
