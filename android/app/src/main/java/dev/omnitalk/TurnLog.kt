package dev.omnitalk

import android.util.Log
import org.json.JSONObject
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream

/**
 * Per-turn telemetry, written to the app's external files dir so `adb pull` can
 * fetch it without root or any permission.
 *
 * Each turn produces two files:
 *   turn_NNN.wav   the exact 16 kHz mono audio that was fed to Whisper
 *   turn_NNN.json  trace marks, transcript, raw model output, timings, slots
 *
 * Why the WAV matters: when a turn goes wrong, "the mic is silent", "Whisper
 * mis-heard it" and "the model ignored a correct transcript" look identical from
 * the outside. With the audio captured we can replay the same bytes through a
 * different model or thread count and find out which one it actually was —
 * without asking the user to reproduce anything.
 */
object TurnLog {
    private var dir: File? = null
    private var seq = 0
    private val pcm = ArrayList<FloatArray>()
    @Volatile var enabled = true

    fun init(d: File) {
        dir = d
        // continue numbering across restarts so nothing is silently overwritten
        seq = (d.listFiles { f -> f.name.startsWith("turn_") && f.name.endsWith(".json") }
            ?.mapNotNull { it.name.removePrefix("turn_").removeSuffix(".json").toIntOrNull() }
            ?.maxOrNull() ?: 0)
    }

    fun begin() {
        if (!enabled) return
        synchronized(pcm) { pcm.clear() }
        seq++
    }

    fun addAudio(chunk: FloatArray) {
        if (!enabled) return
        synchronized(pcm) { pcm.add(chunk) }
    }

    fun finish(
        trace: Trace,
        transcript: String,
        raw: String,
        timings: String,
        lang: String,
        turbo: Boolean,
        fsm: AgentFsm?
    ) {
        if (!enabled) return
        val d = dir ?: return
        val n = "%03d".format(seq)
        try {
            val flat = synchronized(pcm) {
                val total = pcm.sumOf { it.size }
                FloatArray(total).also { out ->
                    var i = 0
                    for (c in pcm) { c.copyInto(out, i); i += c.size }
                }
            }
            if (flat.isNotEmpty()) writeWav(File(d, "turn_$n.wav"), flat)

            var peak = 0f
            for (s in flat) { val a = kotlin.math.abs(s); if (a > peak) peak = a }

            val o = JSONObject()
                .put("seq", seq)
                .put("lang", lang)
                .put("mode", if (turbo) "turbo" else "naive")
                .put("audio_samples", flat.size)
                .put("audio_seconds", flat.size / 16000.0)
                .put("audio_peak", peak)
                .put("transcript", transcript)
                .put("raw_output", raw)
                .put("timings", runCatching { JSONObject(timings) }.getOrDefault(JSONObject()))
                .put("trace", trace.toJson())
            if (fsm != null) {
                o.put("question", fsm.lastQuestion)
                o.put("gloss", fsm.lastGloss)
                o.put("turn", fsm.turn)
                val slots = JSONObject()
                for (s in fsm.objective.slots) slots.put(s.key, s.value ?: JSONObject.NULL)
                o.put("slots", slots)
                o.put("objective", fsm.objective.title)
            }
            File(d, "turn_$n.json").writeText(o.toString(2))
            Log.i("otturn", "wrote turn_$n (${"%.1f".format(flat.size / 16000.0)}s, peak=${"%.3f".format(peak)})")
        } catch (e: Throwable) {
            Log.e("otturn", "failed to write turn log", e)
        }
    }

    /** 16-bit PCM mono @16 kHz — the format whisper.cpp expects, so replay is trivial. */
    private fun writeWav(f: File, samples: FloatArray) {
        val sr = 16000
        val dataBytes = samples.size * 2
        DataOutputStream(FileOutputStream(f)).use { o ->
            fun le32(v: Int) { o.write(v and 0xff); o.write(v ushr 8 and 0xff); o.write(v ushr 16 and 0xff); o.write(v ushr 24 and 0xff) }
            fun le16(v: Int) { o.write(v and 0xff); o.write(v ushr 8 and 0xff) }
            o.writeBytes("RIFF"); le32(36 + dataBytes); o.writeBytes("WAVE")
            o.writeBytes("fmt "); le32(16); le16(1); le16(1)
            le32(sr); le32(sr * 2); le16(2); le16(16)
            o.writeBytes("data"); le32(dataBytes)
            for (s in samples) {
                val v = (s.coerceIn(-1f, 1f) * 32767f).toInt()
                le16(v)
            }
        }
    }
}
