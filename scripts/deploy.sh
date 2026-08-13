#!/usr/bin/env bash
#
# Build, install, push models, launch. Run after scripts/setup.sh.
#
#   bash scripts/deploy.sh            # build + install + launch
#   bash scripts/deploy.sh --models   # also push the model weights (first run)
#   bash scripts/deploy.sh --test     # run the automated 3-turn self test
#
set -uo pipefail
REPO="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SDK="${ANDROID_HOME:-D:/Android/Sdk}"
ADB="$SDK/platform-tools/adb.exe"
GRADLE="${OT_GRADLE:-D:/dev/gradle-dist/gradle-8.11.1/bin/gradle.bat}"
APPDIR="/sdcard/Android/data/dev.omnitalk/files"
APK="$REPO/android/app/build/outputs/apk/release/app-release.apk"

say()  { printf '\n\033[36m==> %s\033[0m\n' "$*"; }
die()  { printf '\n\033[31mFAILED: %s\033[0m\n' "$*"; exit 1; }

[ -x "$ADB" ] || die "adb not at $ADB — run scripts/setup.sh first"
"$ADB" devices | grep -qw device || die "no authorised device. Check USB debugging + the RSA prompt on the phone."

say "building release APK"
( cd "$REPO/android" && "$GRADLE" :app:assembleRelease --no-daemon ) || die "gradle build failed — see docs/TROUBLESHOOTING.md"
[ -f "$APK" ] || die "APK not produced at $APK"

say "installing"
# Xiaomi/POCO block adb installs until 'Install via USB' is enabled in
# Developer options; the error is INSTALL_FAILED_USER_RESTRICTED.
"$ADB" install -r "$APK" || die "install refused. On MIUI enable Developer options > Install via USB, then retry."

if [ "${1:-}" = "--models" ]; then
  say "pushing models (~800 MB, one time)"
  "$ADB" shell "mkdir -p $APPDIR"
  for m in Llama-3.2-1B-Instruct-Q4_0.gguf ggml-tiny-q5_1.bin; do
    if "$ADB" shell "[ -f $APPDIR/$m ] && echo yes" | grep -q yes; then
      echo "    already on device: $m"
    else
      "$ADB" push "$REPO/models/$m" "$APPDIR/$m" || die "push failed for $m"
    fi
  done
fi

if [ "${1:-}" = "--test" ]; then
  say "pushing test audio + running the 3-turn self test"
  for f in en_t1 en_t2 en_t3; do
    [ -f "$REPO/bench/testaudio/$f.wav" ] && "$ADB" push "$REPO/bench/testaudio/$f.wav" "$APPDIR/$f.wav" >/dev/null
  done
  "$ADB" shell "rm -f $APPDIR/turn_*"
  "$ADB" shell am force-stop dev.omnitalk
  "$ADB" shell "am start -n dev.omnitalk/.MainActivity --es selftest en_t1.wav --es lang en --ez reset true" >/dev/null
  sleep 45
  "$ADB" shell "am start -n dev.omnitalk/.MainActivity --es selftest en_t2.wav" >/dev/null
  sleep 45
  "$ADB" shell "am start -n dev.omnitalk/.MainActivity --es selftest en_t3.wav" >/dev/null
  sleep 45
  mkdir -p "$REPO/bench/results/turnlogs"
  for n in turn_001 turn_002 turn_003; do
    "$ADB" pull "$APPDIR/$n.json" "$REPO/bench/results/turnlogs/$n.json" 2>/dev/null
  done
  say "results in bench/results/turnlogs/"
  exit 0
fi

say "launching"
"$ADB" shell am start -n dev.omnitalk/.MainActivity >/dev/null
echo "  running. Watch the pipeline live with:  bash scripts/watch.sh"
