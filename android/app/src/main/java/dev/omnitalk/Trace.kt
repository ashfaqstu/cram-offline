package dev.omnitalk

import org.json.JSONArray
import org.json.JSONObject

/**
 * Stage timestamps for one turn. Every optimization number in the submission
 * comes from here, so capture a NAIVE trace before optimizing — you cannot
 * reconstruct a baseline afterwards.
 *
 * The headline metric is [firstAudioLatency]: end of speech to the first audio
 * sample actually played. Not total turn time — this is what a human waiting in
 * a market actually experiences.
 */
class Trace(val label: String = "") {
    private val t0 = System.nanoTime()
    private val marks = mutableListOf<Pair<String, Double>>()

    fun mark(name: String) = synchronized(marks) {
        marks += name to (System.nanoTime() - t0) / 1_000_000.0
    }

    fun at(name: String): Double? = synchronized(marks) {
        marks.lastOrNull { it.first == name }?.second
    }

    fun first(name: String): Double? = synchronized(marks) {
        marks.firstOrNull { it.first == name }?.second
    }

    /** THE headline number, in milliseconds. */
    fun firstAudioLatency(): Double? {
        val end = at("end_of_speech") ?: return null
        val audio = first("first_audio") ?: return null
        return audio - end
    }

    fun toJson(extra: Map<String, Any?> = emptyMap()): JSONObject {
        val o = JSONObject()
        o.put("label", label)
        o.put("first_audio_ms", firstAudioLatency() ?: JSONObject.NULL)
        val arr = JSONArray()
        synchronized(marks) {
            for ((n, ms) in marks) arr.put(JSONObject().put("mark", n).put("ms", ms))
        }
        o.put("marks", arr)
        for ((k, v) in extra) o.put(k, v ?: JSONObject.NULL)
        return o
    }

    fun summary(): String {
        val fa = firstAudioLatency()
        return buildString {
            append(if (fa != null) "first audio %.2f s".format(fa / 1000.0) else "first audio —")
            at("asr_done")?.let { a -> at("end_of_speech")?.let { e -> append(" · asr tail %.0f ms".format(a - e)) } }
            at("prefill_done")?.let { p -> at("asr_done")?.let { a -> append(" · prefill %.0f ms".format(p - a)) } }
            at("decode_done")?.let { d -> at("prefill_done")?.let { p -> append(" · decode %.0f ms".format(d - p)) } }
        }
    }
}
