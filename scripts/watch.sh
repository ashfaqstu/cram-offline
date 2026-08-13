#!/usr/bin/env bash
#
# Live per-turn telemetry. Run it, then use the app — each turn is pulled off the
# phone within a few seconds and summarised with a verdict.
#
#   bash scripts/watch.sh
#
# The app writes turn_NNN.wav (the exact audio Whisper saw) and turn_NNN.json
# (transcript, model output, slots, timings) per turn. The WAV matters: when a
# turn goes wrong, "mic was silent", "Whisper mis-heard" and "model ignored a
# correct transcript" look identical from outside. With the audio saved you can
# replay the same bytes against a different model instead of asking someone to
# reproduce it.
set -uo pipefail
REPO="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ADB="${ANDROID_HOME:-D:/Android/Sdk}/platform-tools/adb.exe"
DEV="/sdcard/Android/data/dev.omnitalk/files"
OUT="$REPO/bench/results/turnlogs"
mkdir -p "$OUT"

command -v python >/dev/null || { echo "python required"; exit 1; }
echo "watching $DEV   (Ctrl-C to stop)"
echo "------------------------------------------------------------------"

declare -A seen
while true; do
  for n in $("$ADB" shell "ls $DEV 2>/dev/null | grep -E '^turn_[0-9]+\.json$'" 2>/dev/null | tr -d '\r'); do
    [ -n "${seen[$n]:-}" ] && continue
    seen[$n]=1
    base="${n%.json}"
    "$ADB" pull "$DEV/$n"        "$OUT/$n"        >/dev/null 2>&1
    "$ADB" pull "$DEV/$base.wav" "$OUT/$base.wav" >/dev/null 2>&1
    python - "$OUT/$n" <<'PY'
import json, sys
sys.stdout.reconfigure(encoding="utf-8", errors="replace")
j = json.load(open(sys.argv[1], encoding="utf-8"))
t = {m["mark"]: m["ms"] for m in j.get("trace", {}).get("marks", [])}
print()
print("=== %s  [%s]  lang=%s ===" % (sys.argv[1].split("/")[-1], j.get("mode"), j.get("lang")))
peak = j.get("audio_peak", 0)
note = "SILENT - mic captured nothing" if peak < 0.01 else ("very quiet" if peak < 0.05 else "ok")
print("  audio    %.1f s  peak %.3f  %s" % (j.get("audio_seconds", 0), peak, note))
print("  heard    %r" % j.get("transcript", ""))
print("  asks     %r" % j.get("question", ""))
filled = {k: v for k, v in (j.get("slots") or {}).items() if v}
print("  slots    %s" % (filled or "(none filled)"))
ti = j.get("timings") or {}
if ti:
    print("  perf     prefill %s tok @ %s t/s   decode %s tok @ %s t/s" % (
        ti.get("prefill_tok"), ti.get("prefill_tps"), ti.get("decode_tok"), ti.get("decode_tps")))
if "first_audio" in t and "end_of_speech" in t:
    print("  FIRST AUDIO after speech: %.2f s" % ((t["first_audio"] - t["end_of_speech"]) / 1000.0))
bad = []
if peak < 0.01: bad.append("mic silent")
if not j.get("transcript"): bad.append("ASR produced nothing")
if not j.get("question"): bad.append("no question generated")
raw = (j.get("raw_output") or "").lstrip()
if raw and not raw.startswith("{"): bad.append("output is not JSON")
print("  >>> " + ("PROBLEM: " + ", ".join(bad) if bad else "OK"))
PY
  done
  sleep 3
done
