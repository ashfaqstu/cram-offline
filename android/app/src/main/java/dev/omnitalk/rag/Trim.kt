package dev.omnitalk.rag

/**
 * Second-stage retrieval: cut a passage down to the part that answers the question.
 *
 * BM25 finds the right ~800-character passage, but much of it is context the model
 * does not need. On this CPU prefill runs at only about twice decode speed, so
 * every character sent costs real waiting time — roughly 14 ms.
 *
 * IT TAKES A CONTIGUOUS WINDOW, NOT SCATTERED SENTENCES.
 * An earlier version kept each matching sentence plus its neighbour and dropped
 * everything else. On "The four Coffman conditions / A deadlock can arise only if
 * all four hold: / 1. Mutual exclusion... 2. Hold and wait..." the query words
 * appear only in the heading, so the numbered items — the actual answer — were
 * thrown away and the model confidently made something up. Structured content is
 * exactly what students ask about, so the window must stay whole.
 */
object Trim {

    // Do NOT split after a digit's full stop.
    //
    // "1. Mutual exclusion - ..." was being cut into "1." and "Mutual
    // exclusion...", so the list-item test below never matched, the window
    // stopped after the third condition, and the model invented a fourth. The
    // lookbehind requires a non-digit before the full stop, which keeps
    // numbered items whole while still splitting ordinary sentences.
    private val SENTENCE = Regex("(?<=[^0-9][.!?:])\\s+|\\n")
    /** "1." "2)" "-" "*" — a line that continues a list rather than starting a thought. */
    private val LIST_ITEM = Regex("^\\s*(\\d+[.)]|[-*•])\\s+")

    fun toQuery(text: String, query: String, maxChars: Int): String {
        if (text.length <= maxChars) return text
        val terms = Chunk.tokenize(query).toSet()
        if (terms.isEmpty()) return text.take(maxChars)

        val parts = text.split(SENTENCE).map { it.trim() }.filter { it.isNotEmpty() }
        if (parts.size <= 1) return text.take(maxChars)

        val hits = parts.indices.filter { i -> Chunk.tokenize(parts[i]).any { it in terms } }
        if (hits.isEmpty()) return text.take(maxChars)

        var start = hits.first()
        var end = hits.last()

        // Always carry on past the last hit while the following lines are list
        // items: a heading matches, the items beneath it are the content.
        while (end + 1 < parts.size && LIST_ITEM.containsMatchIn(parts[end + 1])) end++
        // Otherwise take one sentence of tail, since definitions often continue.
        if (end == hits.last() && end + 1 < parts.size) end++

        // Grow outward while there is budget, preferring what comes after.
        var used = (start..end).sumOf { parts[it].length + 1 }
        while (used < maxChars) {
            val canAfter = end + 1 < parts.size && used + parts[end + 1].length + 1 <= maxChars
            val canBefore = start > 0 && used + parts[start - 1].length + 1 <= maxChars
            when {
                canAfter -> { end++; used += parts[end].length + 1 }
                canBefore -> { start--; used += parts[start].length + 1 }
                else -> break
            }
        }

        return parts.subList(start, end + 1).joinToString(" ").take(maxChars)
    }
}
