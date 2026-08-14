package dev.omnitalk.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
                        Stat("prefill", "%.0f t/s".format(vm.engine.prefillTps))
                        Stat("cores", "${vm.topo.nBig} + ${vm.topo.nLittle}")
                        Stat("i8mm", if (vm.topo.i8mm) "yes" else "no")
                    }
                    Spacer(Modifier.height(Space.s))
                    Text(
                        "The app timed a real prefill on this phone and chose the settings below " +
                        "so an answer starts in about five seconds. You can change them.",
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

            Setting(
                title = "Slide text sent to the model",
                value = "${s.charBudget} characters",
                warning = s.warningFor("budget", cores),
                sliderValue = s.charBudget.toFloat(),
                range = 300f..2400f,
                steps = 20,
                onChange = { s.charBudget = it.toInt() },
                onDone = { s.save(); vm.applySettings() },
                help = "More context can mean better answers, but every character costs " +
                       "waiting time before the first word appears."
            )

            Setting(
                title = "Answer length limit",
                value = "${s.maxAnswerTokens} tokens",
                warning = s.warningFor("answer", cores),
                sliderValue = s.maxAnswerTokens.toFloat(),
                range = 30f..240f,
                steps = 14,
                onChange = { s.maxAnswerTokens = it.toInt() },
                onDone = { s.save(); vm.applySettings() },
                help = "Cram answers are usually one sentence. A longer limit only matters for " +
                       "questions that genuinely need it."
            )

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
