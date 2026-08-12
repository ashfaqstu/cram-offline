package dev.omnitalk

import org.json.JSONObject

/**
 * The agent is a slot-filling state machine. The model's only job each turn is
 * (a) fill slots from what it just heard and (b) ask for the most important
 * thing still missing. Which slot is missing is computed HERE, in Kotlin, not by
 * the model — a 1B model is reliable at "answer this narrow question", not at
 * "reason about your own state".
 *
 * That is also why the grammar can emit the question BEFORE the slots (O8): the
 * model never needed the extraction step to know what to ask.
 */
data class Slot(val key: String, val label: String, var value: String? = null)

class Objective(
    val title: String,
    val prompt: String,
    val slots: List<Slot>
) {
    fun missing() = slots.filter { it.value == null }
    fun known() = slots.filter { it.value != null }
    val complete: Boolean get() = slots.all { it.value != null }

    fun missingLabels() = missing().joinToString(", ") { it.label }
    fun knownPairs() = known().joinToString(", ") { "${it.label}=${it.value}" }

    fun reset() { slots.forEach { it.value = null } }

    companion object {
        fun busTicket() = Objective(
            "Bus ticket",
            "Find out the departure times of the bus, whether it has air conditioning, and the ticket price",
            listOf(
                Slot("departure", "departure times"),
                Slot("ac", "air conditioning"),
                Slot("price", "ticket price")
            )
        )
        fun pharmacy() = Objective(
            "Pharmacy",
            "Find out if they have the medicine, how much it costs, and how to take it",
            listOf(
                Slot("available", "in stock"),
                Slot("price", "price"),
                Slot("dosage", "dosage")
            )
        )
        fun market() = Objective(
            "Market haggle",
            "Find out the asking price and the best price they will accept",
            listOf(
                Slot("asking", "asking price"),
                Slot("best", "best price")
            )
        )
        fun all() = listOf(busTicket(), pharmacy(), market())
    }
}

class AgentFsm(val objective: Objective, private val maxTurns: Int = 6) {
    var turn = 0; private set
    var lastQuestion = ""; private set
    var lastGloss = ""; private set
    var modelSaysDone = false; private set
    /** Slot values rejected as unsupported by the transcript — surfaced for the write-up. */
    val rejected = mutableListOf<String>()

    /** Parse one grammar-constrained reply. Returns false if it was unusable. */
    fun ingest(json: String, heard: String = ""): Boolean {
        val obj = runCatching { JSONObject(extractObject(json)) }.getOrNull() ?: return false

        lastQuestion = obj.optString("q", lastQuestion)
        lastGloss = obj.optString("g", "")
        // Do NOT trust the model's own "done". It set d=true on turn 1 after
        // inventing every slot. Completion is decided by the slots we accepted.
        modelSaysDone = false

        obj.optJSONObject("s")?.let { s -> mergeSlots(s, heard) }
        turn++
        return true
    }

    private fun mergeSlots(s: org.json.JSONObject, heard: String) {
        // The model answers with whatever key it likes — usually the human LABEL
        // we put in the prompt ("departure times"), not our internal key
        // ("departure"). Match on either, normalised.
        val byName = HashMap<String, Slot>()
        for (slot in objective.slots) {
            byName[norm(slot.key)] = slot
            byName[norm(slot.label)] = slot
        }

        for (k in s.keys()) {
            val slot = byName[norm(k)] ?: continue
            if (slot.value != null) continue              // never overwrite a known fact
            if (s.isNull(k)) continue
            val v = s.optString(k).trim()
            if (v.isEmpty() || v.equals("null", true)) continue

            // ANTI-HALLUCINATION. A 1B model will not honour "copied never
            // invented" on instruction alone: asked only "Hello, welcome to the
            // ticket counter", it confidently returned departure="6:00 AM",
            // ac="Yes", price="50" and declared itself done. Require the value to
            // be supported by something actually said.
            if (!supportedBy(v, heard, slot)) {
                rejected += "${slot.key}='$v'"
                continue
            }
            slot.value = v
        }
    }

    /**
     * Is this value actually supported by what the person just said?
     *
     * The model reliably copies values out of the prompt's worked example — it
     * returned departure="7 am" on a turn where the speaker only discussed air
     * conditioning and price. So evidence is required, in this order:
     *
     *  1. Any digits in the value MUST appear in the transcript. This is the rule
     *     that catches echoed examples, because invented facts are nearly always
     *     numbers (times, prices) that were never spoken.
     *  2. Otherwise a content word must be shared.
     *  3. A bare "yes"/"no" is accepted only if the transcript actually discussed
     *     that slot's topic — "Only the 10 o'clock bus has air conditioning"
     *     legitimately supports ac="Yes" even though the word "yes" never occurs.
     */
    private fun supportedBy(value: String, heard: String, slot: Slot): Boolean {
        if (heard.isBlank()) return false
        val h = heard.lowercase()
        val hWords = h.split(NONWORD).filter { it.length >= 3 }.toSet()

        val vDigits = DIGITS.findAll(value).map { it.value }.toList()
        if (vDigits.isNotEmpty()) {
            val hDigits = DIGITS.findAll(h).map { it.value }.toSet()
            return vDigits.any { it in hDigits }      // unsupported number -> reject
        }

        val vWords = value.lowercase().split(NONWORD).filter { it.length >= 3 }
        if (vWords.any { it in hWords }) return true

        val short = value.lowercase().trim().trim('.', '!')
        if (short in YES_NO) {
            // topic must have been raised, e.g. slot "air conditioning" vs the transcript
            return slot.label.lowercase().split(NONWORD)
                .filter { it.length >= 3 }
                .any { it in hWords }
        }
        return false
    }

    companion object {
        private val DIGITS = Regex("\\d+")
        private val NONWORD = Regex("[^\\p{L}\\p{N}]+")
        private val YES_NO = setOf("yes", "no", "yeah", "yep", "nope", "true", "false", "available", "unavailable")

        /** Grammar guarantees valid JSON, but be defensive for the ungrammared A/B arm. */
        fun extractObject(raw: String): String {
            val i = raw.indexOf('{')
            val j = raw.lastIndexOf('}')
            return if (i >= 0 && j > i) raw.substring(i, j + 1) else raw
        }

        private fun norm(s: String) = s.lowercase().replace(Regex("[^a-z0-9]"), "")
    }

    fun done() = objective.complete || modelSaysDone || turn >= maxTurns

    fun englishSummary(): String {
        if (objective.known().isEmpty()) return "No information was gathered."
        return buildString {
            append("Summary — ${objective.title}.\n")
            for (s in objective.slots) {
                append("• ${s.label.replaceFirstChar { it.uppercase() }}: ")
                append(s.value ?: "not answered")
                append('\n')
            }
        }.trim()
    }

}
