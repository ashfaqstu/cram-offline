package dev.omnitalk.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.omnitalk.AppState

@Composable
fun SettingsScreen(vm: AppState) {
    val s = vm.settings
    val cores = vm.topo.cores
    /** The startup probe has run. Before it does, every derived timing is fiction. */
    val measured = vm.engine.prefillTps > 0.5

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScreenHeader(
            "Settings",
            if (s.isModified) "Tuned by hand" else "Tuned automatically for this phone"
        )

        Column(
            Modifier.padding(horizontal = Space.l),
            verticalArrangement = Arrangement.spacedBy(Space.m)
        ) {

            // What the app measured about this phone, and what it chose because of it.
            Appear {
                Card(accent = true) {
                    SectionLabel("Measured on first run")
                    Spacer(Modifier.height(Space.s))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        // Until the probe has run, prefillTps is 0. Printing "0 t/s"
                        // reads as a broken phone rather than an unfinished measurement.
                        Stat("prefill", if (measured) "%.0f t/s".format(vm.engine.prefillTps) else "...")
                        Stat("cores", "${vm.topo.nBig} + ${vm.topo.nLittle}")
                        Stat("i8mm", if (vm.topo.i8mm) "yes" else "no")
                    }
                    Spacer(Modifier.height(Space.s))
                    Text(
                        if (measured)
                            "The app timed a real prefill on this phone and sized the settings " +
                            "below from that measurement, not from a number baked in on some " +
                            "other device. You can change them."
                        else
                            "Timing a real prefill on this phone. The settings below will be " +
                            "sized from that measurement rather than from a number baked in on " +
                            "some other device.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Paper.InkSoft
                    )
                }
            }

            AnimatedVisibility(s.isModified, enter = fadeIn(), exit = fadeOut()) {
                Card {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Pill("modified", Tone.Warn)
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = { s.resetToOptimal(); vm.applySettings() }) {
                            Text("Reset to measured")
                        }
                    }
                }
            }

            // Presets in the reader's language, with the machine's number kept
            // underneath. "Slide text sent to the model: 900 characters" is
            // honest but speaks the engine's language; "How much of the slide it
            // reads" is the same setting phrased as what it does. The measured
            // cost stays on screen so the trade-off is still visible.
            Card {
                SectionLabel("How much of the slide it reads")
                Spacer(Modifier.height(Space.s))
                val tps = vm.engine.prefillTps
                // The ladder starts at the calibrated value and only climbs. Going
                // below it is not a "faster" option, it is the bug that cut a
                // four-item list in half and let the model invent the rest.
                val presets = listOf(
                    Triple("Brief", s.optimalCharBudget, "measured for this phone"),
                    Triple("Balanced", (s.optimalCharBudget * 1.4).toInt(), "more room for lists"),
                    Triple("Thorough", (s.optimalCharBudget * 2.0).toInt(), "whole slide, slowest")
                )
                Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                    for ((label, chars, _) in presets) {
                        val on = kotlin.math.abs(s.charBudget - chars) < 60
                        Surface(
                            shape = MaterialTheme.shapes.large,
                            color = if (on) Paper.Blue else Paper.Card,
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp, if (on) Paper.Blue else Paper.Rule
                            ),
                            modifier = Modifier.weight(1f).clickable {
                                s.charBudget = chars.coerceIn(300, 2400); s.save(); vm.applySettings()
                            }
                        ) {
                            Column(
                                Modifier.padding(vertical = 10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    label, style = MaterialTheme.typography.titleSmall,
                                    color = if (on) Paper.Card else Paper.InkSoft
                                )
                                // The same arithmetic the calibrator used, so the
                                // number here is the one the reader will wait.
                                Text(
                                    if (measured) "~%.0fs".format(chars / 3.6 / tps) else "$chars ch",
                                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = Mono),
                                    color = if (on) Paper.Card.copy(alpha = .8f) else Paper.InkFaint
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(Space.s))
                Text(
                    (presets.firstOrNull { kotlin.math.abs(s.charBudget - it.second) < 60 }?.third
                        ?.plus(" - ") ?: "") +
                    "${s.charBudget} characters of slide text" +
                    if (measured)
                        ". The time shown is the longest you would wait for the first word; " +
                        "short slides come back sooner."
                    else "",
                    style = MaterialTheme.typography.bodySmall, color = Paper.InkSoft
                )
                s.warningFor("budget", cores)?.let {
                    Spacer(Modifier.height(Space.s))
                    Surface(color = Paper.RedPenSoft, shape = MaterialTheme.shapes.medium) {
                        Text(
                            it, style = MaterialTheme.typography.bodySmall,
                            color = Paper.RedPen, modifier = Modifier.padding(10.dp)
                        )
                    }
                }
            }

            Card {
                SectionLabel("How long answers are")
                Spacer(Modifier.height(Space.s))
                // 80 is the calibrated default, so the untouched app shows a
                // preset already lit rather than three empty boxes.
                val lengths = listOf("A sentence" to 80, "A paragraph" to 150, "Full" to 220)
                val nearest = lengths.minByOrNull { kotlin.math.abs(s.maxAnswerTokens - it.second) }
                Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                    for ((label, tok) in lengths) {
                        val on = nearest?.second == tok
                        Surface(
                            shape = MaterialTheme.shapes.large,
                            color = if (on) Paper.Blue else Paper.Card,
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp, if (on) Paper.Blue else Paper.Rule
                            ),
                            modifier = Modifier.weight(1f).clickable {
                                s.maxAnswerTokens = tok; s.save(); vm.applySettings()
                            }
                        ) {
                            Text(
                                label, style = MaterialTheme.typography.titleSmall,
                                color = if (on) Paper.Card else Paper.InkSoft,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp)
                            )
                        }
                    }
                }
                Spacer(Modifier.height(Space.s))
                Text(
                    "Roughly one second for every seven words on this phone.",
                    style = MaterialTheme.typography.bodySmall, color = Paper.InkSoft
                )
            }

            Setting(
                title = "Threads for writing the answer",
                value = "${s.decodeThreads} of $cores cores",
                warning = s.warningFor("decode", cores),
                sliderValue = s.decodeThreads.toFloat(),
                range = 1f..cores.toFloat(),
                steps = (cores - 2).coerceAtLeast(0),
                onChange = { s.decodeThreads = it.toInt() },
                onDone = { s.save(); vm.reloadModel() },
                help = "Changing this reloads the model."
            )

            Setting(
                title = "Threads for reading the slides",
                value = "${s.prefillThreads} of $cores cores",
                warning = null,
                sliderValue = s.prefillThreads.toFloat(),
                range = 1f..cores.toFloat(),
                steps = (cores - 2).coerceAtLeast(0),
                onChange = { s.prefillThreads = it.toInt() },
                onDone = { s.save(); vm.reloadModel() },
                help = "Reading the slides scales with cores in a way that writing does not."
            )

            Card {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Show other matches", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Extra slides below the answer, for checking the working",
                            style = MaterialTheme.typography.bodySmall, color = Paper.InkSoft
                        )
                    }
                    Switch(checked = s.showSources, onCheckedChange = { s.showSources = it; s.save() })
                }
                Spacer(Modifier.height(Space.m))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Ruled paper background", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Slowly drifting page texture. Turn it off for a plain background.",
                            style = MaterialTheme.typography.bodySmall, color = Paper.InkSoft
                        )
                    }
                    Switch(
                        checked = s.animatedBackground,
                        onCheckedChange = { s.animatedBackground = it; s.save() }
                    )
                }
            }

            // The project's finding, on the reader's own hardware.
            Card {
                SectionLabel("About this phone")
                Spacer(Modifier.height(Space.s))
                Text(
                    "${vm.topo.nBig} fast cores + ${vm.topo.nLittle} efficient cores at " +
                    "${vm.topo.maxKhz / 1000} MHz.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(Space.s))
                Text(
                    if (vm.topo.kleidiCapable)
                        "This CPU has the matrix extensions Arm's KleidiAI kernels need, so they " +
                        "are active."
                    else
                        "Arm's KleidiAI is inert on this CPU: its int4 kernels require i8mm or " +
                        "SME, and this processor has neither. We measured no difference with it " +
                        "switched on or off, so the speed here comes from how the app is built, " +
                        "not from the library.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Paper.InkSoft
                )
            }

            Card {
                SectionLabel("Privacy")
                Spacer(Modifier.height(Space.s))
                Text(
                    "This app has no internet permission and no storage permission. It cannot " +
                    "send your documents anywhere, even by mistake.",
                    style = MaterialTheme.typography.bodySmall, color = Paper.InkSoft
                )
            }

            Spacer(Modifier.height(Space.l))
        }
    }
}

/**
 * One tunable. The warning appears under the slider rather than in a dialog: a
 * dialog would have to be dismissed before the effect could be seen, and the point
 * is to explain the trade-off while the reader is making it.
 */
@Composable
private fun Setting(
    title: String,
    value: String,
    warning: String?,
    sliderValue: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    onChange: (Float) -> Unit,
    onDone: () -> Unit,
    help: String
) {
    Card {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Pill(value, if (warning != null) Tone.Warn else Tone.Neutral)
        }
        Slider(
            value = sliderValue,
            onValueChange = onChange,
            onValueChangeFinished = onDone,
            valueRange = range,
            steps = steps
        )
        Text(help, style = MaterialTheme.typography.bodySmall, color = Paper.InkSoft)
        AnimatedVisibility(warning != null, enter = fadeIn(), exit = fadeOut()) {
            Column {
                Spacer(Modifier.height(Space.s))
                Surface(
                    color = Paper.RedPenSoft,
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(9.dp)
                ) {
                    Text(
                        warning.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = Paper.RedPen,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }
        }
    }
}
