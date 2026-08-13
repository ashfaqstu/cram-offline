#!/usr/bin/env bash
#
# OmniTalk Edge — one-command development setup for Windows (Git Bash).
#
#   bash scripts/setup.sh
#
# Installs everything needed to build and run the project:
#   JDK 21 · Android command-line tools · NDK 27.3 · CMake · platform-tools
#   Gradle 8.11.1 · Python chart deps · model weights (sha256-verified)
#
# EVERYTHING GOES ON D: BY DEFAULT. The machine this was developed on had 9 GB
# free on C: and the full toolchain plus builds and models needs ~18 GB. Override
# with OT_ROOT=/e/whatever if your layout differs.
#
# Safe to re-run: every step checks before doing anything.
set -uo pipefail

OT_ROOT="${OT_ROOT:-D:/dev}"
SDK="${OT_SDK:-D:/Android/Sdk}"
JDK="$OT_ROOT/jdk-21"
GRADLE_VER="8.11.1"
GRADLE_DIR="$OT_ROOT/gradle-dist/gradle-$GRADLE_VER"
NDK_VER="27.3.13750724"
CLT_URL="https://dl.google.com/android/repository/commandlinetools-win-13114758_latest.zip"

REPO="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

say()  { printf '\n\033[36m==> %s\033[0m\n' "$*"; }
ok()   { printf '    \033[32mok\033[0m %s\n' "$*"; }
warn() { printf '    \033[33m!!\033[0m %s\n' "$*"; }
die()  { printf '\n\033[31mFAILED: %s\033[0m\n' "$*"; exit 1; }

ps1() { powershell -NoProfile -ExecutionPolicy Bypass -Command "$1"; }

# ── 0. sanity ────────────────────────────────────────────────────────────────
say "checking prerequisites"
command -v git >/dev/null   || die "git not found. Install Git for Windows first."
command -v curl >/dev/null  || die "curl not found (ships with Windows 10+)."
command -v powershell >/dev/null || die "powershell not found; run this from Git Bash on Windows."
ok "git, curl, powershell"

free_gb=$(ps1 "[math]::Round((Get-CimInstance Win32_LogicalDisk -Filter \"DeviceID='D:'\").FreeSpace/1GB,1)" | tr -d '\r')
say "D: has ${free_gb} GB free (need ~12 GB for toolchain + models + builds)"
[ "${free_gb%.*}" -lt 8 ] && warn "that is tight; the NDK alone is 2.2 GB"

mkdir -p "$OT_ROOT" "$SDK" || die "cannot create $OT_ROOT / $SDK"

# ── 1. JDK 21 ────────────────────────────────────────────────────────────────
# The Android Gradle Plugin supports JDK 17/21. A newer system JDK (e.g. 26)
# will fail the build with confusing errors, so we install 21 and point
# JAVA_HOME at it rather than touching the system default.
say "JDK 21"
if [ -x "$JDK/bin/javac.exe" ]; then ok "already at $JDK"
else
  ps1 "winget install -e --id EclipseAdoptium.Temurin.21.JDK --location '$(cygpath -w "$JDK")' --accept-package-agreements --accept-source-agreements --disable-interactivity" \
    || die "JDK install failed. Install Temurin 21 manually to $JDK"
  [ -x "$JDK/bin/javac.exe" ] || die "JDK not found at $JDK after install"
  ok "installed"
fi

# ── 2. Android command-line tools ────────────────────────────────────────────
# Android Studio is deliberately NOT installed: it costs ~4 GB and the build
# never needs it. Edit Kotlin in VS Code; Gradle does the rest.
say "Android command-line tools"
if [ -x "$SDK/cmdline-tools/latest/bin/sdkmanager.bat" ]; then ok "already present"
else
  tmp="$OT_ROOT/clt.zip"
  curl -L --fail --retry 3 -o "$tmp" "$CLT_URL" || die "cmdline-tools download failed"
  ps1 "Expand-Archive -Path '$(cygpath -w "$tmp")' -DestinationPath '$(cygpath -w "$OT_ROOT/clt-tmp")' -Force" || die "unzip failed"
  mkdir -p "$SDK/cmdline-tools"
  rm -rf "$SDK/cmdline-tools/latest"
  mv "$OT_ROOT/clt-tmp/cmdline-tools" "$SDK/cmdline-tools/latest" || die "move failed"
  rm -rf "$OT_ROOT/clt-tmp" "$tmp"
  ok "installed"
fi

# ── 3. SDK packages ──────────────────────────────────────────────────────────
# sdkmanager reads licence acceptance from stdin, so feed it a file of y's.
say "SDK packages (NDK is 2.2 GB — this is the slow step)"
if [ -d "$SDK/ndk/$NDK_VER" ] && [ -d "$SDK/platform-tools" ]; then ok "already installed"
else
  yes_file="$OT_ROOT/yes.txt"; : > "$yes_file"
  for _ in $(seq 1 40); do echo y >> "$yes_file"; done
  SDKM="$(cygpath -w "$SDK/cmdline-tools/latest/bin/sdkmanager.bat")"
  SDKW="$(cygpath -w "$SDK")"
  cmd //c "\"$SDKM\" --sdk_root=\"$SDKW\" --licenses < \"$(cygpath -w "$yes_file")\"" >/dev/null 2>&1
  for p in "platform-tools" "platforms;android-34" "build-tools;34.0.0" "cmake;3.22.1" "cmake;3.31.6" "ndk;$NDK_VER"; do
    printf '    installing %s ... ' "$p"
    if cmd //c "\"$SDKM\" --sdk_root=\"$SDKW\" \"$p\" < \"$(cygpath -w "$yes_file")\"" >/dev/null 2>&1
      then echo ok; else echo FAILED; warn "retry manually: sdkmanager \"$p\""; fi
  done
  rm -f "$yes_file"
fi
[ -x "$SDK/platform-tools/adb.exe" ] || die "adb missing at $SDK/platform-tools"

# ── 4. Gradle ────────────────────────────────────────────────────────────────
say "Gradle $GRADLE_VER"
if [ -x "$GRADLE_DIR/bin/gradle.bat" ]; then ok "already present"
else
  mkdir -p "$OT_ROOT/gradle-dist"
  # NOTE: PowerShell 5.1 defaults to old TLS and fails against gradle.org.
  # curl has its own TLS stack and works, so always use curl here.
  curl -L --fail --retry 3 -o "$OT_ROOT/gradle-dist/g.zip" \
       "https://services.gradle.org/distributions/gradle-$GRADLE_VER-bin.zip" || die "Gradle download failed"
  ps1 "Expand-Archive -Path '$(cygpath -w "$OT_ROOT/gradle-dist/g.zip")' -DestinationPath '$(cygpath -w "$OT_ROOT/gradle-dist")' -Force" || die "unzip failed"
  rm -f "$OT_ROOT/gradle-dist/g.zip"
  ok "installed"
fi

# ── 5. environment ───────────────────────────────────────────────────────────
say "environment variables (User scope)"
ps1 "
[Environment]::SetEnvironmentVariable('JAVA_HOME','$(cygpath -w "$JDK")','User')
[Environment]::SetEnvironmentVariable('ANDROID_HOME','$(cygpath -w "$SDK")','User')
[Environment]::SetEnvironmentVariable('ANDROID_SDK_ROOT','$(cygpath -w "$SDK")','User')
[Environment]::SetEnvironmentVariable('ANDROID_NDK','$(cygpath -w "$SDK/ndk/$NDK_VER")','User')
[Environment]::SetEnvironmentVariable('ANDROID_NDK_HOME','$(cygpath -w "$SDK/ndk/$NDK_VER")','User')
[Environment]::SetEnvironmentVariable('GRADLE_USER_HOME','$(cygpath -w "$OT_ROOT/gradle")','User')
\$add = @('$(cygpath -w "$JDK/bin")','$(cygpath -w "$SDK/platform-tools")','$(cygpath -w "$SDK/cmdline-tools/latest/bin")')
\$cur = [Environment]::GetEnvironmentVariable('Path','User'); \$parts = @()
if (\$cur) { \$parts = \$cur -split ';' | Where-Object { \$_ -ne '' } }
foreach (\$a in \$add) { if (\$parts -notcontains \$a) { \$parts = @(\$a) + \$parts } }
[Environment]::SetEnvironmentVariable('Path', (\$parts -join ';'), 'User')
" >/dev/null
ok "JAVA_HOME, ANDROID_HOME, ANDROID_NDK, GRADLE_USER_HOME, PATH"
warn "open a NEW terminal for these to take effect"

# ── 6. submodules ────────────────────────────────────────────────────────────
say "git submodules (llama.cpp, whisper.cpp)"
git -C "$REPO" submodule update --init --recursive || die "submodule init failed"
ok "$(git -C "$REPO" submodule status | wc -l) submodules at pinned commits"

# ── 7. python chart deps ─────────────────────────────────────────────────────
say "python packages for bench/analyze.py"
if command -v python >/dev/null; then
  python -m pip install --quiet matplotlib pandas tabulate 2>/dev/null && ok "matplotlib, pandas, tabulate" \
    || warn "pip install failed — charts will not regenerate, everything else works"
else warn "python not found — skipping (only needed to regenerate charts)"; fi

# ── 8. models ────────────────────────────────────────────────────────────────
say "model weights (~1.6 GB, sha256-verified, never committed)"
bash "$REPO/scripts/fetch_models.sh" || die "model download failed — see scripts/fetch_models.sh"

# ── done ─────────────────────────────────────────────────────────────────────
say "setup complete"
cat <<EOF

  Next:
    1. Open a NEW terminal (environment variables were just set)
    2. Connect the phone: USB debugging ON, accept the RSA prompt
       On Xiaomi/POCO/Redmi also enable, in Developer options:
         - Install via USB
         - USB debugging (Security settings)
       Both usually require a Mi account sign-in.
    3. adb devices                # must list your phone as "device"
    4. bash scripts/deploy.sh     # build, install, push models, launch

  If anything failed above, see docs/TROUBLESHOOTING.md — every error we
  actually hit during development is listed there with its fix.
EOF
