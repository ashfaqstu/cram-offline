package dev.omnitalk.ui

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.omnitalk.AppState
import dev.omnitalk.rag.Document
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// ---- Slides -----------------------------------------------------------------

@Composable
fun LibraryScreen(vm: AppState, onPick: () -> Unit, onOpen: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        // The landing screen leads with the product, not with a status line.
        BrandHeader(
            if (vm.docs.isEmpty()) "Ask your lecture slides. No signal needed."
            else "${vm.docs.size} deck${if (vm.docs.size == 1) "" else "s"} on this phone"
        )

        if (vm.docs.isEmpty()) {
            EmptyState(
                "Exam tomorrow?",
                "Load your lecture slides and ask them anything. Everything is read and indexed " +
                "on this phone, so it works with no signal and nothing is ever uploaded."
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    // Offered first, because someone with no PDF to hand should
                    // still be able to see the whole thing work in one tap.
                    Button(onClick = { vm.loadSample(); onOpen() }, enabled = !vm.busy) {
                        Text("Try a sample deck")
                    }
                    Spacer(Modifier.height(Space.xs))
                    TextButton(onClick = onPick, enabled = !vm.busy) { Text("Or open your own PDF") }
                }
            }
        } else {
            LazyColumn(
                Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = Space.l, vertical = Space.s),
                verticalArrangement = Arrangement.spacedBy(Space.s)
            ) {
                itemsIndexed(vm.docs) { i, d ->
                    Appear(delayMs = i * 60) {
                        DocCard(
                            d, d.id == vm.current?.id,
                            onClick = { vm.select(d); onOpen() },
                            onDelete = { vm.deleteDoc(d) }
                        )
                    }
                }
            }
            Box(Modifier.fillMaxWidth().padding(Space.l)) {
                Button(onClick = onPick, enabled = !vm.busy, modifier = Modifier.fillMaxWidth()) {
                    Text("Open another PDF")
                }
            }
        }
    }
}

private fun <T> androidx.compose.foundation.lazy.LazyListScope.itemsIndexed(
    list: List<T>, content: @Composable (Int, T) -> Unit
) = items(list.size) { i -> content(i, list[i]) }

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DocCard(d: Document, selected: Boolean, onClick: () -> Unit, onDelete: () -> Unit) {
    var confirming by remember { mutableStateOf(false) }
    Card(Modifier.clickable(onClick = onClick), accent = selected) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(
                    d.title, style = MaterialTheme.typography.titleMedium,
                    maxLines = 2, color = Paper.Ink
                )
                if (selected) {
                    Spacer(Modifier.height(Space.xs))
                    // "opened" label: a small blue box sitting directly under the title
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Paper.Blue,
                        modifier = Modifier.wrapContentSize()
                    ) {
                        Text(
                            "opened",
                            style = MaterialTheme.typography.labelSmall,
                            color = androidx.compose.ui.graphics.Color.White,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            }
            // Two taps to delete, no dialog. A dialog for one destructive action
            // on a list item is heavier than the action deserves; asking in
            // place is enough to stop an accident.
            TextButton(onClick = { if (confirming) onDelete() else confirming = true }) {
                Text(
                    if (confirming) "Really?" else "Remove",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (confirming) Paper.RedPen else Paper.InkFaint
                )
            }
        }
        Spacer(Modifier.height(Space.s))
        // FlowRow, not Row: on a narrow screen the last pill was squeezed to a
        // few characters wide and wrapped one letter per line.
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Space.s),
            verticalArrangement = Arrangement.spacedBy(Space.s)
        ) {
            Pill("${d.pageCount} slides")
            Pill("${d.chunks.size} passages")
            if (d.isSample) Pill("sample", Tone.Mark)
        }
    }
}

// ---- Ask --------------------------------------------------------------------

// Fallback suggestions when the deck has no usable headings yet
private val FALLBACK_SUGGESTIONS = listOf(
    "What are the main topics covered here?",
    "Summarise the key points on slide 1",
    "What is the most important concept in this deck?"
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AskScreen(vm: AppState, onCite: (Int) -> Unit, onNavigateToSlides: () -> Unit) {
    val doc = vm.current
    if (doc == null) {
        EmptyState("Nothing loaded yet", "Open a deck in the Slides tab, then ask it anything.")
        return
    }
    val scroll = rememberScrollState()

    // Follow the answer as it writes itself, the way a page scrolls under a pen.
    LaunchedEffect(vm.answer) { if (vm.streaming) scroll.animateScrollTo(scroll.maxValue) }

    Column(Modifier.fillMaxSize()) {
        // Deck-title button — always visible, tapping it navigates to Slides.
        DeckTitleBar(title = doc.title, onNavigateToSlides = onNavigateToSlides)
        // The "Ask" header is always shown so the screen context is clear.
        // The question replaces the spacer below it once one has been asked.
        ScreenHeader("Ask", tight = true)
        if (vm.askedQuestion.isNotEmpty()) {
            Column(Modifier.fillMaxWidth().padding(start = Space.l, end = Space.l, top = Space.l, bottom = Space.s)) {
                Text(
                    vm.askedQuestion,
                    style = MaterialTheme.typography.headlineSmall,
                    color = Paper.Ink
                )
            }
        }

        Column(Modifier.weight(1f).verticalScroll(scroll).padding(horizontal = Space.l)) {

            if (vm.answer.isEmpty() && !vm.streaming && vm.passages.isEmpty()) {
                SectionLabel("Try one of these")
                Spacer(Modifier.height(Space.s))
                val shown = vm.suggestions.ifEmpty { FALLBACK_SUGGESTIONS }
                shown.forEachIndexed { i, s ->
                    Appear(delayMs = 80 * i) {
                        Card(Modifier.padding(bottom = Space.s).clickable {
                            vm.question = s; vm.ask()
                        }) { Text(s, style = MaterialTheme.typography.bodyMedium, color = Paper.Ink) }
                    }
                }

                // WHAT IS LEFT.
                //
                // The gap this closes: Cram could answer any question you already
                // had, but cramming's first question is "what should I even be
                // looking at?" - and at 1 a.m. with 40 slides you do not know what
                // you do not know. The app was already recording which slide
                // answered each question and which slides produced cards; it just
                // threw that away. Shown here it turns the deck into a checklist.
                val left = vm.untouchedPages()
                val covered = doc.pageCount - left.size
                // Shown even at zero. "11 slides, none covered, start on slide 1"
                // is the most useful thing this screen can say to someone who has
                // just opened a deck and does not know where to begin — which is
                // the state cramming actually starts in.
                Spacer(Modifier.height(Space.m))
                Card {
                    SectionLabel("Where you are")
                    Spacer(Modifier.height(Space.s))
                    LinearProgressIndicator(
                        progress = { covered.toFloat() / doc.pageCount.coerceAtLeast(1) },
                        modifier = Modifier.fillMaxWidth(),
                        color = Paper.Blue, trackColor = Paper.Rule
                    )
                    Spacer(Modifier.height(Space.s))
                    Text(
                        when {
                            left.isEmpty() ->
                                "Every slide has been asked about or turned into a card."
                            covered == 0 ->
                                "${doc.pageCount} slides, none covered yet. Ask about one, or " +
                                "make cards from it, and it counts as covered."
                            else ->
                                "$covered of ${doc.pageCount} slides covered. Not looked at yet: " +
                                left.take(6).joinToString(", ") +
                                if (left.size > 6) " and ${left.size - 6} more" else ""
                        },
                        style = MaterialTheme.typography.bodySmall, color = Paper.InkSoft
                    )
                    if (left.isNotEmpty()) {
                        Spacer(Modifier.height(Space.s))
                        TextButton(onClick = { vm.makeCardsFromPage(left.first()) }) {
                            Text("Start on slide ${left.first()}")
                        }
                    }
                }
            }

            // EVIDENCE FIRST.
            // This lands a couple of milliseconds after the question is sent,
            // long before the model produces a token. It is the whole latency
            // design: read the real slide while the sentence is still being
            // written. Kept short so the answer is not pushed off screen.
            AnimatedVisibility(vm.passages.isNotEmpty(), enter = fadeIn(tween(200))) {
                val top = vm.passages.firstOrNull() ?: return@AnimatedVisibility
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SectionLabel("Found in your slides")
                        Spacer(Modifier.width(Space.s))
                        Pill("${vm.evidenceShownMs} ms", Tone.Good)
                    }
                    Spacer(Modifier.height(Space.s))
                    Card(Modifier.padding(bottom = Space.m).clickable { onCite(top.chunk.page) }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Pill("slide ${top.chunk.page}", Tone.Mark)
                            Spacer(Modifier.weight(1f))
                            Text("open", style = MaterialTheme.typography.bodySmall, color = Paper.Blue)
                        }
                        Spacer(Modifier.height(Space.s))
                        HighlightedText(
                            text = top.chunk.text.take(190).trimEnd() +
                                    if (top.chunk.text.length > 190) "..." else "",
                            query = vm.askedQuestion
                        )
                    }
                }
            }

            // The written answer, streaming on top of the evidence.
            // NOT wrapped in AnimatedVisibility: the text changes on every token,
            // and re-running a fade against a constantly recomposing child left
            // the answer stuck part-way through its transition and effectively
            // invisible. The answer is the one thing that must never be subtle.
            if (vm.answer.isNotEmpty() || vm.streaming) {
                Column {
                    SectionLabel(if (vm.streaming) "Writing" else "Answer")
                    Spacer(Modifier.height(Space.s))
                    Card(accent = true) {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                vm.answer,
                                style = MaterialTheme.typography.bodyLarge,
                                color = Paper.Ink,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            if (vm.streaming) WritingCursor()
                        }
                        if (!vm.streaming && vm.answer.isNotEmpty()) {
                            Spacer(Modifier.height(Space.m))
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(Space.s),
                                verticalArrangement = Arrangement.spacedBy(Space.s)
                            ) {
                                Pill("evidence ${vm.evidenceShownMs} ms", Tone.Good)
                                Pill("first word %.1fs".format(vm.lastTtftMs / 1000.0))
                            }
                        }
                    }
                    // The obvious next thing to do with an answer you just read:
                    // revise it. Without this the reader has to walk to another
                    // tab and hunt for the same slide by hand.
                    if (!vm.streaming && vm.passages.isNotEmpty()) {
                        Spacer(Modifier.height(Space.s))
                        val page = vm.passages.first().chunk.page
                        TextButton(onClick = { vm.makeCardsFromPage(page) }) {
                            Text("Make flashcards from slide $page")
                        }
                    }
                    Spacer(Modifier.height(Space.m))
                }
            }

            if (vm.settings.showSources && vm.passages.size > 1) {
                SectionLabel("Other matches")
                Spacer(Modifier.height(Space.s))
                for (p in vm.passages.drop(1).take(2)) {
                    Card(Modifier.padding(bottom = Space.s).clickable { onCite(p.chunk.page) }) {
                        Pill("slide ${p.chunk.page}", Tone.Mark)
                        Spacer(Modifier.height(Space.s))
                        HighlightedText(
                            text = p.chunk.text.take(140).trimEnd() + "...",
                            query = vm.askedQuestion
                        )
                    }
                }
            }
            Spacer(Modifier.height(Space.l))
        }

        Surface(color = Paper.Card, shadowElevation = 8.dp) {
            Row(
                Modifier.fillMaxWidth().padding(Space.m),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = vm.question,
                    onValueChange = { vm.question = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Ask your slides") },
                    enabled = vm.modelReady && !vm.streaming,
                    maxLines = 3,
                    shape = MaterialTheme.shapes.large,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { vm.ask() })
                )
                Spacer(Modifier.width(Space.s))
                FilledIconButton(
                    onClick = { vm.ask() },
                    enabled = vm.modelReady && !vm.streaming && vm.question.isNotBlank()
                ) {
                    if (vm.streaming) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    else Text("Ask")
                }
            }
        }
    }
}

// ---- Source -----------------------------------------------------------------

@Composable
fun ReaderScreen(vm: AppState, onNavigateToSlides: () -> Unit) {
    val doc = vm.current
    if (doc == null) { EmptyState("Nothing loaded yet", "Open a deck in the Slides tab."); return }
    val pages = vm.pagesOf(doc)
    val page = vm.readerPage.coerceIn(1, maxOf(pages.size, 1))
    val pdfUri = vm.pdfUriOf(doc)

    // Sub-view toggle: Original PDF | Extracted Slides
    // Only show Original PDF tab if we have the URI (imported this session)
    var showOriginal by remember(doc.id) { mutableStateOf(false) }
    // If the URI is gone (deck reloaded from disk), fall back to slides view
    val canShowOriginal = pdfUri != null

    Column(Modifier.fillMaxSize()) {
        DeckTitleBar(title = doc.title, onNavigateToSlides = onNavigateToSlides)

        // Sub-tab row
        val ctx = LocalContext.current
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Space.s, vertical = Space.xs),
            horizontalArrangement = Arrangement.spacedBy(Space.s)
        ) {
            SubTabButton(
                label = "Extracted Slides",
                selected = !showOriginal,
                onClick = { showOriginal = false },
                modifier = Modifier.weight(1f)
            )
            SubTabButton(
                label = "Original PDF",
                selected = showOriginal && canShowOriginal,
                // Always tappable so we can show the toast; dimmed when unavailable
                enabled = canShowOriginal,
                onClick = {
                    if (canShowOriginal) {
                        showOriginal = true
                    } else {
                        android.widget.Toast.makeText(
                            ctx,
                            "Original PDF not available for this deck",
                            android.widget.Toast.LENGTH_LONG
                        ).show()
                    }
                },
                modifier = Modifier.weight(1f)
            )
        }

        if (showOriginal && canShowOriginal) {
            PdfPageViewer(uri = pdfUri!!, modifier = Modifier.weight(1f))
        } else {
            // Extracted slides view (original behaviour)
            ScreenHeader("Slide $page", tight = true)
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Space.l)
            ) {
                Appear(delayMs = 0) {
                    Card {
                        HighlightedText(
                            text = pages.getOrNull(page - 1)?.trim().orEmpty()
                                .ifEmpty { "This slide has no text that could be read." },
                            query = vm.askedQuestion
                        )
                    }
                }
                Spacer(Modifier.height(Space.l))
            }
            Surface(color = Paper.Card, shadowElevation = 8.dp) {
                Row(
                    Modifier.fillMaxWidth().padding(Space.m),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = { vm.readerPage = page - 1 }, enabled = page > 1) { Text("Back") }
                    Text(
                        "$page of ${pages.size}",
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = Mono),
                        color = Paper.InkSoft
                    )
                    TextButton(
                        onClick = { vm.readerPage = page + 1 },
                        enabled = page < pages.size
                    ) { Text("Next") }
                }
            }
        }
    }
}

/** Compact toggle button for sub-views inside a screen. */
@Composable
private fun SubTabButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (selected) Paper.Blue else Paper.Card,
        border = BorderStroke(1.dp, if (selected) Paper.Blue else Paper.Rule),
        // Always intercept the click so onClick() can show a toast when disabled
        modifier = modifier.clickable(onClick = onClick)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) androidx.compose.ui.graphics.Color.White
                    else if (enabled) Paper.InkSoft else Paper.InkFaint,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 9.dp, horizontal = Space.s)
        )
    }
}

/**
 * Renders the original PDF file page-by-page using Android's built-in PdfRenderer.
 * Pages are rendered as bitmaps in a scrollable lazy column.
 * PdfRenderer requires a seekable FileDescriptor, so we open via the ContentResolver.
 */
@Composable
private fun PdfPageViewer(uri: Uri, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    var pageCount by remember { mutableStateOf(0) }
    var renderer by remember { mutableStateOf<PdfRenderer?>(null) }

    DisposableEffect(uri) {
        val fd = ctx.contentResolver.openFileDescriptor(uri, "r")
        val r = fd?.let { PdfRenderer(it) }
        renderer = r
        pageCount = r?.pageCount ?: 0
        onDispose { r?.close(); fd?.close() }
    }

    if (pageCount == 0) {
        Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text(
                "Original PDF not available.\nImport the file again to view it here.",
                style = MaterialTheme.typography.bodySmall,
                color = Paper.InkSoft,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(Space.xl)
            )
        }
        return
    }

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = Space.s, vertical = Space.s),
        verticalArrangement = Arrangement.spacedBy(Space.s),
        state = rememberLazyListState()
    ) {
        items(pageCount) { index ->
            PdfPageItem(renderer = renderer, index = index)
        }
    }
}

@Composable
private fun PdfPageItem(renderer: PdfRenderer?, index: Int) {
    var bitmap by remember(index) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(renderer, index) {
        if (renderer == null) return@LaunchedEffect
        bitmap = withContext(Dispatchers.Default) {
            runCatching {
                val page = renderer.openPage(index)
                // Scale to 1080px wide for readability; maintain aspect ratio
                val scale = 1080f / page.width
                val bmp = Bitmap.createBitmap(
                    1080, (page.height * scale).toInt(), Bitmap.Config.ARGB_8888
                )
                bmp.eraseColor(android.graphics.Color.WHITE)
                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()
                bmp
            }.getOrNull()
        }
    }
    val bmp = bitmap
    if (bmp != null) {
        Card {
            androidx.compose.foundation.Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = "Page ${index + 1}",
                contentScale = ContentScale.FillWidth,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                "Page ${index + 1} of ${renderer?.pageCount ?: 0}",
                style = MaterialTheme.typography.labelSmall,
                color = Paper.InkFaint,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = Space.xs)
            )
        }
    } else {
        Card {
            Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp, color = Paper.Blue)
            }
        }
    }
}

/**
 * First-run screen when there is no model on disk.
 *
 * This used to be a one-line status reading "Model missing. Push it to
 * /sdcard/Android/data/dev.omnitalk/files" — an instruction that assumes adb, a
 * computer, and knowing what adb is. Anyone evaluating the app rather than
 * building it was simply stuck at a dead end.
 *
 * It stays a manual step on purpose: fetching the weights in-app would need an
 * INTERNET permission, and this app's strongest claim is that it holds none.
 */
@Composable
fun SetupScreen(vm: AppState, onPickModel: () -> Unit) {
    val progress = vm.modelCopyProgress
    Box(Modifier.fillMaxSize().background(Paper.Stock)) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Space.l),
            verticalArrangement = Arrangement.Center
        ) {
            BrandHeader("One thing to set up")

            Spacer(Modifier.height(Space.l))

            if (progress != null) {
                Card(accent = true) {
                    Text("Copying the model", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(Space.s))
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth(),
                        color = Paper.Blue, trackColor = Paper.Rule
                    )
                    Spacer(Modifier.height(Space.s))
                    Text(
                        "%.0f%% — this takes a few seconds and happens once.".format(progress * 100),
                        style = MaterialTheme.typography.bodySmall, color = Paper.InkSoft
                    )
                }
                return@Column
            }

            Card {
                Text(
                    "Cram needs a language model file to run. It is about 770 MB and " +
                    "stays on this phone.",
                    style = MaterialTheme.typography.bodyMedium, color = Paper.Ink
                )
                Spacer(Modifier.height(Space.m))
                Step(1, "Download the model", "Llama-3.2-1B-Instruct-Q4_0.gguf from Hugging Face, " +
                     "in any browser. It lands in your Downloads folder.")
                Step(2, "Tap the button below", "Pick that file. Cram copies it into its own " +
                     "storage, so you can delete the download afterwards.")
                Step(3, "That is all", "It never needs the internet again — and it has no " +
                     "permission to use it.")
                Spacer(Modifier.height(Space.m))
                Button(onClick = onPickModel, modifier = Modifier.fillMaxWidth()) {
                    Text("Choose the model file")
                }
            }

            Spacer(Modifier.height(Space.m))

            Card {
                SectionLabel("Why not just download it for you")
                Spacer(Modifier.height(Space.s))
                Text(
                    "Doing that would mean asking Android for internet access. This app " +
                    "declares no internet permission at all, which is what makes it unable " +
                    "to send your documents anywhere. Check it yourself under App info → " +
                    "Permissions. One extra tap seemed a fair price.",
                    style = MaterialTheme.typography.bodySmall, color = Paper.InkSoft
                )
            }
        }
    }
}

@Composable
private fun Step(n: Int, title: String, body: String) {
    Row(Modifier.padding(bottom = Space.m)) {
        Surface(shape = RoundedCornerShape(50), color = Paper.Blue, modifier = Modifier.size(24.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    "$n", style = MaterialTheme.typography.labelSmall,
                    color = androidx.compose.ui.graphics.Color.White
                )
            }
        }
        Spacer(Modifier.width(Space.s))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = Paper.Ink)
            Text(body, style = MaterialTheme.typography.bodySmall, color = Paper.InkSoft)
        }
    }
}
