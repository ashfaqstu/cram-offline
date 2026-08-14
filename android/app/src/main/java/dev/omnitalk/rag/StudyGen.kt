package dev.omnitalk.rag

import org.json.JSONArray

/**
 * Generating study material from the slides: flashcards and quiz questions.
 *
 * Deliberately the same retrieve-then-generate path as answering a question.
 * The topic the reader types ("deadlock detection", "the Banker's algorithm")
 * is a retrieval query first, so the model only ever sees slides that are
 * genuinely about that topic — it is not asked to recall anything, only to
 * reshape text placed in front of it. That is the difference between a study
 * aid built from your slides and one that quietly makes things up.
 */
data class Card(val front: String, val back: String, val page: Int)

/**
 * How to choose which slides the cards come from.
 *
 * A topic is only a retrieval query, so a vague one pulls loosely related
 * slides and the cards wander. Most of the time the reader already knows the
 * answer in page numbers - "chapter 3 is slides 12 to 20" - and a range is an
 * exact instruction where a topic is a guess.
 */
sealed interface Scope {
    data object WholeDeck : Scope
    data class Pages(val from: Int, val to: Int) : Scope
    /** Topics picked from the deck's own headings — exact, not a search. */
    data class Topics(val titles: List<String>, val chunkIds: Set<Int>) : Scope

    fun describe(): String = when (this) {
        WholeDeck -> "the whole deck"
        is Pages -> if (from == to) "slide $from" else "slides $from to $to"
        is Topics -> when (titles.size) {
            0 -> "no topic"
            1 -> titles.first()
            2 -> "${titles[0]} and ${titles[1]}"
            else -> "${titles[0]} and ${titles.size - 1} more"
        }
    }
}

object StudyPrompt {

    /**
     * Asks for one item per line in a fixed shape rather than JSON.
     *
     * A 1B model produces malformed JSON often enough that parsing it becomes
     * the dominant failure mode, and a half-written array is unrecoverable
     * mid-stream. Line-delimited output degrades gracefully: if generation is
     * cut short, every complete line before the cut is still usable.
     */
    /**
     * PHRASED AS A NORMAL REQUEST, NOT AS CONSTRAINTS.
     *
     * The first version opened with "Use ONLY the excerpts given. Do not invent
     * facts. No extra text." and Llama 3.2 1B refused outright: "I can't help
     * with writing text that may promote or facilitate illegal activities."
     * Terse stacked prohibitions read like a jailbreak attempt to a small,
     * heavily safety-tuned model. Asking the way a student would ask gets
     * exactly the same grounding, because the notes are right there in the
     * message and there is nothing else to draw on.
     */
    fun build(scope: Scope, passages: List<Chunk>, count: Int): String {
        val sb = StringBuilder()
        sb.append("<|begin_of_text|><|start_header_id|>system<|end_header_id|>\n\n")
        sb.append(
            "You are a friendly study assistant. You help students revise by turning " +
            "their lecture notes into flashcards."
        )
        sb.append("<|eot_id|><|start_header_id|>user<|end_header_id|>\n\n")
        sb.append("Here are notes from my lecture")
        if (scope is Scope.Topics && scope.titles.isNotEmpty()) {
            sb.append(" about ").append(scope.titles.joinToString(", "))
        }
        sb.append(":\n\n")
        for (c in passages) {
            sb.append("[slide ").append(c.page).append("] ").append(c.text.trim()).append("\n\n")
        }
        // "Just the lines on their own, please" is a request, not a prohibition.
        // Phrasing it as a rule ("no explanations, no extra text") is what
        // triggered the model's refusal earlier. Without it the model writes an
        // "Explanation:" paragraph after every card and exhausts the budget.
        sb.append("Please make $count flashcards from these notes. ")
        sb.append("Put each card on its own line like this:\n\nQ: What is X? | A: X is Y\n\n")
        sb.append("Just the $count lines on their own, please - no explanations.")
        sb.append("<|eot_id|><|start_header_id|>assistant<|end_header_id|>\n\n")
        return sb.toString()
    }

    /**
     * Deliberately forgiving.
     *
     * Asked for "Q: ... | A: ..." on one line, the model reliably produces the
     * right content in a slightly different shape — "**Card 2**" headings, the
     * "| A:" pushed onto the next line, bold markers. Rejecting those would
     * throw away correct flashcards over punctuation, so the parser splits on
     * "Q:" and looks for the next "A:" however it is separated.
     *
     * Safe to call on every token, so cards appear one at a time instead of
     * after a silent minute.
     */
    fun parse(raw: String, pageFor: (String) -> Int): List<Card> {
        val out = ArrayList<Card>()
        val blocks = raw.split(Regex("(?im)^\\s*(?:\\*\\*)?\\s*Q\\s*:")).drop(1)
        for (block in blocks) {
            // "A:" may start a line OR follow the pipe on the same line -- the
            // model uses both, sometimes within one response. Requiring line
            // start silently discarded every correctly-formed card.
            val aIdx = Regex("(?i)(?:^|\\||\\n)\\s*\\**\\s*A\\s*:", RegexOption.MULTILINE)
                .find(block) ?: continue
            val front = clean(block.substring(0, aIdx.range.first))
            var back = clean(block.substring(aIdx.range.last + 1))
            // The model likes to add "Explanation: ..." and a heading for the
            // next card. Both belong to something other than this answer.
            back = back
                .split(Regex("(?im)^\\s*(?:\\*\\*)?\\s*(?:Card\\s*\\d|Explanation\\s*:)"))
                .first().trim()
            if (front.length < 4 || back.length < 2) continue
            out.add(Card(front, back, pageFor("$front $back")))
        }
        return out
    }

    private fun clean(s: String) = s
        .replace("**", "")
        .replace(Regex("(?m)^\\s*Card\\s*\\d+\\s*$"), "")
        .trim()
        .trim('|', '-', ':', ' ', '\n')
        .trim()
}
