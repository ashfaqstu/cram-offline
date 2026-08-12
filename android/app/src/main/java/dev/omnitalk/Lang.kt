package dev.omnitalk

/**
 * Target languages.
 *
 * [code] is the Whisper language code, [ttsTag] a BCP-47 tag for Android TTS,
 * [promptName] the English name we put in the prompt (models respond better to
 * "Bengali" than to "bn").
 *
 * ON BENGALI — read before assuming it works. Published numbers put Whisper-tiny
 * at 67-110% WER on Bengali, and Meta does not list Bengali among Llama 3.2's
 * supported languages (en, de, fr, it, pt, hi, es, th). We are shipping it as a
 * selectable target anyway for two reasons:
 *
 *   1. Testability. The developer speaks Bengali; being able to hear whether the
 *      agent is talking sense is worth more right now than a language we cannot
 *      evaluate. Blind development is slower and produces worse results.
 *   2. It is a measurement we owe the write-up regardless. docs/LANGUAGES.md is
 *      supposed to carry a per-language capability matrix, and third-party WER
 *      figures are not the same as numbers from this device and this pipeline.
 *
 * So: measure it, then decide the demo language from data. If Bengali holds up,
 * it is a far better story than Spanish — it is where this was built, and it is
 * exactly the "low-resource language nobody optimizes for" case that makes the
 * mid-tier-hardware thesis land harder. If it does not hold up, we publish the
 * negative result and demo in a language that does.
 */
enum class Lang(
    val code: String,
    val ttsTag: String,
    val display: String,
    val promptName: String
) {
    BN("bn", "bn-BD", "বাংলা", "Bengali"),
    HI("hi", "hi-IN", "हिन्दी", "Hindi"),
    ES("es", "es-ES", "Español", "Spanish"),
    EN("en", "en-US", "English", "English");

    companion object {
        /**
         * MEASURED 2026-08-13 on this device, replaying captured Bengali speech
         * through whisper-cli with the whole utterance and `-l bn`:
         *   tiny -> " Keep it to soul."
         *   base -> " ki kottisu"
         * Neither produces Bengali script. This is the models, not our chunking —
         * consistent with published 67-110% WER for Whisper on Bengali. So BN
         * stays selectable and honestly labelled, but HI leads: Llama 3.2 lists
         * Hindi as supported and Whisper handles it far better.
         */
        val pickable = listOf(HI, EN, ES, BN)
    }
}
