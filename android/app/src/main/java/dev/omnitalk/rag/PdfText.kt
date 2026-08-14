package dev.omnitalk.rag

import android.content.Context
import android.net.Uri
import android.util.Log
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.File

/**
 * PDF -> per-page text, fully offline.
 *
 * PdfBox-Android is used because Android's own PdfRenderer only rasterises pages
 * to bitmaps — it exposes no text layer at all, so it cannot support search or
 * retrieval without OCR.
 *
 * Scanned PDFs have no text layer. We detect that (near-zero extracted
 * characters) and say so plainly rather than indexing an empty document and
 * letting the user wonder why every answer is "not in this document". OCR is
 * out of scope.
 */
object PdfText {
    private const val TAG = "otpdf"

    data class Result(
        val pages: List<String>,
        val title: String,
        val charCount: Int,
        val error: String? = null
    ) {
        val hasText: Boolean get() = charCount > 200
    }

    fun extract(ctx: Context, uri: Uri, displayName: String): Result {
        return try {
            ctx.contentResolver.openInputStream(uri).use { input ->
                if (input == null) return Result(emptyList(), displayName, 0, "cannot open file")
                PDDocument.load(input).use { doc ->
                    val n = doc.numberOfPages
                    val stripper = PDFTextStripper()
                    val pages = ArrayList<String>(n)
                    var total = 0
                    for (p in 1..n) {
                        stripper.startPage = p
                        stripper.endPage = p
                        val t = runCatching { stripper.getText(doc) }.getOrDefault("")
                        pages.add(t)
                        total += t.length
                    }
                    val title = doc.documentInformation?.title?.takeIf { it.isNotBlank() }
                        ?: displayName.removeSuffix(".pdf")
                    Log.i(TAG, "extracted $n pages, $total chars from $displayName")
                    val err = if (total <= 200)
                        "No text layer found. This looks like a scanned PDF — OCR is not supported."
                    else null
                    Result(pages, title, total, err)
                }
            }
        } catch (e: Throwable) {
            Log.e(TAG, "extract failed", e)
            Result(emptyList(), displayName, 0, e.message ?: "could not read PDF")
        }
    }

    /** Plain text files are accepted too — useful for testing and costs nothing. */
    fun extractText(ctx: Context, uri: Uri, displayName: String): Result {
        return try {
            val text = ctx.contentResolver.openInputStream(uri)!!
                .bufferedReader().use { it.readText() }
            // paginate at ~2500 chars so citations still point somewhere useful
            val pages = text.chunked(2500)
            Result(pages, displayName.substringBeforeLast('.'), text.length)
        } catch (e: Throwable) {
            Result(emptyList(), displayName, 0, e.message ?: "could not read file")
        }
    }

    /** PdfBox needs its font/resource cache primed once per process. */
    fun init(ctx: Context) {
        runCatching {
            com.tom_roush.pdfbox.android.PDFBoxResourceLoader.init(ctx.applicationContext)
        }.onFailure { Log.e(TAG, "PDFBox init failed", it) }
    }

    fun cacheDirFor(ctx: Context): File =
        File(ctx.filesDir, "docs").also { it.mkdirs() }
}
