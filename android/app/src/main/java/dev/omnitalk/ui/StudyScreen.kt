package dev.omnitalk.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.omnitalk.AppState

/**
 * Flashcards from the reader's own slides, and a practice loop over them.
 *
 * There is one generator, not two. An earlier version offered "Flashcards" and
 * "Quiz" as separate buttons, but both produced question-and-answer pairs from
 * the same passages with near-identical prompts - two controls doing one thing,
 * which is worse than one. What was missing was not a second generator but
 * somewhere to actually use the cards, so Quiz became Practice.
 */
@Composable
fun StudyScreen(vm: AppState, onCite: (Int) -> Unit) {
    val doc = vm.current
    if (doc == null) {
        EmptyState("Nothing loaded yet", "Open a deck in the Slides tab to make flashcards from it.")
        return
    }
    if (vm.cards.isNotEmpty() && vm.practiceIndex < vm.cards.size && vm.practiceStarted) {
        PracticeView(vm, onCite); return
    }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Flashcards", doc.title)

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Space.l)) {

            SectionLabel("Make cards from")
            Spacer(Modifier.height(Space.s))
            Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                for (m in AppState.ScopeMode.entries) {
                    val on = vm.scopeMode == m
                    Surface(
                        shape = MaterialTheme.shapes.large,
                        color = if (on) Paper.Blue else Paper.Card,
                        border = BorderStroke(1.dp, if (on) Paper.Blue else Paper.Rule),
                        modifier = Modifier.weight(1f).clickable { vm.scopeMode = m }
                    ) {
                        Text(
                            when (m) {
                                AppState.ScopeMode.Deck -> "Whole deck"
                                AppState.ScopeMode.Pages -> "Slides"
                                AppState.ScopeMode.Topic -> "Topic"
                            },
                            style = MaterialTheme.typography.titleSmall,
                            color = if (on) Paper.Card else Paper.InkSoft,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 11.dp)
                        )
                    }
                }
            }
            Spacer(Modifier.height(Space.m))

            // A page range is an exact instruction where a topic is a guess, so
            // it is offered first-class rather than buried.
            when (vm.scopeMode) {
                AppState.ScopeMode.Pages -> Row(verticalAlignment = Alignment.CenterVertically) {
                    NumberBox("From", vm.pageFrom, doc.pageCount) { vm.pageFrom = it }
                    Spacer(Modifier.width(Space.m))
                    NumberBox("To", vm.pageTo, doc.pageCount) { vm.pageTo = it }
                    Spacer(Modifier.width(Space.m))
                    Text(
                        "of ${doc.pageCount}",
                        style = MaterialTheme.typography.bodySmall, color = Paper.InkSoft
                    )
                }
                // Pick from the deck's own headings rather than typing a guess.
                // A typed topic is a search that can miss; a heading is exact.
                AppState.ScopeMode.Topic -> TopicPicker(vm)
                AppState.ScopeMode.Deck -> Text(
                    "Cards will be spread across all ${doc.pageCount} slides.",
                    style = MaterialTheme.typography.bodySmall, color = Paper.InkSoft
                )
            }

            Spacer(Modifier.height(Space.m))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("How many", style = MaterialTheme.typography.bodySmall, color = Paper.InkSoft)
                Spacer(Modifier.width(Space.s))
                for (n in listOf(3, 5, 8)) {
                    Box(Modifier.padding(end = Space.s).clickable { vm.studyCount = n }) {
                        Pill("$n", if (vm.studyCount == n) Tone.Good else Tone.Neutral)
                    }
                }
                Spacer(Modifier.weight(1f))
                Button(onClick = { vm.generateStudy() }, enabled = vm.modelReady && !vm.studying) {
                    if (vm.studying) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = Paper.Card)
                        Spacer(Modifier.width(Space.s))
                        Text("Writing")
                    } else Text("Make cards")
                }
            }

            Spacer(Modifier.height(Space.l))

            if (vm.cards.isEmpty() && !vm.studying) {
                Card {
                    Text(
                        "Cram writes revision cards from your slides. Every card comes from " +
                        "text that is actually in the deck, and says which slide it came from " +
                        "so you can check it.",
                        style = MaterialTheme.typography.bodyMedium, color = Paper.InkSoft
                    )
                }
            }

            if (vm.cards.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionLabel("${vm.cards.size} cards from ${vm.cardsScope}")
                    Spacer(Modifier.weight(1f))
                    if (!vm.studying) {
                        Button(onClick = { vm.startPractice() }) { Text("Practise") }
                    }
                }
                Spacer(Modifier.height(Space.s))
            }

            vm.cards.forEachIndexed { i, c ->
                AnimatedVisibility(true, enter = fadeIn(tween(220))) {
                    Card(Modifier.padding(bottom = Space.s).clickable { vm.toggleReveal(i) }) {
                        Row(verticalAlignment = Alignment.Top) {
                            Text(
                                "${i + 1}", style = MaterialTheme.typography.labelSmall,
                                color = Paper.InkFaint, modifier = Modifier.padding(end = Space.s, top = 3.dp)
                            )
                            Text(
                                c.front, style = MaterialTheme.typography.titleSmall,
                                color = Paper.Ink, modifier = Modifier.weight(1f)
                            )
                        }
                        Spacer(Modifier.height(Space.s))
                        if (vm.revealed.contains(i)) {
                            Text(c.back, style = MaterialTheme.typography.bodyMedium, color = Paper.Ink)
                            if (c.page > 0) {
                                Spacer(Modifier.height(Space.s))
                                Box(Modifier.clickable { onCite(c.page) }) {
                                    Pill("from slide ${c.page}", Tone.Mark)
                                }
                            }
                        } else {
                            Text("Tap to reveal", style = MaterialTheme.typography.bodySmall, color = Paper.Blue)
                        }
                    }
                }
            }
            Spacer(Modifier.height(Space.l))
        }
    }
}

/** One card at a time, answer hidden, self-graded. The actual studying. */
@Composable
private fun PracticeView(vm: AppState, onCite: (Int) -> Unit) {
    val card = vm.cards.getOrNull(vm.practiceIndex) ?: return
    Column(Modifier.fillMaxSize().padding(Space.l)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${vm.practiceIndex + 1} of ${vm.cards.size}",
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = Mono), color = Paper.InkSoft
            )
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { vm.stopPractice() }) { Text("Done") }
        }
        LinearProgressIndicator(
            progress = { (vm.practiceIndex + 1f) / vm.cards.size },
            modifier = Modifier.fillMaxWidth(),
            color = Paper.Blue, trackColor = Paper.Rule
        )

        Column(
            Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                card.front,
                style = MaterialTheme.typography.headlineSmall,
                color = Paper.Ink, textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(Space.l))
            if (vm.practiceRevealed) {
                Card(accent = true) {
                    Text(card.back, style = MaterialTheme.typography.bodyLarge, color = Paper.Ink)
                    if (card.page > 0) {
                        Spacer(Modifier.height(Space.s))
                        Box(Modifier.clickable { onCite(card.page) }) {
                            Pill("from slide ${card.page}", Tone.Mark)
                        }
                    }
                }
            } else {
                OutlinedButton(onClick = { vm.practiceRevealed = true }) { Text("Show answer") }
            }
        }

        if (vm.practiceRevealed) {
            Row(horizontalArrangement = Arrangement.spacedBy(Space.m)) {
                OutlinedButton(
                    onClick = { vm.practiceNext(false) }, modifier = Modifier.weight(1f)
                ) { Text("Review again") }
                Button(
                    onClick = { vm.practiceNext(true) }, modifier = Modifier.weight(1f)
                ) { Text("Got it") }
            }
        }
    }
}

/**
 * Pick topics from the deck.
 *
 * Headings come free with indexing and are used by default. When a deck has no
 * real titles the list degenerates to "Slide 1, Slide 2, ...", so the one-time
 * model pass is offered right there, with an honest note about what it costs.
 * It is never run automatically: a minute of the reader's time should be their
 * decision.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TopicPicker(vm: AppState) {
    val topics = vm.topics()
    val pass = vm.topicPass

    Column {
        if (pass != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = Paper.Blue)
                Spacer(Modifier.width(Space.s))
                Text(
                    "Reading the deck, batch ${pass.first} of ${pass.second}",
                    style = MaterialTheme.typography.bodySmall, color = Paper.InkSoft
                )
            }
            Spacer(Modifier.height(Space.s))
        }

        if (topics.isEmpty()) {
            Text(
                "No topics yet.", style = MaterialTheme.typography.bodySmall, color = Paper.InkSoft
            )
        } else {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Space.s),
                verticalArrangement = Arrangement.spacedBy(Space.s)
            ) {
                for (t in topics) {
                    val on = vm.selectedTopics.contains(t.title)
                    Surface(
                        shape = MaterialTheme.shapes.large,
                        color = if (on) Paper.HighlighterSoft else Paper.Card,
                        border = BorderStroke(1.dp, if (on) Paper.Highlighter else Paper.Rule),
                        modifier = Modifier.clickable { vm.toggleTopic(t.title) }
                    ) {
                        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                            Text(
                                t.title,
                                style = MaterialTheme.typography.titleSmall,
                                color = Paper.Ink
                            )
                            Text(
                                t.pages,
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = Mono),
                                color = Paper.InkFaint
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(Space.s))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (vm.usingGeneratedTopics()) {
                Pill("named by Cram", Tone.Good)
            } else if (vm.headingsWeak()) {
                Pill("weak headings", Tone.Warn)
            } else {
                Pill("from slide titles", Tone.Neutral)
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { vm.generateTopics() }, enabled = vm.modelReady && pass == null) {
                Text(if (vm.usingGeneratedTopics()) "Read again" else "Find topics")
            }
        }
        if (!vm.usingGeneratedTopics()) {
            Text(
                if (vm.headingsWeak())
                    "This deck has few usable slide titles. Reading it once takes about a " +
                    "minute and gives much better topics - it is saved and never repeated."
                else
                    "Topics come from the slide titles. If they are not quite right, Cram can " +
                    "read the deck once and name them itself.",
                style = MaterialTheme.typography.bodySmall, color = Paper.InkSoft
            )
        }
    }
}

@Composable
private fun NumberBox(label: String, value: Int, max: Int, onChange: (Int) -> Unit) {
    OutlinedTextField(
        value = value.toString(),
        onValueChange = { s -> s.filter { it.isDigit() }.toIntOrNull()?.let { onChange(it.coerceIn(1, max)) } },
        label = { Text(label) },
        singleLine = true,
        modifier = Modifier.width(104.dp),
        shape = MaterialTheme.shapes.large,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
    )
}
