package dev.omnitalk

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private const val LLM_FILE = "Llama-3.2-1B-Instruct-Q4_0.gguf"
private const val ASR_FILE = "ggml-tiny-q5_1.bin"

class MainActivity : ComponentActivity() {

    private lateinit var vm: AppState

    private val askMic = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        vm.micGranted = it
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        vm = AppState(this)
        vm.micGranted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        setContent { MaterialTheme(colorScheme = darkColorScheme()) { Root(vm) } }

        if (!vm.micGranted) askMic.launch(Manifest.permission.RECORD_AUDIO)
        lifecycleScope.launch {
            vm.boot()
            handleSelfTest(intent)
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        lifecycleScope.launch { handleSelfTest(intent) }
    }

    /** `--es selftest <file.wav>` and optional `--es lang <bn|hi|es|en>` / `--ez turbo <bool>` */
    private fun handleSelfTest(intent: android.content.Intent?) {
        val wav = intent?.getStringExtra("selftest") ?: return
        intent.getStringExtra("lang")?.let { code ->
            Lang.entries.firstOrNull { it.code == code }?.let { vm.applyLanguage(it) }
        }
        if (intent.hasExtra("turbo")) {
            val want = intent.getBooleanExtra("turbo", true)
            if (want != vm.turbo) { vm.toggleTurbo(); return }   // reload, then re-issue
        }
        if (intent.getBooleanExtra("reset", false)) vm.startObjective(vm.objective)
        vm.runFromWav(wav)
    }

    override fun onDestroy() { vm.close(); super.onDestroy() }
}

// ─────────────────────────────────────────────────────────────────────────────

class AppState(private val act: ComponentActivity) {
    val topo = Topology.read()

    var micGranted by mutableStateOf(false)
    var status by mutableStateOf("starting…")
    var loaded by mutableStateOf(false)
    var turbo by mutableStateOf(true)
    /**
     * English while validating: Whisper is strongest there, so an English pass
     * proves the pipeline and isolates any remaining failure to the language.
     * Switch to HI/ES for the demo once the loop is verified. Bengali measured
     * unusable with tiny/base — see Lang.kt.
     */
    var lang by mutableStateOf(Lang.EN)
    var voiceOk by mutableStateOf(true)
    var recording by mutableStateOf(false)
    var partial by mutableStateOf("")
    var lastLatency by mutableStateOf<Double?>(null)
    var lastTimings by mutableStateOf("")
    var summary by mutableStateOf<String?>(null)

    val lines = mutableStateListOf<Line>()
    data class Line(val who: String, val text: String, val gloss: String)

    var objective by mutableStateOf(Objective.busTicket())
    var fsm = AgentFsm(objective)

    private var pipeline: Pipeline? = null
    private var tts: Tts? = null
    private var grammar: String = ""
    private var stopFlag = false
    private var trace = Trace()

    val modelDir: File get() = act.getExternalFilesDir(null)!!
    private val llmPath get() = File(modelDir, LLM_FILE)
    private val asrPath get() = File(modelDir, ASR_FILE)

    suspend fun boot() {
        grammar = act.assets.open("agent.gbnf").bufferedReader().readText()
        TurnLog.init(modelDir)

        if (!llmPath.exists() || !asrPath.exists()) {
            status = "Models missing. adb push them to:\n${modelDir.absolutePath}"
            return
        }
        status = "loading models…"
        tts = Tts(act) { trace.mark("first_audio") }

        val p = Pipeline(topo, Pipeline.Config(turbo = turbo))
        val ok = p.load(llmPath.absolutePath, asrPath.absolutePath)
        if (!ok) { status = "model load FAILED"; return }
        pipeline = p
        loaded = true

        applyLanguage(lang)
        startObjective(objective)
    }

    fun applyLanguage(l: Lang) {
        lang = l
        voiceOk = tts?.setLanguage(l.ttsTag) ?: false
        status = if (voiceOk) "ready · ${l.display} · ${topo.describe()}"
        else "ready · ${l.display} — NO offline voice, text only · ${topo.describe()}"
        // Prompt language changed, so the pre-warmed prefix is stale.
        startObjective(objective)
    }

    fun startObjective(o: Objective) {
        objective = o
        o.reset()
        fsm = AgentFsm(o)
        lines.clear()
        summary = null
        // O9 — prefill the static prefix now, while the user is still reading.
        act.lifecycleScope.launch {
            pipeline?.prewarm(Prompts.staticPrefix(lang, o))
            status = "ready · ${lang.display} · prewarmed · ${topo.describe()}" +
                    (if (!voiceOk) " · NO VOICE" else "")
        }
    }

    fun onPressStart() {
        if (!loaded || recording) return
        recording = true
        stopFlag = false
        partial = ""
        trace = Trace(if (turbo) "turbo" else "naive")
        tts?.armFirstAudioMark()
        TurnLog.begin()

        act.lifecycleScope.launch {
            val p = pipeline ?: return@launch
            val audio = Audio.record(5.0) { stopFlag }
            val raw = p.runTurn(
                audio = audio,
                promptSuffix = { heard -> Prompts.turnSuffix(fsm, heard) },
                grammar = grammar,
                lang = lang.code,
                trace = trace,
                onPartial = { partial = it },
                onSentence = { s -> tts?.speak(s) }
            )
            val parsed = if (raw.isNotBlank()) fsm.ingest(raw, partial) else false
            TurnLog.finish(trace, partial, raw, p.lastTimings, lang.code, turbo, fsm)

            withContext(Dispatchers.Main) {
                if (raw.isBlank()) {
                    lines += Line("agent", "(heard nothing — hold longer and speak up)", "")
                    recording = false
                    return@withContext
                }
                if (partial.isNotBlank()) lines += Line("them", partial, "")
                if (parsed) {
                    lines += Line("agent", fsm.lastQuestion, fsm.lastGloss)
                } else {
                    lines += Line("agent", "(unparseable reply)", raw.take(120))
                }
                lastLatency = trace.firstAudioLatency()
                lastTimings = p.lastTimings
                if (fsm.done()) summary = fsm.englishSummary()
                recording = false
            }
        }
    }

    fun onPressEnd() { stopFlag = true }

    /**
     * Run one turn from a WAV instead of the microphone.
     *
     *   adb shell am start -n dev.omnitalk/.MainActivity --es selftest test1.wav
     *
     * Same Pipeline, same prompts, same grammar, same TurnLog output — only the
     * audio source differs. Lets the agent loop be regression-tested after every
     * change without a human speaking, and makes a failure replayable.
     */
    fun runFromWav(name: String) {
        if (!loaded || recording) return
        val f = File(modelDir, name)
        if (!f.exists()) { status = "selftest: $name not found"; return }
        recording = true
        partial = ""
        trace = Trace(if (turbo) "turbo" else "naive")
        tts?.armFirstAudioMark()
        TurnLog.begin()
        status = "selftest: $name"

        act.lifecycleScope.launch {
            val p = pipeline ?: return@launch
            val samples = withContext(Dispatchers.IO) { Audio.readWav(f) }
            if (samples.isEmpty()) {
                status = "selftest: $name unreadable"; recording = false; return@launch
            }
            // feed it in the same 5 s chunks the microphone would produce
            val chunk = (Audio.SAMPLE_RATE * 5.0).toInt()
            val flow = kotlinx.coroutines.flow.flow {
                var i = 0
                while (i < samples.size) {
                    val n = minOf(chunk, samples.size - i)
                    emit(samples.copyOfRange(i, i + n))
                    i += n
                }
            }
            val raw = p.runTurn(
                audio = flow,
                promptSuffix = { heard -> Prompts.turnSuffix(fsm, heard) },
                grammar = grammar,
                lang = lang.code,
                trace = trace,
                onPartial = { partial = it },
                onSentence = { s -> tts?.speak(s) }
            )
            val parsed = if (raw.isNotBlank()) fsm.ingest(raw, partial) else false
            TurnLog.finish(trace, partial, raw, p.lastTimings, lang.code, turbo, fsm)
            withContext(Dispatchers.Main) {
                if (partial.isNotBlank()) lines += Line("them", partial, "")
                if (parsed) lines += Line("agent", fsm.lastQuestion, fsm.lastGloss)
                else lines += Line("agent", "(unparseable)", raw.take(120))
                lastLatency = trace.firstAudioLatency()
                lastTimings = p.lastTimings
                if (fsm.done()) summary = fsm.englishSummary()
                recording = false
                status = "selftest done · ${lang.display}"
            }
        }
    }

    fun toggleTurbo() {
        turbo = !turbo
        loaded = false
        status = "reloading in ${if (turbo) "TURBO" else "NAIVE"} mode…"
        act.lifecycleScope.launch {
            pipeline?.close()
            val p = Pipeline(topo, Pipeline.Config(turbo = turbo))
            if (p.load(llmPath.absolutePath, asrPath.absolutePath)) {
                pipeline = p; loaded = true
                startObjective(objective)
            } else status = "reload failed"
        }
    }

    fun close() { pipeline?.close(); tts?.close() }
}

// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun Root(vm: AppState) {
    Surface(Modifier.fillMaxSize(), color = Color(0xFF0F1315)) {
        Column(Modifier.fillMaxSize().padding(14.dp)) {
            Header(vm)
            Spacer(Modifier.height(8.dp))
            LangPicker(vm)
            Spacer(Modifier.height(8.dp))
            ObjectiveBoard(vm)
            Spacer(Modifier.height(10.dp))
            Transcript(vm, Modifier.weight(1f))
            Metrics(vm)
            Spacer(Modifier.height(8.dp))
            TalkButton(vm)
        }
    }
}

@Composable
private fun Header(vm: AppState) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("OmniTalk Edge", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold)
            Text(vm.status, color = Color(0xFF7D888F), fontSize = 11.sp, fontFamily = FontFamily.Monospace)
        }
        // F3 — the judge flips this and feels the difference in five seconds.
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(if (vm.turbo) "TURBO" else "NAIVE",
                color = if (vm.turbo) Color(0xFF34CFC0) else Color(0xFFE0736A),
                fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            Switch(checked = vm.turbo, onCheckedChange = { vm.toggleTurbo() })
        }
    }
}

@Composable
private fun LangPicker(vm: AppState) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("SPEAK", color = Color(0xFF7D888F), fontSize = 10.sp,
            fontFamily = FontFamily.Monospace, letterSpacing = 1.5.sp,
            modifier = Modifier.padding(end = 8.dp))
        for (l in Lang.pickable) {
            val on = vm.lang == l
            Box(
                Modifier.padding(end = 6.dp)
                    .background(
                        if (on) Color(0xFF14322F) else Color(0xFF171C1F),
                        RoundedCornerShape(4.dp)
                    )
                    .pointerInput(l) {
                        detectTapGestures(onTap = { if (vm.loaded) vm.applyLanguage(l) })
                    }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(l.display,
                    color = if (on) Color(0xFF34CFC0) else Color(0xFF7D888F),
                    fontSize = 13.sp,
                    fontWeight = if (on) FontWeight.Bold else FontWeight.Normal)
            }
        }
        if (!vm.voiceOk) {
            Text("no voice", color = Color(0xFFE0736A), fontSize = 10.sp,
                fontFamily = FontFamily.Monospace)
        }
    }
}

@Composable
private fun ObjectiveBoard(vm: AppState) {
    Column(
        Modifier.fillMaxWidth()
            .background(Color(0xFF171C1F), RoundedCornerShape(6.dp))
            .padding(12.dp)
    ) {
        Text("OBJECTIVE", color = Color(0xFF7D888F), fontSize = 10.sp,
            fontFamily = FontFamily.Monospace, letterSpacing = 1.5.sp)
        Text(vm.objective.title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        // The slot board is how an English-only judge follows a Spanish conversation.
        for (s in vm.objective.slots) {
            Row(Modifier.padding(vertical = 2.dp)) {
                Text(if (s.value != null) "✓" else "○",
                    color = if (s.value != null) Color(0xFF34CFC0) else Color(0xFF5C6672),
                    fontSize = 13.sp, modifier = Modifier.width(20.dp))
                Text(s.label, color = Color(0xFFB3BCC2), fontSize = 13.sp, modifier = Modifier.width(140.dp))
                Text(s.value ?: "—", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            }
        }
        Spacer(Modifier.height(4.dp))
        Text("Turn ${vm.fsm.turn} of 6", color = Color(0xFF7D888F), fontSize = 11.sp,
            fontFamily = FontFamily.Monospace)
    }
}

@Composable
private fun Transcript(vm: AppState, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        vm.summary?.let {
            Column(Modifier.fillMaxWidth()
                .background(Color(0xFF14322F), RoundedCornerShape(6.dp)).padding(12.dp)) {
                Text(it, color = Color(0xFF34CFC0), fontSize = 14.sp)
            }
            Spacer(Modifier.height(8.dp))
        }
        for (l in vm.lines) {
            val agent = l.who == "agent"
            Column(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                Text(if (agent) "🤖 ${l.text}" else "👤 ${l.text}",
                    color = if (agent) Color.White else Color(0xFFD8964F), fontSize = 15.sp)
                if (l.gloss.isNotBlank())
                    Text(l.gloss, color = Color(0xFF7D888F), fontSize = 12.sp,
                        modifier = Modifier.padding(start = 22.dp))
            }
        }
        if (vm.partial.isNotBlank() && vm.recording)
            Text("… ${vm.partial}", color = Color(0xFF5C6672), fontSize = 13.sp)
    }
}

@Composable
private fun Metrics(vm: AppState) {
    val fa = vm.lastLatency
    Text(
        buildString {
            append(if (fa != null) "FIRST AUDIO ${"%.2f".format(fa / 1000.0)} s" else "FIRST AUDIO —")
            if (vm.lastTimings.isNotBlank()) {
                Regex("\"decode_tps\":([\\d.]+)").find(vm.lastTimings)?.let {
                    append("   decode ${it.groupValues[1]} tok/s")
                }
                Regex("\"prefill_tok\":(\\d+)").find(vm.lastTimings)?.let {
                    append("   prefill ${it.groupValues[1]} tok")
                }
            }
        },
        color = if (vm.turbo) Color(0xFF34CFC0) else Color(0xFFE0736A),
        fontSize = 12.sp, fontFamily = FontFamily.Monospace
    )
}

@Composable
private fun TalkButton(vm: AppState) {
    // GESTURE LAYER — its modifier chain must NEVER change, or an in-flight press
    // is cancelled mid-hold. Previously the background colour was on this Box and
    // depended on vm.recording, so the moment recording started the Box recomposed
    // and tryAwaitRelease() returned early: recording lasted ~1.3 s regardless of
    // how long you actually held it, and the turn completed on press.
    // Keep state reads inside the lambda, and put anything that changes in a child.
    Box(
        Modifier
            .fillMaxWidth()
            .height(70.dp)
            .pointerInput(Unit) {
                detectTapGestures(onPress = {
                    if (!(vm.loaded && vm.micGranted)) return@detectTapGestures
                    vm.onPressStart()
                    tryAwaitRelease()
                    vm.onPressEnd()
                })
            }
    ) {
        // VISUAL LAYER — free to recompose as much as it likes.
        val enabled = vm.loaded && vm.micGranted
        Box(
            Modifier.matchParentSize().background(
                when {
                    !enabled -> Color(0xFF2C3438)
                    vm.recording -> Color(0xFFA33A32)
                    else -> Color(0xFF0A7D74)
                }, RoundedCornerShape(10.dp)
            ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                when {
                    !vm.micGranted -> "GRANT MICROPHONE"
                    !vm.loaded -> "LOADING…"
                    vm.recording -> "LISTENING — RELEASE TO SEND"
                    else -> "HOLD TO SPEAK"
                },
                color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold
            )
        }
    }
}
