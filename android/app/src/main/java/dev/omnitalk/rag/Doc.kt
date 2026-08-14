package dev.omnitalk.rag

/**
 * A passage of a document, small enough to fit several into the model's context
 * and large enough to answer a question on its own.
 */
data class Chunk(
    val id: Int,
    val page: Int,
    val text: String
) {
    /** Lowercased word tokens, used by both indexing and querying. */
    val terms: List<String> by lazy { tokenize(text) }

    /**
     * The first line, treated as the passage's heading.
     *
     * Slide decks are written as "title, then content", so a slide called
     * "The four Coffman conditions" is almost certainly the answer to
     * "what are the four Coffman conditions". Plain BM25 misses this: it
     * ranked a short Summary slide above the real definition, because the
     * definition slide was longer and length normalisation punished it.
     */
    val headingTerms: Set<String> by lazy {
        tokenize(text.lineSequence().firstOrNull().orEmpty()).toSet()
    }

    /** True when the passage carries a numbered or bulleted list. */
    val hasList: Boolean by lazy {
        text.lineSequence().any { Regex("^\\s*(\\d+[.)]|[-*•])\\s+").containsMatchIn(it) }
    }

    companion object {
        private val SPLIT = Regex("[^\\p{L}\\p{N}]+")
        fun tokenize(s: String): List<String> =
            s.lowercase().split(SPLIT).filter { it.length > 1 && it !in STOP }

        // Only the words that would otherwise dominate every score. Deliberately
        // short — over-filtering hurts recall on technical documents.
        private val STOP = setOf(
            "the", "and", "for", "are", "but", "not", "you", "all", "can", "her", "was",
            "one", "our", "out", "day", "get", "has", "him", "his", "how", "its", "may",
            "new", "now", "old", "see", "two", "who", "did", "yes", "this", "that",
            "with", "from", "they", "have", "been", "were", "will", "your", "what",
            "when", "there", "their", "would", "which", "these", "those", "than", "then"
        )
    }
}

data class Document(
    val id: String,
    val title: String,
    val pageCount: Int,
    val chunks: List<Chunk>,
    val charCount: Int
) {
    val indexed: Boolean get() = chunks.isNotEmpty()
}

/**
 * Splits extracted page text into overlapping passages.
 *
 * Overlap matters: a fact that straddles a chunk boundary is otherwise
 * unretrievable, and boundaries fall in arbitrary places. 15% costs little and
 * removes a whole class of "the answer is in the PDF but it never finds it".
 */
object Chunker {
    const val TARGET_CHARS = 800
    const val OVERLAP_CHARS = 120

    fun chunk(pages: List<String>): List<Chunk> {
        val out = ArrayList<Chunk>()
        var id = 0
        for ((idx, raw) in pages.withIndex()) {
            val page = idx + 1
            val text = raw.replace(Regex("[ \\t]+"), " ")
                .replace(Regex("\\n{3,}"), "\n\n")
                .trim()
            if (text.length < 40) continue          // page numbers, headers, blanks

            var start = 0
            while (start < text.length) {
                var end = minOf(start + TARGET_CHARS, text.length)
                if (end < text.length) {
                    // prefer a sentence boundary, then any whitespace
                    val window = text.substring(start, end)
                    val cut = window.lastIndexOfAny(charArrayOf('.', '!', '?', '\n'))
                    end = when {
                        cut > TARGET_CHARS / 2 -> start + cut + 1
                        else -> {
                            val sp = window.lastIndexOf(' ')
                            if (sp > TARGET_CHARS / 2) start + sp else end
                        }
                    }
                }
                val piece = text.substring(start, end).trim()
                if (piece.length >= 40) out.add(Chunk(id++, page, piece))
                if (end >= text.length) break
                start = maxOf(end - OVERLAP_CHARS, start + 1)
            }
        }
        return out
    }
}
