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
 * Everything a deck remembers between sessions beyond its text.
 *
 * [known] holds indices into [cards], which is only meaningful while those exact
 * cards exist — so the two are always written and read together. Regenerating
 * cards therefore resets what you knew, which is correct: they are different
 * questions.
 *
 * [askedPages] and [cardedPages] are what makes "have I covered this deck?"
 * answerable. The app already knows which slide answered each question and which
 * slides each card came from; it simply used to throw that away.
 */
data class StudyState(
    val cards: List<Card> = emptyList(),
    val cardsScope: String = "",
    val known: Set<Int> = emptySet(),
    val askedPages: Set<Int> = emptySet(),
    val cardedPages: Set<Int> = emptySet()
) {
    /** Slides that have either answered a question or produced a card. */
    val touchedPages: Set<Int> get() = askedPages + cardedPages

    /** Cards a completed practice run did not mark as known. */
    fun missed(): List<Int> = cards.indices.filter { it !in known }
}

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
        // THE WORKED EXAMPLES LIVE HERE, NOT NEXT TO THE NOTES.
        //
        // They used to sit two lines above the assistant header, which made them
        // the most recent thing in context at the moment generation started — and
        // a 1B model handed them straight back as content. Every deck produced
        // "What do roots do?" and "Why do leaves need sunlight?" no matter what
        // the slides said. Moving them into the system turn puts the whole of the
        // notes between the example and the first generated token, so the nearest
        // material to copy is the student's own.
        //
        // They stay concrete rather than abstract: with a placeholder pair like
        // "Q: What is X? | A: X is Y", a slide of bare terms produced
        // "Q: Mutual exclusion | A: Mutual exclusion", because nothing showed the
        // two halves having to differ. And they stay about plant biology, which no
        // deck here is about, so anything copied is obvious rather than plausible.
        sb.append(" Each card is one line, a real question on the left and an ")
        sb.append("explanation on the right, like these two:\n\n")
        sb.append("Q: What do roots do? | A: They draw water and minerals out of the soil\n")
        sb.append("Q: Why do leaves need sunlight? | A: Light powers the reaction that makes sugar\n\n")
        sb.append("Those show the shape only. The content of every card you write comes ")
        sb.append("from the notes the student gives you.")
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
        sb.append("Please make $count flashcards from these notes above. ")
        // The shape is demonstrated once, in the system turn. Repeating a worked
        // example here would put copyable content back next to the generation
        // point, which is the bug this ordering exists to avoid — so this is a
        // reminder of the format with nothing in it worth copying.
        sb.append("Ask a real question on the left, and answer it on the right using what my ")
        sb.append("notes say. One card per line, in the ")
        sb.append("\"Q: ... | A: ...\" shape. ")
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
            if (degenerate(front, back)) continue
            if (echoesExample(front, back)) continue
            out.add(Card(question(front), back, pageFor("$front $back")))
        }
        return out
    }

    /** The model often drops the question mark. Restore it rather than show "What is a deadlock". */
    private fun question(front: String): String =
        if (front.endsWith("?") || front.endsWith(".") || front.endsWith(":")) front
        else if (Regex("^(?i)(what|why|how|when|where|which|who|name|list|define|give)\\b")
                .containsMatchIn(front)) "$front?"
        else front

    /**
     * A card whose back merely restates its front teaches nothing.
     *
     * Slides that are just a list of bare terms ("Mutual exclusion / Hold and
     * wait / ...") tempt the model into copying the term across both halves.
     * Such a card looks fine in a list and is worthless the moment it is turned
     * over, so it is dropped rather than shown — a short deck of real cards
     * beats a full one padded with echoes.
     */
    private fun degenerate(front: String, back: String): Boolean {
        fun norm(s: String) = s.lowercase().replace(Regex("[^a-z0-9 ]"), " ")
            .replace(Regex("\\s+"), " ").trim()
            .removePrefix("what is ").removePrefix("what does ").removePrefix("what are ")
            .removeSuffix(" mean").trim()
        val f = norm(front)
        val b = norm(back)
        if (f.isEmpty() || b.isEmpty()) return true
        return f == b || (b.length < f.length + 12 && (f.contains(b) || b.contains(f)))
    }

    /**
     * The worked example, handed back as if it were a card.
     *
     * Prompt ordering makes this rare; it does not make it impossible, and a 1B
     * model asked the same question twice will not always answer the same way
     * about where its content should come from. Every deck was producing
     * "What do roots do?" and "Why do leaves need sunlight?" regardless of the
     * slides, so the symptom is caught here as well as discouraged there —
     * a check that cannot fail the way a phrasing can.
     *
     * Matching on the examples' subject rather than their wording is what makes
     * this work: the model paraphrases as it copies ("what does the roots do"),
     * so the strings differ while the vocabulary does not. Two hits are required
     * so a single incidental "light" in a physics deck survives.
     *
     * The cost is a deck genuinely about plant biology, whose real cards would be
     * dropped. That is the trade the examples' subject already made, and it is
     * the right way round: a missing card is visible, an invented one is not.
     */
    private fun echoesExample(front: String, back: String): Boolean {
        val text = "$front $back".lowercase()
        return EXAMPLE_TERMS.count { Regex("\\b$it\\b").containsMatchIn(text) } >= 2
    }

    /** Content words of the two worked examples in [build]'s system turn. */
    private val EXAMPLE_TERMS = listOf(
        "roots", "soil", "minerals", "leaves", "sunlight", "sugar", "photosynthesis"
    )

    private fun clean(s: String) = s
        .replace("**", "")
        .replace(Regex("(?m)^\\s*Card\\s*\\d+\\s*$"), "")
        .trim()
        .trim('|', '-', ':', ' ', '\n')
        .trim()
}
