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

    /**
     * Prefilled ONCE per objective, during dead time (O9). Never re-prefilled.
     *
     * The worked example is the expensive part — and it is free, because it lives
     * in the pre-warmed prefix rather than the per-turn suffix. Without it a 1B
     * model echoed the schema back as slot keys and repeated the user's sentence
     * as its own question. Abstract instructions do not survive at this size;
     * one concrete demonstration does.
     */
    fun staticPrefix(lang: Lang, objective: Objective): String {
        val keys = objective.slots.joinToString(", ") { it.key }
        val first = objective.slots.first().key
        return """
You are asking a local person questions in ${lang.promptName}, on behalf of an English-speaking traveller.
Your goal: ${objective.prompt}.

Reply with ONE JSON object and nothing else:
  q = the next question YOU ask, written in ${lang.promptName}
  g = the English translation of q
  s = facts the person just told you. Allowed keys: $keys. Leave s empty if they told you nothing.
  d = false

Never put a fact in s unless the person just said it. Never ask about something already known.

Example
They said: "The bus leaves at seven in the morning."
{"q":"Does it have air conditioning?","g":"Does it have air conditioning?","s":{"$first":"7 am"},"d":false}
""".trim()
    }

    /**
     * The ONLY thing prefilled per turn — typically 30-60 tokens.
     *
     * Slots are named by their exact keys, because the model otherwise answers
     * with the human label ("departure times") and nothing matches. The FSM now
     * accepts either, but naming the keys here makes the common case cheap.
     */
    fun turnSuffix(fsm: AgentFsm, heard: String): String {
        val known = fsm.objective.knownPairs().ifEmpty { "none" }
        val missingSlots = fsm.objective.missing()
        val missing = missingSlots.joinToString(", ") { it.key }.ifEmpty { "nothing" }
        val target = missingSlots.firstOrNull()?.label
        // The instruction goes AFTER the quote. With "They said" last, the model
        // simply continued it and echoed the speaker's sentence back as its own
        // question. Ending on the instruction is what makes it ask instead.
        return "\nThey said: \"$heard\"" +
                "\nAlready known: $known" +
                "\nStill missing: $missing" +
                (if (target != null) "\nAsk them about: $target" else "") +
                "\nq must be a NEW question you ask them. Never repeat their words. " +
                "Put a key in s only if they just said it.\n"
    }

    /** Opening turn — nothing heard yet. */
    fun openingSuffix(fsm: AgentFsm): String =
        "\nStill needed: ${fsm.objective.missingLabels()}\nKnown: none\nThey said: \"\"\n"

    /** Live Intercom: plain translation, no grammar. */
    fun translate(target: String, text: String): String =
        "Translate to $target. Reply with the translation only.\n\"$text\"\n"
}
