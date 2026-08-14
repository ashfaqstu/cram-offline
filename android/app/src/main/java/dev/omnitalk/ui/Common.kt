package dev.omnitalk.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp

/** Shared building blocks, so every screen reads as the same app. */

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = Paper.InkFaint,
        modifier = modifier
    )
}

/** A sheet of paper. Slight shadow so it sits *on* the page rather than in it. */
@Composable
fun Card(
    modifier: Modifier = Modifier,
    accent: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = if (accent) Paper.BlueSoft else Paper.Card,
        border = BorderStroke(1.dp, if (accent) Paper.Blue.copy(alpha = .25f) else Paper.Rule),
        shadowElevation = if (accent) 0.dp else 1.dp
    ) {
        Column(Modifier.padding(Space.m), content = content)
    }
}

/** Slides up and fades in. Used for anything that arrives rather than exists. */
@Composable
fun Appear(visible: Boolean = true, delayMs: Int = 0, content: @Composable () -> Unit) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(visible) { if (visible) { delay(delayMs.toLong()); shown = true } }
    AnimatedVisibility(
        visible = shown && visible,
        enter = fadeIn(tween(260)) + slideInVertically(tween(300)) { it / 6 },
        exit = fadeOut(tween(140))
    ) { content() }
}

private suspend fun delay(ms: Long) = kotlinx.coroutines.delay(ms)

@Composable
fun Stat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        SectionLabel(label)
        Spacer(Modifier.height(3.dp))
        Text(
            value,
            style = MaterialTheme.typography.titleMedium.copy(fontFamily = Mono),
            color = Paper.Ink
        )
    }
}

enum class Tone { Good, Warn, Neutral, Mark }

@Composable
fun Pill(text: String, tone: Tone = Tone.Neutral) {
    val (bg, fg) = when (tone) {
        Tone.Good -> Paper.BlueSoft to Paper.Blue
        Tone.Warn -> Paper.RedPenSoft to Paper.RedPen
        Tone.Mark -> Paper.HighlighterSoft to Color(0xFF7A5B00)
        Tone.Neutral -> Color(0xFFF2ECE0) to Paper.InkSoft
    }
    Box(Modifier.background(bg, RoundedCornerShape(7.dp)).padding(horizontal = 9.dp, vertical = 4.dp)) {
        Text(text, style = MaterialTheme.typography.labelSmall.copy(fontFamily = Mono), color = fg)
    }
}

/**
 * Passage text with the query's own words marked in highlighter.
 *
 * Rendered from retrieval output alone, so it appears milliseconds after the
 * question is sent — the reader can see *why* this slide matched before any text
 * has been generated. A real marker behind the word reads instantly; coloured
 * text does not.
 */
@Composable
fun HighlightedText(text: String, query: String, modifier: Modifier = Modifier) {
    // Same tokenizer as the index: a separate rule here once highlighted "the"
    // everywhere while contributing nothing to the match.
    val terms = remember(query) { dev.omnitalk.rag.Chunk.tokenize(query).toSet() }
    val annotated = remember(text, terms) {
        buildAnnotatedString {
            var i = 0
            for (m in Regex("\\p{L}[\\p{L}\\p{N}]*").findAll(text)) {
                if (m.range.first > i) append(text.substring(i, m.range.first))
                if (m.value.lowercase() in terms) {
                    withStyle(
                        SpanStyle(background = Paper.Highlighter, color = Paper.Ink,
                            fontWeight = FontWeight.Medium)
                    ) { append(m.value) }
                } else append(m.value)
                i = m.range.last + 1
            }
            if (i < text.length) append(text.substring(i))
        }
    }
    Text(annotated, style = MaterialTheme.typography.bodySmall, color = Paper.InkSoft, modifier = modifier)
}

/** A blinking pen nib, shown while the model is still writing. */
@Composable
fun WritingCursor() {
    val t = rememberInfiniteTransition(label = "cursor")
    val a by t.animateFloat(
        1f, 0.15f,
        infiniteRepeatable(tween(620, easing = LinearEasing), RepeatMode.Reverse),
        label = "blink"
    )
    Box(
        Modifier.padding(start = 3.dp).size(width = 9.dp, height = 19.dp)
            .alpha(a).background(Paper.Blue, RoundedCornerShape(2.dp))
    )
}

@Composable
fun EmptyState(title: String, body: String, action: (@Composable () -> Unit)? = null) {
    Column(
        Modifier.fillMaxSize().padding(Space.xl),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Appear {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                Spacer(Modifier.height(Space.s))
                Text(
                    body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Paper.InkSoft,
                    textAlign = TextAlign.Center
                )
                if (action != null) { Spacer(Modifier.height(Space.l)); action() }
            }
        }
    }
}

@Composable
/**
 * @param tight for screens that sit under a [DeckTitleBar]. Without it the
 * default top padding stacks on the bar's and the screen title drifts so far
 * down it reads as belonging to the content rather than to the screen.
 */
fun ScreenHeader(title: String, subtitle: String? = null, tight: Boolean = false) {
    Column(Modifier.fillMaxWidth().padding(
        start = Space.l, end = Space.l,
        top = if (tight) Space.s else Space.l,
        bottom = if (tight) Space.s else Space.m
    )) {
        Text(title, style = MaterialTheme.typography.headlineSmall, color = Paper.Ink)
        if (subtitle != null) {
            Spacer(Modifier.height(2.dp))
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Paper.InkSoft, maxLines = 2)
        }
    }
}

/**
 * A clickable deck-title box, styled like a button with margins.
 *
 * Sits pinned above the scrollable content on Ask, Study, and Source screens.
 * Looks like the "Open another PDF" button — a contained rounded rectangle,
 * not a full-bleed bar — with proper padding on all sides. Tapping navigates
 * back to the Slides menu.
 */
@Composable
fun DeckTitleBar(title: String, onNavigateToSlides: () -> Unit) {
    // Wider and squarer than a card: this is a control, not a container, and it
    // should read as the same family as the "Open another PDF" button. The bottom
    // margin is tight because the screen title sits directly under it — the two
    // are one header block, not two stacked ones.
    Box(
        Modifier
            .fillMaxWidth()
            .padding(start = Space.xs, end = Space.xs, top = Space.s, bottom = 2.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = Paper.BlueSoft,
            border = BorderStroke(1.dp, Paper.Blue.copy(alpha = 0.30f)),
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onNavigateToSlides)
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Space.m, vertical = Space.m),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    "‹",
                    style = MaterialTheme.typography.titleLarge,
                    color = Paper.Blue,
                    modifier = Modifier.padding(end = Space.s)
                )
                Text(
                    title,
                    style = MaterialTheme.typography.titleLarge,
                    color = Paper.Blue,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

