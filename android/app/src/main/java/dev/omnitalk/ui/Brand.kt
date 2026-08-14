package dev.omnitalk.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.sin

/**
 * The mark: a highlighter stroke swept across two lines of text.
 *
 * That is literally what the app does — find the line that matters on a slide and
 * mark it — so the logo is the product rather than an abstract glyph. Drawn in
 * Canvas rather than shipped as a raster so it stays crisp at any size and can be
 * animated on the landing screen.
 */
@Composable
fun SiftMark(size: androidx.compose.ui.unit.Dp = 44.dp, sweep: Float = 1f) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val line = h * 0.085f

        // two lines of "text"
        drawLine(
            Paper.InkFaint, Offset(w * 0.12f, h * 0.34f), Offset(w * 0.88f, h * 0.34f),
            strokeWidth = line, cap = StrokeCap.Round
        )
        drawLine(
            Paper.InkFaint, Offset(w * 0.12f, h * 0.76f), Offset(w * 0.62f, h * 0.76f),
            strokeWidth = line, cap = StrokeCap.Round
        )
        // the highlighter sweep across the middle line, drawn under the ink
        if (sweep > 0f) {
            drawLine(
                Paper.Highlighter,
                Offset(w * 0.08f, h * 0.55f),
                Offset(w * (0.08f + 0.84f * sweep), h * 0.55f),
                strokeWidth = h * 0.30f, cap = StrokeCap.Round
            )
        }
        drawLine(
            Paper.Ink, Offset(w * 0.12f, h * 0.55f), Offset(w * 0.80f, h * 0.55f),
            strokeWidth = line * 1.2f, cap = StrokeCap.Round
        )
    }
}

/** Wordmark used at the top of the landing screen. */
@Composable
fun BrandHeader(subtitle: String? = null) {
    val t = rememberInfiniteTransition(label = "brand")
    // A slow, occasional sweep — the highlighter re-marking the line. Long pause
    // built into the easing so it reads as punctuation, not as a loading spinner.
    val sweep by t.animateFloat(
        0f, 1f,
        infiniteRepeatable(
            tween(5200, easing = CubicBezierEasing(0.9f, 0f, 0.1f, 1f)),
            RepeatMode.Restart
        ),
        label = "sweep"
    )

    Row(
        Modifier.fillMaxWidth().padding(start = Space.l, end = Space.l, top = Space.l, bottom = Space.s),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SiftMark(sweep = sweep)
        Spacer(Modifier.width(Space.m))
        Column {
            Text(
                "Cram",
                style = MaterialTheme.typography.displaySmall,
                color = Paper.Ink
            )
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Paper.InkSoft)
            }
        }
    }
}

/**
 * Ruled-paper background with a very slow drift.
 *
 * Faint enough to be furniture rather than decoration — it gives the page depth
 * without competing with the text. Can be switched off in Settings, because on a
 * long study session some people will want it gone, and because an animation
 * nobody can stop is a bug.
 */
@Composable
fun PaperBackground(enabled: Boolean, modifier: Modifier = Modifier) {
    if (!enabled) return
    val t = rememberInfiniteTransition(label = "paper")
    val drift by t.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(26_000, easing = LinearEasing), RepeatMode.Reverse),
        label = "drift"
    )
    Canvas(modifier.fillMaxSize()) { drawRuledPaper(drift) }
}

private fun DrawScope.drawRuledPaper(drift: Float) {
    val spacing = 34.dp.toPx()
    val offset = drift * spacing
    var y = -spacing + offset
    while (y < size.height + spacing) {
        drawLine(
            Paper.Rule.copy(alpha = 0.55f),
            Offset(0f, y), Offset(size.width, y),
            strokeWidth = 1f
        )
        y += spacing
    }
    // the red margin rule down the left, as on a school exercise book
    val x = size.width * 0.085f
    drawLine(
        Paper.RedPen.copy(alpha = 0.10f),
        Offset(x, 0f), Offset(x, size.height),
        strokeWidth = 1.5f
    )
    // a soft wash that breathes, so the page is never completely static
    val wash = 0.03f + 0.02f * sin(drift * 3.14159f).toFloat()
    drawCircle(
        Paper.Highlighter.copy(alpha = wash),
        radius = size.width * 0.75f,
        center = Offset(size.width * (0.2f + drift * 0.15f), size.height * 0.15f)
    )
}

/**
 * Bottom-bar glyphs, drawn rather than imported.
 *
 * Four tiny marks in the same visual language as the logo: a stack of sheets, a
 * question, an open page, a dial. Icon fonts would have meant another dependency
 * and a look borrowed from every other app.
 */
@Composable
fun TabIcon(index: Int, selected: Boolean) {
    val c = if (selected) Paper.Blue else Paper.InkFaint
    Canvas(Modifier.size(22.dp)) {
        val w = size.width; val h = size.height; val s = h * 0.09f
        when (index) {
            0 -> {  // stacked sheets
                drawRect(Color.Transparent)
                for (i in 0..2) {
                    val y = h * (0.24f + i * 0.24f)
                    drawLine(c, Offset(w * 0.16f, y), Offset(w * 0.84f, y), s, StrokeCap.Round)
                }
            }
            1 -> {  // a question: hook and dot
                drawArc(
                    c, -190f, 220f, false,
                    topLeft = Offset(w * 0.26f, h * 0.12f),
                    size = androidx.compose.ui.geometry.Size(w * 0.46f, h * 0.42f),
                    style = Stroke(width = s, cap = StrokeCap.Round)
                )
                drawLine(c, Offset(w * 0.5f, h * 0.52f), Offset(w * 0.5f, h * 0.66f), s, StrokeCap.Round)
                drawCircle(c, radius = s * 0.75f, center = Offset(w * 0.5f, h * 0.84f))
            }
            2 -> {  // an open page with a marked line
                drawLine(c, Offset(w * 0.18f, h * 0.2f), Offset(w * 0.82f, h * 0.2f), s, StrokeCap.Round)
                drawLine(Paper.Highlighter, Offset(w * 0.18f, h * 0.5f), Offset(w * 0.7f, h * 0.5f), s * 2.4f, StrokeCap.Round)
                drawLine(c, Offset(w * 0.18f, h * 0.5f), Offset(w * 0.7f, h * 0.5f), s, StrokeCap.Round)
                drawLine(c, Offset(w * 0.18f, h * 0.8f), Offset(w * 0.6f, h * 0.8f), s, StrokeCap.Round)
            }
            else -> { // a dial
                drawCircle(c, radius = w * 0.30f, center = Offset(w / 2, h / 2), style = Stroke(s))
                drawLine(c, Offset(w / 2, h * 0.5f), Offset(w * 0.72f, h * 0.32f), s, StrokeCap.Round)
            }
        }
    }
}
