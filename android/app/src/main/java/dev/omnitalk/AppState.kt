package dev.omnitalk

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.omnitalk.rag.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private const val MODEL_FILE = "Llama-3.2-1B-Instruct-Q4_0.gguf"

/**
 * All app state, as a ViewModel.
 *
 * It used to be a plain object created in onCreate, which meant any
 * configuration change - rotating, the system toggling dark mode, plugging in a
 * keyboard - rebuilt it and silently discarded the loaded deck, the answer on
 * screen and every generated flashcard. A ViewModel outlives Activity
 * recreation, so work the reader waited 30 seconds for survives.
 */
class AppState(app: Application) : AndroidViewModel(app) {

    private val ctx get() = getApplication<Application>()
    private val scope get() = viewModelScope

    val topo = Topology.read()
    val engine = RagEngine()
    val settings = Settings(ctx)
    private val store = DocStore(ctx)

    var modelReady by mutableStateOf(false); private set
    var status by mutableStateOf(""); private set

    val docs = mutableStateListOf<Document>()
    var current by mutableStateOf<Document?>(null); private set

    var busy by mutableStateOf(false); private set
    var busyLabel by mutableStateOf("")

    // ask
    var question by mutableStateOf("")
    var answer by mutableStateOf("")
    var streaming by mutableStateOf(false); private set
    var passages by mutableStateOf<List<Scored>>(emptyList()); private set
    val history = mutableStateListOf<QA>()
    var askedQuestion by mutableStateOf("")
    /** Suggested questions, derived from THIS deck rather than hard-coded. */
    var suggestions by mutableStateOf<List<String>>(emptyList()); private set

    // measurements
    var lastDecodeTps by mutableStateOf(0.0)
    var lastTtftMs by mutableStateOf(0L)
    var evidenceShownMs by mutableStateOf(0L)

    // reader
    var readerPage by mutableStateOf(1)
    var requestedTab by mutableStateOf(-1)

    // study
    var scopeMode by mutableStateOf(ScopeMode.Topic)
    /** Titles the reader ticked, from the deck's own headings. */
    val selectedTopics = mutableStateListOf<String>()
    var pageFrom by mutableStateOf(1)
    var pageTo by mutableStateOf(1)
    var studyCount by mutableStateOf(5)
    var cards by mutableStateOf<List<Card>>(emptyList()); private set
    var studying by mutableStateOf(false); private set
    var studyMs by mutableStateOf(0L)
    var cardsScope by mutableStateOf(""); private set
    val revealed = mutableStateListOf<Int>()

    // practice
    var practiceStarted by mutableStateOf(false)
    var practiceIndex by mutableStateOf(0)
    var practiceRevealed by mutableStateOf(false)
    val known = mutableStateListOf<Int>()

    enum class ScopeMode { Deck, Pages, Topic }

    data class QA(val q: String, val a: String, val pages: List<Int>, val ms: Long)

    private val pagesById = HashMap<String, List<String>>()
    /** Original PDF URI, kept in RAM only — content:// grants are not persisted. */
    private val pdfUriById = HashMap<String, Uri>()
    val modelPath: File get() = File(ctx.getExternalFilesDir(null), MODEL_FILE)

    init { boot() }

    private fun boot() {
        PdfText.init(ctx)
        // Decks first, so the library is populated even while the model loads.
        scope.launch {
            val saved = withContext(Dispatchers.IO) { store.loadAll() }
            for ((doc, pages) in saved) {
                pagesById[doc.id] = pages
                docs.add(doc)
                val t = withContext(Dispatchers.IO) { store.loadTopics(doc) }
                if (t.isNotEmpty()) generatedTopics[doc.id] = t
            }
            saved.firstOrNull()?.let { select(it.first) }
        }
        if (!modelPath.exists()) {
            status = "Model missing. Push it to ${modelPath.parent}"
            return
        }
        status = "Measuring this phone"
        scope.launch {
            val ok = engine.load(modelPath.absolutePath, topo)
            if (ok) {
                settings.applyCalibration(engine.charBudget, engine.decodeThreads, engine.prefillThreads)
                applySettings()
            }
            modelReady = ok
            if (ok) flash("Tuned for this phone: %.0f tokens/sec".format(engine.prefillTps))
            else status = "Could not load the model"
        }
    }

    /** Show a message, then take it away. A banner that never leaves is furniture. */
    private fun flash(text: String, ms: Long = 2600) {
        status = text
        scope.launch { delay(ms); if (status == text) status = "" }
    }

    fun applySettings() {
        engine.charBudget = settings.charBudget
        engine.maxAnswerTokens = settings.maxAnswerTokens
    }

    fun reloadModel() {
        if (!modelReady) return
        modelReady = false
        status = "Applying new settings"
        scope.launch {
            engine.close()
            val ok = engine.load(
                modelPath.absolutePath, topo,
                decodeOverride = settings.decodeThreads,
                prefillOverride = settings.prefillThreads
            )
            current?.let { engine.setDocument(it) }
            applySettings()
            modelReady = ok
            status = if (ok) "" else "Could not reload the model"
        }
    }

    // ---- documents ----------------------------------------------------------

    fun importDoc(uri: Uri) {
        if (busy) return
        busy = true
        busyLabel = "Reading document"
        scope.launch {
            val name = displayName(uri)
            val res = withContext(Dispatchers.IO) {
                if (name.lowercase().endsWith(".pdf")) PdfText.extract(ctx, uri, name)
                else PdfText.extractText(ctx, uri, name)
            }
            if (res.error != null || !res.hasText) {
                status = res.error ?: "No readable text in $name"
                busy = false
                return@launch
            }
            busyLabel = "Indexing"
            val chunks = withContext(Dispatchers.Default) { Chunker.chunk(res.pages) }
            val doc = Document(
                id = uri.toString(), title = res.title, pageCount = res.pages.size,
                chunks = chunks, charCount = res.charCount
            )
            pagesById[doc.id] = res.pages
            pdfUriById[doc.id] = uri
            docs.removeAll { it.id == doc.id }
            docs.add(0, doc)
            withContext(Dispatchers.IO) { store.save(doc, res.pages) }
            select(doc)
            flash("Indexed ${doc.pageCount} pages into ${chunks.size} passages")
            busy = false
        }
    }

    fun loadSample() {
        if (busy) return
        busy = true
        busyLabel = "Loading sample deck"
        scope.launch {
            val text = withContext(Dispatchers.IO) {
                ctx.assets.open("sample_lecture.txt").bufferedReader().use { it.readText() }
            }
            val pages = text.split(Regex("(?m)^--- Slide \\d+ ---$"))
                .map { it.trim() }.filter { it.isNotEmpty() }
            val chunks = withContext(Dispatchers.Default) { Chunker.chunk(pages) }
            // No "(sample)" in the title: a suffix in the name makes the library
            // look like it has a test file in it. It is marked with a badge.
            val doc = Document(
                id = "sample://cse314", title = "CSE 314 - Deadlocks",
                pageCount = pages.size, chunks = chunks, charCount = text.length
            )
            pagesById[doc.id] = pages
            docs.removeAll { it.id == doc.id }
            docs.add(0, doc)
            withContext(Dispatchers.IO) { store.save(doc, pages) }
            select(doc)
            flash("Sample deck ready")
            busy = false
        }
    }

    fun pagesOf(d: Document): List<String> = pagesById[d.id] ?: emptyList()
    /** Returns the original PDF Uri if the user imported it this session, null otherwise. */
    fun pdfUriOf(d: Document): Uri? = pdfUriById[d.id]

    fun deleteDoc(d: Document) {
        docs.remove(d)
        pagesById.remove(d.id)
        store.delete(d)
        if (current?.id == d.id) {
            current = null
            docs.firstOrNull()?.let { select(it) }
        }
    }

    fun select(d: Document) {
        current = d
        engine.setDocument(d)
        history.clear()
        answer = ""; askedQuestion = ""; question = ""
        passages = emptyList()
        readerPage = 1
        pageFrom = 1; pageTo = minOf(d.pageCount, 5)
        selectedTopics.clear()
        clearCards()
        buildSuggestions(d)
    }

    /**
     * Suggested questions built from the deck's own headings.
     *
     * They used to be three hard-coded strings about deadlocks, so importing a
     * chemistry PDF still offered "What are the four Coffman conditions?" -
     * which reads as broken. Headings make good questions because slide titles
     * are already the topic, phrased.
     */
    private fun buildSuggestions(d: Document) {
        val heads = d.chunks
            .asSequence()
            .map { it.text.lineSequence().firstOrNull().orEmpty().trim() }
            .filter { it.length in 8..60 && !it.endsWith(".") }
            .distinct()
            .take(8)
            .toList()
        suggestions = heads.shuffled().take(3).map { h ->
            if (h.split(" ").size <= 3) "What is $h?" else "Explain: $h"
        }
    }

    // ---- ask ----------------------------------------------------------------

    fun ask() {
        val q = question.trim()
        if (q.isEmpty() || current == null || !modelReady || streaming) return
        streaming = true
        answer = ""
        askedQuestion = q
        question = ""
        val t0 = System.currentTimeMillis()
        var first = 0L

        scope.launch {
            passages = engine.retrieve(q)
            evidenceShownMs = System.currentTimeMillis() - t0
            if (passages.isEmpty()) {
                answer = "Not in these slides."
                streaming = false
                history.add(0, QA(q, answer, emptyList(), System.currentTimeMillis() - t0))
                return@launch
            }
            val sb = StringBuilder()
            engine.answer(q, passages) { piece ->
                if (first == 0L) { first = System.currentTimeMillis(); lastTtftMs = first - t0 }
                sb.append(piece); answer = sb.toString()
            }
            Regex("\"decode_tps\":([\\d.]+)").find(engine.lastTimings)
                ?.let { lastDecodeTps = it.groupValues[1].toDouble() }
            streaming = false
            history.add(0, QA(q, answer, passages.map { it.chunk.page }.distinct(),
                System.currentTimeMillis() - t0))
        }
    }

    // ---- study --------------------------------------------------------------

    private fun clearCards() {
        cards = emptyList(); revealed.clear(); known.clear()
        practiceIndex = 0; practiceRevealed = false; cardsScope = ""
    }

    /** Model-named topics per deck, once generated. Survives restarts. */
    private val generatedTopics = mutableStateMapOf<String, List<Topic>>()
    var topicPass by mutableStateOf<Pair<Int, Int>?>(null); private set

    /** Generated topics if the pass has been run for this deck, else the headings. */
    fun topics(): List<Topic> {
        val d = current ?: return emptyList()
        return generatedTopics[d.id] ?: d.topics
    }

    fun usingGeneratedTopics(): Boolean = current?.let { generatedTopics.containsKey(it.id) } == true

    /** True when the deck's own headings are too poor to choose from. */
    fun headingsWeak(): Boolean = (current?.headingQuality ?: 1f) < 0.5f

    /**
     * One pass over the deck to name its topics properly, then never again.
     * Saved with the deck, so it survives restarts and is not recomputed.
     */
    fun generateTopics() {
        val d = current ?: return
        if (!modelReady || studying || topicPass != null) return
        scope.launch {
            topicPass = 0 to 1
            val t = engine.generateTopics { i, n -> topicPass = i to n }
            topicPass = null
            if (t.isEmpty()) { flash("Could not read topics from this deck"); return@launch }
            generatedTopics[d.id] = t
            selectedTopics.clear()
            withContext(Dispatchers.IO) { store.save(d, pagesOf(d), t) }
            flash("Found ${t.size} topics in ${d.title}")
        }
    }

    fun toggleTopic(t: String) {
        if (selectedTopics.contains(t)) selectedTopics.remove(t) else selectedTopics.add(t)
    }

    fun currentScope(): Scope = when (scopeMode) {
        ScopeMode.Deck -> Scope.WholeDeck
        ScopeMode.Pages -> Scope.Pages(minOf(pageFrom, pageTo), maxOf(pageFrom, pageTo))
        ScopeMode.Topic -> {
            val picked = topics().filter { it.title in selectedTopics }
            if (picked.isEmpty()) Scope.WholeDeck
            else Scope.Topics(picked.map { it.title }, picked.flatMap { it.chunkIds }.toSet())
        }
    }

    fun generateStudy() {
        if (!modelReady || studying || current == null) return
        val scopeSel = currentScope()
        studying = true
        clearCards()
        val t0 = System.currentTimeMillis()
        scope.launch {
            val sb = StringBuilder()
            val (raw, _) = engine.study(scopeSel, studyCount) { piece ->
                sb.append(piece)
                cards = StudyPrompt.parse(sb.toString()) { t -> engine.pageFor(t) }
            }
            cards = StudyPrompt.parse(raw.ifBlank { sb.toString() }) { t -> engine.pageFor(t) }
            studyMs = System.currentTimeMillis() - t0
            cardsScope = scopeSel.describe()
            studying = false
            if (cards.isEmpty()) flash("Nothing usable found in ${scopeSel.describe()}")
        }
    }

    fun toggleReveal(i: Int) { if (revealed.contains(i)) revealed.remove(i) else revealed.add(i) }

    // ---- cross-links --------------------------------------------------------
    //
    // Ask and Study were two islands: you could get an answer about slide 4 and
    // then have to walk to the other tab and hunt for slide 4 again to revise
    // it. These turn four features into one product - each screen offers the
    // obvious next thing to do with what is on it.

    /** From an answer or a card, jump to Ask with the question already asked. */
    fun askAbout(text: String) {
        question = text
        requestedTab = TAB_ASK
        ask()
    }

    /** From an answer's citation, make cards from exactly that slide. */
    fun makeCardsFromPage(page: Int) {
        scopeMode = ScopeMode.Pages
        pageFrom = page; pageTo = page
        requestedTab = TAB_STUDY
        generateStudy()
    }

    /** From a topic, ask about it instead of revising it. */
    fun askAboutTopic(t: Topic) = askAbout("Explain ${t.title}")

    companion object {
        const val TAB_ASK = 1
        const val TAB_STUDY = 2
    }

    // ---- practice -----------------------------------------------------------

    fun startPractice() {
        practiceStarted = true; practiceIndex = 0; practiceRevealed = false; known.clear()
    }

    fun stopPractice() { practiceStarted = false }

    fun practiceNext(gotIt: Boolean) {
        if (gotIt) known.add(practiceIndex) else known.remove(practiceIndex)
        practiceRevealed = false
        if (practiceIndex < cards.lastIndex) practiceIndex++
        else { practiceStarted = false; flash("Practised ${cards.size} cards, ${known.size} known") }
    }

    private fun displayName(uri: Uri): String {
        ctx.contentResolver.query(uri, null, null, null, null)?.use { c ->
            val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (i >= 0 && c.moveToFirst()) return c.getString(i)
        }
        return uri.lastPathSegment ?: "document"
    }

    override fun onCleared() { engine.close(); super.onCleared() }
}
