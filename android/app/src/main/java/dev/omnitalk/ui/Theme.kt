package dev.omnitalk.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The study-desk palette.
 *
 * This app is used at a desk, at night, over lecture slides, so it is dressed as
 * paper and marking pens rather than as a terminal: warm off-white stock, ink-blue
 * text, and a real highlighter yellow behind matched words — which is exactly what
 * a student does to a slide with a marker.
 *
 * Deliberately single-theme. A neutral light/dark pair would have been the safe
 * choice, but the whole point is that it looks like *something*.
 */
object Paper {
    val Stock       = Color(0xFFFAF6EE)   // warm page
    val Card        = Color(0xFFFFFDF9)   // a sheet on top of the page
    val Rule        = Color(0xFFE7DFD0)   // faint ruled line
    val Ink         = Color(0xFF23272E)   // pen on paper, not pure black
    val InkSoft     = Color(0xFF6B7280)   // pencil
    val InkFaint    = Color(0xFF9AA1AB)

    val Blue        = Color(0xFF2F5C8F)   // ballpoint — the accent
    val BlueSoft    = Color(0xFFE3ECF6)
    val Highlighter = Color(0xFFFFE066)   // the marker
    val HighlighterSoft = Color(0xFFFFF3C4)
    val GreenPen    = Color(0xFF3F7D58)
    val RedPen      = Color(0xFFB4472A)
    val RedPenSoft  = Color(0xFFF7E3DC)
}

private val Scheme = lightColorScheme(
    primary = Paper.Blue,
    onPrimary = Color.White,
    primaryContainer = Paper.BlueSoft,
    onPrimaryContainer = Paper.Blue,
    secondary = Paper.GreenPen,
    background = Paper.Stock,
    onBackground = Paper.Ink,
    surface = Paper.Card,
    onSurface = Paper.Ink,
    surfaceVariant = Color(0xFFF2ECE0),
    onSurfaceVariant = Paper.InkSoft,
    outline = Paper.Rule,
    outlineVariant = Paper.Rule,
    error = Paper.RedPen,
    onError = Color.White,
    errorContainer = Paper.RedPenSoft,
    onErrorContainer = Paper.RedPen
)

/** Monospace is kept only for measurements, where digits should line up. */
val Mono = FontFamily.Monospace

private val AppType = Typography(
    displaySmall  = TextStyle(fontSize = 30.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.7).sp),
    headlineSmall = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp),
    titleMedium   = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
    titleSmall    = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium),
    bodyLarge     = TextStyle(fontSize = 16.5.sp, lineHeight = 26.sp),
    bodyMedium    = TextStyle(fontSize = 14.5.sp, lineHeight = 22.sp),
    bodySmall     = TextStyle(fontSize = 13.sp, lineHeight = 19.sp),
    labelSmall    = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.1.sp)
)

@Composable
fun SiftTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme, typography = AppType, content = content)
}

object Space {
    val xs = 4.dp
    val s = 8.dp
    val m = 14.dp
    val l = 20.dp
    val xl = 30.dp
}
