package dev.omnitalk.rag

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Tunable settings, auto-optimised on first run.
 *
 * The defaults are not constants baked in from one phone: on first launch the app
 * times a real prefill and derives values that keep the answer starting in about
 * five seconds on *this* hardware. Everything stays adjustable afterwards, because
 * the right trade-off depends on the document and on how patient the reader is —
 * but any manual value is flagged, and one tap restores the measured optimum.
 */
class Settings(ctx: Context) {

    private val prefs = ctx.getSharedPreferences("sift_settings", Context.MODE_PRIVATE)

    /** Values the calibration decided. Displayed as "optimised" and used for reset. */
    var optimalCharBudget = RagEngine.PASSAGE_CHAR_BUDGET_DEFAULT; private set
    var optimalDecodeThreads = 6; private set
    var optimalPrefillThreads = 8; private set

    var charBudget by mutableStateOf(prefs.getInt(K_BUDGET, -1))
    var decodeThreads by mutableStateOf(prefs.getInt(K_DECODE, -1))
    var prefillThreads by mutableStateOf(prefs.getInt(K_PREFILL, -1))
    var maxAnswerTokens by mutableStateOf(prefs.getInt(K_ANSWER, RagEngine.MAX_ANSWER_TOKENS))
    var showSources by mutableStateOf(prefs.getBoolean(K_SOURCES, true))
    /** The drifting ruled-paper background. An animation nobody can stop is a bug. */
    var animatedBackground by mutableStateOf(prefs.getBoolean(K_BG, true))

    /**
     * Saved values are only trusted if they came from the current tuning logic.
     *
     * Bump SETTINGS_VERSION whenever the calibration changes meaning. Without
     * this, a budget saved by an older build survives an upgrade and silently
     * overrides the new defaults — which is exactly what happened: a 403-char
     * budget from an earlier, speed-only calibration kept truncating passages
     * long after the minimum had been raised to 900, so the model kept
     * inventing the last item of every list.
     */
    private val storedVersion = prefs.getInt(K_VERSION, 0)
    val calibrated: Boolean
        get() = prefs.getBoolean(K_CALIBRATED, false) && storedVersion == SETTINGS_VERSION

    /** Called once after the model loads and prefill speed has been measured. */
    fun applyCalibration(budget: Int, decode: Int, prefill: Int) {
        optimalCharBudget = budget
        optimalDecodeThreads = decode
        optimalPrefillThreads = prefill
        if (!calibrated) {
            charBudget = budget; decodeThreads = decode; prefillThreads = prefill
            maxAnswerTokens = RagEngine.MAX_ANSWER_TOKENS
            prefs.edit()
                .putInt(K_BUDGET, budget).putInt(K_DECODE, decode).putInt(K_PREFILL, prefill)
                .putInt(K_ANSWER, maxAnswerTokens)
                .putInt(K_VERSION, SETTINGS_VERSION)
                .putBoolean(K_CALIBRATED, true).apply()
        }
        if (charBudget < 0) charBudget = budget
        if (decodeThreads < 0) decodeThreads = decode
        if (prefillThreads < 0) prefillThreads = prefill
        // Never let a saved value fall below what a list-shaped answer needs.
        if (charBudget < RagEngine.PASSAGE_CHAR_BUDGET_MIN) {
            charBudget = RagEngine.PASSAGE_CHAR_BUDGET_MIN
        }
    }

    fun save() {
        prefs.edit()
            .putInt(K_VERSION, SETTINGS_VERSION)
            .putInt(K_BUDGET, charBudget)
            .putInt(K_DECODE, decodeThreads)
            .putInt(K_PREFILL, prefillThreads)
            .putInt(K_ANSWER, maxAnswerTokens)
            .putBoolean(K_SOURCES, showSources)
            .putBoolean(K_BG, animatedBackground)
            .apply()
    }

    fun resetToOptimal() {
        charBudget = optimalCharBudget
        decodeThreads = optimalDecodeThreads
        prefillThreads = optimalPrefillThreads
        maxAnswerTokens = RagEngine.MAX_ANSWER_TOKENS
        save()
    }

    val isModified: Boolean
        get() = charBudget != optimalCharBudget ||
                decodeThreads != optimalDecodeThreads ||
                prefillThreads != optimalPrefillThreads ||
                maxAnswerTokens != RagEngine.MAX_ANSWER_TOKENS

    /** A plain-language warning for a setting that will hurt, or null if it is fine. */
    fun warningFor(key: String, cores: Int): String? = when (key) {
        "decode" ->
            // The measured cliff: on 2 big + 6 LITTLE, using every core made decode
            // 58% SLOWER, because each layer ends in a barrier and the fast cores
            // sit waiting on the slow ones.
            if (decodeThreads >= cores)
                "Using every core usually makes generation slower, not faster. On this class of " +
                "CPU we measured a 58% drop, because the fast cores end up waiting on the slow ones."
            else null
        "budget" -> when {
            charBudget > optimalCharBudget * 1.6 ->
                "More slide text means a longer wait before the first word. Roughly 14 ms per " +
                "character on this phone."
            charBudget < optimalCharBudget * 0.6 ->
                "Very little context. Answers get faster but vaguer, and may miss facts that are " +
                "in your slides."
            else -> null
        }
        "answer" ->
            if (maxAnswerTokens > 160)
                "Long answers take a while: about 1 second for every 7 words on this phone."
            else null
        else -> null
    }

    private companion object {
        /** Bump when calibration changes meaning, to discard stale saved values. */
        const val SETTINGS_VERSION = 2
        const val K_VERSION = "settings_version"
        const val K_BUDGET = "char_budget"
        const val K_DECODE = "decode_threads"
        const val K_PREFILL = "prefill_threads"
        const val K_ANSWER = "max_answer"
        const val K_SOURCES = "show_sources"
        const val K_BG = "animated_bg"
        const val K_CALIBRATED = "calibrated"
    }
}
