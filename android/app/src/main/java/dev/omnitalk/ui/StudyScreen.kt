package dev.omnitalk.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
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
import androidx.compose.ui.unit.dp
import dev.omnitalk.AppState
import dev.omnitalk.rag.StudyKind

/**
 * Flashcards and quiz questions, generated from the reader's own slides.
 *
 * The card back is hidden until tapped. That is not decoration: a flashcard you
 * can already read is not a flashcard, and hiding the answer is the entire
 * mechanism by which the format works.
 */
@Composable
fun StudyScreen(vm: AppState, onCite: (Int) -> Unit) {
    val doc = vm.current
    if (doc == null) {
        EmptyState("Nothing loaded yet", "Open a deck in the Slides tab to make flashcards from it.")
        return
    }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Study", doc.title)

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Space.l)) {

            // Kind picker
            Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                for (k in StudyKind.entries) {
                    val on = vm.studyKind == k
                    Surface(
                        shape = MaterialTheme.shapes.large,
                        color = if (on) Paper.Blue else Paper.Card,
                        border = androidx.compose.foundation.BorderStroke(1.dp, if (on) Paper.Blue else Paper.Rule),
                        modifier = Modifier.weight(1f).clickable { vm.studyKind = k }
                    ) {
                        Text(
                            k.label,
                            style = MaterialTheme.typography.titleSmall,
                            color = if (on) Paper.Card else Paper.InkSoft,
                            modifier = Modifier.padding(vertical = 11.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }
            Spacer(Modifier.height(Space.m))

            OutlinedTextField(
                value = vm.studyTopic,
                onValueChange = { vm.studyTopic = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Topic") },
                placeholder = { Text("e.g. deadlock detection, or leave blank for the whole deck") },
                enabled = vm.modelReady && !vm.studying,
                maxLines = 2,
                shape = MaterialTheme.shapes.large,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { vm.generateStudy() })
            )
            Spacer(Modifier.height(Space.s))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("How many", style = MaterialTheme.typography.bodySmall, color = Paper.InkSoft)
                Spacer(Modifier.width(Space.s))
                for (n in listOf(3, 5, 8)) {
                    val on = vm.studyCount == n
                    Box(Modifier.padding(end = Space.s).clickable { vm.studyCount = n }) {
                        Pill("$n", if (on) Tone.Good else Tone.Neutral)
                    }
                }
                Spacer(Modifier.weight(1f))
                Button(
                    onClick = { vm.generateStudy() },
                    enabled = vm.modelReady && !vm.studying
                ) {
                    if (vm.studying) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = Paper.Card)
                        Spacer(Modifier.width(Space.s))
                        Text("Writing")
                    } else Text(vm.studyKind.verb)
                }
            }

            Spacer(Modifier.height(Space.l))

            if (vm.cards.isEmpty() && !vm.studying) {
                Card {
                    Text(
                        "Pick a topic and Cram writes revision cards from your slides. " +
                        "Every card is built from text that is actually in the deck, and each " +
                        "one says which slide it came from so you can check it.",
                        style = MaterialTheme.typography.bodyMedium, color = Paper.InkSoft
                    )
                }
            }

            vm.cards.forEachIndexed { i, c ->
                AnimatedVisibility(true, enter = fadeIn(tween(220))) {
                    Card(Modifier.padding(bottom = Space.s).clickable { vm.toggleReveal(i) }) {
                        Row(verticalAlignment = Alignment.Top) {
                            Text(
                                "${i + 1}",
                                style = MaterialTheme.typography.labelSmall,
                                color = Paper.InkFaint,
                                modifier = Modifier.padding(end = Space.s, top = 3.dp)
                            )
                            Text(
                                c.front,
                                style = MaterialTheme.typography.titleSmall,
                                color = Paper.Ink,
                                modifier = Modifier.weight(1f)
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
                            Text(
                                "Tap to reveal",
                                style = MaterialTheme.typography.bodySmall,
                                color = Paper.Blue
                            )
                        }
                    }
                }
            }

            if (vm.cards.isNotEmpty() && !vm.studying) {
                Spacer(Modifier.height(Space.s))
                Pill("${vm.cards.size} cards in ${"%.1f".format(vm.studyMs / 1000.0)}s", Tone.Neutral)
            }
            Spacer(Modifier.height(Space.l))
        }
    }
}
