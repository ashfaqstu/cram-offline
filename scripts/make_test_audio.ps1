# Generates the demo-script lines as 16 kHz mono WAVs using Windows SAPI, so the
# agent loop can be regression-tested without a person speaking.
#
# Android offers no way to inject audio into the microphone, so the app takes a
# `selftest` intent that runs the same pipeline from a file instead. Synthetic
# speech is not a substitute for real-voice testing, but it makes the FSM,
# slot-extraction and anti-hallucination behaviour repeatable after every change.
Add-Type -AssemblyName System.Speech
$OUT = 'D:\omnitalk\bench\testaudio'
New-Item -ItemType Directory -Force -Path $OUT | Out-Null

$lines = [ordered]@{
  'en_t1' = 'Hello, welcome to the ticket counter. How can I help you today?'
  'en_t2' = "We have buses to Cox's Bazar at eight in the morning, twelve noon, and ten at night."
  'en_t3' = 'Only the ten o clock bus has air conditioning, and a ticket costs fifteen hundred taka.'
}

$fmt = New-Object System.Speech.AudioFormat.SpeechAudioFormatInfo(
        16000,
        [System.Speech.AudioFormat.AudioBitsPerSample]::Sixteen,
        [System.Speech.AudioFormat.AudioChannel]::Mono)

$s = New-Object System.Speech.Synthesis.SpeechSynthesizer
"voices available:"
$s.GetInstalledVoices() | ForEach-Object { "  " + $_.VoiceInfo.Name + " [" + $_.VoiceInfo.Culture + "]" }
$s.Rate = -1     # slightly slower than default; closer to how people actually talk

foreach ($k in $lines.Keys) {
  $p = Join-Path $OUT "$k.wav"
  $s.SetOutputToWaveFile($p, $fmt)
  $s.Speak($lines[$k])
  "{0,-8} {1,7:N0} bytes  '{2}'" -f $k, (Get-Item $p).Length, $lines[$k]
}
$s.SetOutputToNull()
$s.Dispose()
"`nwrote to $OUT"
