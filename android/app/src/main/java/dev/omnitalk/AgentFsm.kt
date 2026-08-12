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

    /** Parse one grammar-constrained reply. Returns false if it was unusable. */
    fun ingest(json: String): Boolean {
        val obj = runCatching { JSONObject(extractObject(json)) }.getOrNull() ?: return false

        lastQuestion = obj.optString("q", lastQuestion)
        lastGloss = obj.optString("g", "")
        modelSaysDone = obj.optBoolean("d", false)

        obj.optJSONObject("s")?.let { s ->
            for (slot in objective.slots) {
                if (slot.value != null) continue          // never overwrite a known fact
                if (!s.has(slot.key)) continue
                if (s.isNull(slot.key)) continue
                val v = s.optString(slot.key).trim()
                if (v.isNotEmpty() && !v.equals("null", true)) slot.value = v
            }
        }
        turn++
        return true
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

    companion object {
        /** Grammar guarantees valid JSON, but be defensive for the ungrammared A/B arm. */
        fun extractObject(raw: String): String {
            val i = raw.indexOf('{')
            val j = raw.lastIndexOf('}')
            return if (i >= 0 && j > i) raw.substring(i, j + 1) else raw
        }
    }
}
