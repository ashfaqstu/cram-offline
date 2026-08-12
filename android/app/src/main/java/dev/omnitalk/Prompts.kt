package dev.omnitalk

/**
 * Prompt assets.
 *
 * BUDGET: every prompt token costs ~50 ms of prefill on this device (measured
 * 18.7 tok/s at 6 threads). A 300-token system prompt is 16 seconds. So the
 * static prefix is kept under ~90 tokens and pre-warmed during objective
 * selection (O9); only the short per-turn suffix is prefilled each turn (O6).
 *
 * Prose in a prompt is a latency bug here. Resist adding explanation — measure
 * instead.
 */
object Prompts {

    /** Prefilled ONCE per objective, during dead time. Never re-prefilled. */
    fun staticPrefix(lang: Lang, objective: Objective): String = """
You speak ${lang.promptName} to a local person for an English-speaking traveller.
Goal: ${objective.prompt}.
Reply with one JSON object: q = your next short question in ${lang.promptName};
g = its English translation; s = facts you just learned, values short and in
English, copied never invented; d = true only when nothing is missing.
""".trim()

    /** The ONLY thing prefilled per turn — typically 30-60 tokens. */
    fun turnSuffix(fsm: AgentFsm, heard: String): String {
        val known = fsm.objective.knownPairs().ifEmpty { "none" }
        val missing = fsm.objective.missingLabels().ifEmpty { "nothing" }
        return "\nStill needed: $missing\nKnown: $known\nThey said: \"$heard\"\n"
    }

    /** Opening turn — nothing heard yet. */
    fun openingSuffix(fsm: AgentFsm): String =
        "\nStill needed: ${fsm.objective.missingLabels()}\nKnown: none\nThey said: \"\"\n"

    /** Live Intercom: plain translation, no grammar. */
    fun translate(target: String, text: String): String =
        "Translate to $target. Reply with the translation only.\n\"$text\"\n"
}
