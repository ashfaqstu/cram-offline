package dev.omnitalk

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import dev.omnitalk.ui.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var vm: AppState

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        vm = AppState(this, lifecycleScope)
        vm.boot()
        setContent { SiftTheme { Root(vm) } }
        handleTestIntent(intent)
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent); setIntent(intent); handleTestIntent(intent)
    }

    /**
     * Scripted end-to-end run, no file picker and no human:
     *   adb shell am start -n dev.omnitalk/.MainActivity \
     *     --ez sample true --es ask 'What are the four Coffman conditions'
     */
    private fun handleTestIntent(intent: android.content.Intent?) {
        intent ?: return
        val wantSample = intent.getBooleanExtra("sample", false)
        val q = intent.getStringExtra("ask")
        if (!wantSample && q == null) return
        lifecycleScope.launch {
            if (wantSample && vm.current == null) {
                vm.loadSample()
                var waited = 0
                while ((!vm.modelReady || vm.busy) && waited < 90_000) { delay(500); waited += 500 }
            }
            if (q != null) { vm.requestedTab = 1; vm.question = q; vm.ask() }
        }
    }

    override fun onDestroy() { vm.close(); super.onDestroy() }
}

private enum class Tab(val label: String) {
    Slides("Slides"), Ask("Ask"), Study("Study"), Source("Source"), Settings("Settings")
}

@Composable
private fun Root(vm: AppState) {
    var tab by remember { mutableStateOf(Tab.Slides) }

    LaunchedEffect(vm.requestedTab) {
        if (vm.requestedTab in Tab.entries.indices) {
            tab = Tab.entries[vm.requestedTab]; vm.requestedTab = -1
        }
    }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) { vm.importDoc(uri); tab = Tab.Ask } }

    fun pick() = picker.launch(arrayOf("application/pdf", "text/plain"))

    Scaffold(
        containerColor = Paper.Stock,
        bottomBar = {
            NavigationBar(containerColor = Paper.Card, tonalElevation = 0.dp) {
                Tab.entries.forEachIndexed { i, t ->
                    NavigationBarItem(
                        selected = tab == t,
                        onClick = { tab = t },
                        label = { Text(t.label) },
                        icon = { TabIcon(i, tab == t) },
                        alwaysShowLabel = true,
                        colors = NavigationBarItemDefaults.colors(
                            selectedTextColor = Paper.Blue,
                            unselectedTextColor = Paper.InkFaint,
                            indicatorColor = Paper.BlueSoft
                        )
                    )
                }
            }
        }
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {
            PaperBackground(vm.settings.animatedBackground)
            Column(Modifier.fillMaxSize()) {
                Banner(vm)
                // Plain `when`, not Crossfade.
                // Crossfade holds its children at partial alpha while it settles,
                // and with the drifting background recomposing continuously behind
                // it, it never settled — the answer text rendered permanently
                // washed out and unreadable. Screen content must never depend on
                // an animation completing.
                when (tab) {
                    Tab.Slides -> LibraryScreen(vm, onPick = { pick() }, onOpen = { tab = Tab.Ask })
                    Tab.Ask -> AskScreen(vm) { page -> vm.readerPage = page; tab = Tab.Source }
                    Tab.Study -> StudyScreen(vm) { page -> vm.readerPage = page; tab = Tab.Source }
                    Tab.Source -> ReaderScreen(vm)
                    Tab.Settings -> SettingsScreen(vm)
                }
            }
        }
    }
}

/**
 * Only appears when there is genuinely something to say.
 *
 * It used to sit there permanently reciting core counts and clock speeds, which
 * made the app look like a diagnostic tool and told the reader nothing they
 * wanted. Hardware details moved to Settings, where someone who cares can go and
 * find them.
 */
@Composable
private fun Banner(vm: AppState) {
    val message = when {
        vm.busy -> vm.busyLabel
        !vm.modelReady -> vm.status.ifEmpty { "Getting ready" }
        vm.status.isNotEmpty() -> vm.status
        else -> ""
    }
    AnimatedVisibility(
        visible = message.isNotEmpty(),
        enter = expandVertically(tween(200)) , exit = shrinkVertically(tween(200))
    ) {
        Surface(color = Paper.HighlighterSoft, modifier = Modifier.fillMaxWidth()) {
            Column {
                if (vm.busy || !vm.modelReady) {
                    LinearProgressIndicator(
                        Modifier.fillMaxWidth(),
                        color = Paper.Highlighter,
                        trackColor = Paper.HighlighterSoft
                    )
                }
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = Space.l, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        message,
                        style = MaterialTheme.typography.bodySmall,
                        color = Paper.Ink
                    )
                }
            }
        }
    }
}
