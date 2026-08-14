package dev.omnitalk.rag

import kotlin.math.ln

/**
 * BM25 retrieval over one document's chunks.
 *
 * Deliberately not embeddings, at least to begin with:
 *   - no extra model to download, load, or fit in RAM alongside the LLM
 *   - indexing a 100-page PDF takes milliseconds instead of a minute of encoding
 *   - it never silently fails; if a term is in the document, it is findable
 *
 * That matters for a document Q&A tool, where questions usually share vocabulary
 * with the source (names, numbers, section headings). Semantic search wins on
 * paraphrase, and Retriever keeps a seam for adding it as a second scorer.
 */
class Bm25(private val chunks: List<Chunk>) {

    private val k1 = 1.5

    // 0.5, not the usual 0.75.
    // Length normalisation exists to stop long documents winning by sheer size.
    // Here "long" means "the slide that actually lists all four conditions",
    // and at 0.75 that slide lost to a two-line Summary slide mentioning the
    // same words. Passages are already a bounded size, so heavy normalisation
    // only punishes the detailed answer.
    private val b = 0.5

    private val df = HashMap<String, Int>()
    private val tf = ArrayList<Map<String, Int>>(chunks.size)
    private val len = IntArray(chunks.size)
    private var avgLen = 1.0

    init {
        for ((i, c) in chunks.withIndex()) {
            val counts = HashMap<String, Int>()
            for (t in c.terms) counts[t] = (counts[t] ?: 0) + 1
            tf.add(counts)
            len[i] = c.terms.size
            for (t in counts.keys) df[t] = (df[t] ?: 0) + 1
        }
        if (chunks.isNotEmpty()) avgLen = len.average().coerceAtLeast(1.0)
    }

    val size: Int get() = chunks.size

    fun search(query: String, topK: Int): List<Scored> {
        if (chunks.isEmpty()) return emptyList()
        val qTerms = Chunk.tokenize(query)
        if (qTerms.isEmpty()) return emptyList()
        val wantsEnumeration = ENUMERATING.containsMatchIn(query)

        val n = chunks.size.toDouble()
        val scores = DoubleArray(chunks.size)

        for (t in qTerms) {
            val d = df[t] ?: continue
            // BM25 IDF. The +0.5s keep it positive for terms that appear almost
            // everywhere, which would otherwise score negative and push good
            // chunks down.
            val idf = ln(1.0 + (n - d + 0.5) / (d + 0.5))
            for (i in chunks.indices) {
                val f = tf[i][t] ?: continue
                val norm = f * (k1 + 1) / (f + k1 * (1 - b + b * len[i] / avgLen))
                scores[i] += idf * norm
            }
        }

        // Structural signals BM25 cannot see.
        //
        // BM25 treats a passage as a bag of words, so it cannot tell a slide
        // TITLED "The four Coffman conditions" from a passing mention in a
        // summary. On lecture slides the title is the strongest evidence there
        // is, and a question asking "what are the four X" is answered by the
        // slide that actually enumerates them.
        for (i in chunks.indices) {
            if (scores[i] <= 0.0) continue
            val c = chunks[i]

            val headHits = qTerms.count { it in c.headingTerms }
            if (headHits > 0) {
                val coverage = headHits.toDouble() / qTerms.distinct().size
                scores[i] *= 1.0 + HEADING_WEIGHT * coverage
            }

            // "what are the four ...", "list the ...", "which ..." want the
            // passage that enumerates, not the one that alludes.
            if (c.hasList && wantsEnumeration) scores[i] *= 1.0 + LIST_WEIGHT
        }

        return scores.indices
            .filter { scores[it] > 0.0 }
            .sortedByDescending { scores[it] }
            .take(topK)
            .map { Scored(chunks[it], scores[it]) }
    }

    private companion object {
        const val HEADING_WEIGHT = 0.9
        const val LIST_WEIGHT = 0.35
        val ENUMERATING = Regex(
            "\\b(what are|which|list|name the|how many|types of|kinds of|steps|conditions)\\b",
            RegexOption.IGNORE_CASE
        )
    }
}

data class Scored(val chunk: Chunk, val score: Double)
