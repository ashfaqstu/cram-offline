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

/** A heading found in the deck, and the slides it covers. */
data class Topic(val title: String, val fromPage: Int, val toPage: Int, val chunkIds: List<Int>) {
    val pages: String get() = if (fromPage == toPage) "slide $fromPage" else "slides $fromPage-$toPage"
}

data class Document(
    val id: String,
    val title: String,
    val pageCount: Int,
    val chunks: List<Chunk>,
    val charCount: Int
) {
    val indexed: Boolean get() = chunks.isNotEmpty()
    val isSample: Boolean get() = id.startsWith("sample://")

    /**
     * The deck's topics, taken from its own slide headings.
     *
     * Read once when the deck is indexed and reused from then on. Asking the
     * reader to type a topic made them guess at words the deck might use, and a
     * near-miss quietly produced cards about the wrong slides. The headings are
     * already the topics, written by whoever made the deck.
     *
     * Derived rather than generated: extracting headings is string work that
     * takes milliseconds, where asking the model to summarise every slide would
     * take minutes on this hardware and could invent topics the deck lacks.
     */
    val topics: List<Topic> by lazy { extractTopics(chunks) }

    /**
     * How well the headings worked.
     *
     * When a deck has no real slide titles - continuous prose, scanned notes,
     * a paper - every chunk falls back to "Slide N" and the topic list is
     * useless for choosing what to revise. Measuring that lets the app offer
     * the slower LLM pass exactly when it is worth its minute, instead of
     * always or never.
     */
    val headingQuality: Float by lazy {
        if (topics.isEmpty()) 0f
        else topics.count { !it.title.startsWith("Slide ") }.toFloat() / topics.size
    }

    companion object {
        private fun looksLikeHeading(s: String): Boolean {
            val t = s.trim()
            if (t.length !in 3..70) return false
            if (t.endsWith(".") || t.endsWith(",")) return false
            if (t.count { it == ' ' } > 9) return false          // a sentence, not a title
            return t.firstOrNull()?.isLetterOrDigit() == true
        }

        fun extractTopics(chunks: List<Chunk>): List<Topic> {
            val out = LinkedHashMap<String, MutableList<Chunk>>()
            var currentTitle: String? = null
            for (c in chunks) {
                val head = c.text.lineSequence().firstOrNull()?.trim().orEmpty()
                if (looksLikeHeading(head)) currentTitle = head
                val key = currentTitle ?: "Slide ${c.page}"
                out.getOrPut(key) { mutableListOf() }.add(c)
            }
            return out.map { (title, cs) ->
                Topic(
                    title = title,
                    fromPage = cs.minOf { it.page },
                    toPage = cs.maxOf { it.page },
                    chunkIds = cs.map { it.id }
                )
            }
        }
    }
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
