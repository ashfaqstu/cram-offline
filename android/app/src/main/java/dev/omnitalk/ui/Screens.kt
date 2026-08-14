package dev.omnitalk.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import dev.omnitalk.AppState
import dev.omnitalk.rag.Document

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
            Text(
                d.title, style = MaterialTheme.typography.titleMedium,
                maxLines = 2, color = Paper.Ink, modifier = Modifier.weight(1f)
            )
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
            if (selected) Pill("open", Tone.Good)
        }
    }
}

// ---- Ask --------------------------------------------------------------------

private val SUGGESTIONS = listOf(
    "When is assignment 3 due?",
    "What are the four Coffman conditions?",
    "What is the time complexity of the Banker's algorithm?"
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AskScreen(vm: AppState, onCite: (Int) -> Unit) {
    val doc = vm.current
    if (doc == null) {
        EmptyState("Nothing loaded yet", "Open a deck in the Slides tab, then ask it anything.")
        return
    }
    val scroll = rememberScrollState()

    // Follow the answer as it writes itself, the way a page scrolls under a pen.
    LaunchedEffect(vm.answer) { if (vm.streaming) scroll.animateScrollTo(scroll.maxValue) }

    Column(Modifier.fillMaxSize()) {
        // The question is pinned, not scrolled.
        // The answer auto-scrolls as it writes, which used to carry the question
        // off the top of the screen — leaving a page of answers with no visible
        // sign of what was asked.
        if (vm.askedQuestion.isEmpty()) {
            ScreenHeader("Ask", doc.title)
        } else {
            Column(Modifier.fillMaxWidth().padding(start = Space.l, end = Space.l, top = Space.l, bottom = Space.s)) {
                Text(doc.title, style = MaterialTheme.typography.bodySmall, color = Paper.InkFaint)
                Spacer(Modifier.height(3.dp))
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
                SUGGESTIONS.forEachIndexed { i, s ->
                    Appear(delayMs = 80 * i) {
                        Card(Modifier.padding(bottom = Space.s).clickable {
                            vm.question = s; vm.ask()
                        }) { Text(s, style = MaterialTheme.typography.bodyMedium, color = Paper.Ink) }
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
fun ReaderScreen(vm: AppState) {
    val doc = vm.current
    if (doc == null) { EmptyState("Nothing loaded yet", "Open a deck in the Slides tab."); return }
    val pages = vm.pagesOf(doc)
    val page = vm.readerPage.coerceIn(1, maxOf(pages.size, 1))

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Slide $page", doc.title)
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
