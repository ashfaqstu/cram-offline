# Troubleshooting

Every problem here was hit for real during development, with the exact error text so
it is searchable. If you hit something new, add it.

---

## Phone / adb

### `adb devices` shows nothing

The device often enumerates fine in Windows but adb does not see it until the server
is restarted:

```bash
adb kill-server && adb start-server && adb devices
```

If still empty, check Windows can see it at all:

```powershell
Get-PnpDevice -PresentOnly | Where-Object { $_.InstanceId -match 'VID_2717|VID_22D9' } |
  Select-Object Status, Class, FriendlyName
```

`VID_2717` = Xiaomi/POCO/Redmi, `VID_22D9` = Oppo/Realme. You want to see an
**ADB Interface** entry. If you only see an MTP/portable device, USB debugging is off.
If you see nothing, try another cable — many charge-only cables carry no data lines.

### Device shows as `unauthorized`

The RSA prompt is waiting on the phone screen. Unlock it and accept. If it never
appears: `adb kill-server`, unplug, replug.

### `INSTALL_FAILED_USER_RESTRICTED: Install canceled by user`

MIUI / Realme UI blocking adb installs. Developer options → **Install via USB** ON.
Usually requires signing into a Mi/Realme account, sometimes with a SIM inserted.
Turning **MIUI optimization** OFF on the same screen often unblocks it too.

Workaround if you cannot enable it: push the APK and tap it in a file manager.

```bash
adb push android/app/build/outputs/apk/release/app-release.apk /sdcard/Download/
```

### `java.lang.SecurityException: Injecting to another application requires INJECT_EVENTS`

`adb shell input` is blocked. Enable **USB debugging (Security settings)** in
Developer options; a reboot is sometimes needed. Only affects scripted UI taps — the
`--es selftest` path does not need it.

### The device shows as `ATOLL-AB-IDP` or the USB PID changes

The phone is not booted into Android — bootloader, diagnostic or download mode. Hold
**Power for 10–15 s** to force a normal restart. Do not run fastboot or EDL tools.

---

## Native build

### `LLVM ERROR: IO failure on output stream: No space left on device`

Followed by `PLEASE submit a bug report to https://github.com/android-ndk/ndk/issues`.

**Not an NDK bug — the disk is full.** A default `cmake --build` on llama.cpp builds
**520 targets**, each statically linked *with* debug symbols: `llama-cli` alone comes
out at 196 MB and the build tree reaches 8 GB. Always build with an explicit target
list and strip:

```
-DLLAMA_BUILD_TESTS=OFF -DLLAMA_BUILD_EXAMPLES=OFF
-DCMAKE_C_FLAGS=-g0 -DCMAKE_CXX_FLAGS=-g0 -DCMAKE_EXE_LINKER_FLAGS=-s
cmake --build build-android -j 6 --target llama-cli llama-bench
```

`scripts/build_native.ps1` already does this.

### `ninja: error: unknown target 'llama-cli', did you mean 'llama-app'?`

`tools/cli` sits inside `if (LLAMA_BUILD_SERVER)` in this revision, so
`LLAMA_BUILD_SERVER=OFF` silently deletes the target. It was never renamed. Set
`-DLLAMA_BUILD_SERVER=ON` for that configure — ninja still builds only the targets you
name, so it costs nothing.

**General rule:** after any configure, check before trusting a target name:
`ninja -C <builddir> -t targets | grep <name>`

### KleidiAI download fails during CMake configure

```
error: downloading 'https://github.com/ARM-software/kleidiai/releases/...' failed
        status_code: 56  "Failure when receiving data from the peer"
```

`GGML_CPU_KLEIDIAI=ON` makes ggml FetchContent the tarball at configure time. The
source is vendored at `third_party/kleidiai` and `native/CMakeLists.txt` points
FetchContent at it, so configure never touches the network. If you see this, the
vendored copy is missing — restore it from git.

### `SIGILL` / `signal 4` at runtime

Something was built with too high a `-march`. The official `docs/android.md` example
uses `-march=armv8.7a`; Cortex-A76/A78 are **Armv8.2-A** and will execute an illegal
instruction. Build with **no** explicit `-march` (GGML dispatches at runtime). If you
must pin it: `-march=armv8.2-a+dotprod+fp16`.

---

## Gradle

### `Could not find ... ndk` / CMake toolchain file missing

AGP defaults to its own NDK version. `app/build.gradle.kts` pins
`ndkVersion = "27.3.13750724"` — if you change the installed NDK, change that too.

### Gradle downloads fail with `The underlying connection was closed`

PowerShell 5.1 defaults to old TLS and gradle.org rejects it. Use `curl` instead, or
force TLS 1.2:

```powershell
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
```

### Build succeeds but the app has the old behaviour

Gradle occasionally keeps a stale native library. Force it:

```bash
cd android && ./gradlew clean :app:assembleRelease --no-daemon
```

---

## Runtime crashes

### `SIGSEGV` in `ggml_vec_dot_q4_0_q8_0` / `ggml_compute_forward_mul_mat`

`n_ubatch` was left larger than `n_batch`. Defaults are `n_batch=2048`,
`n_ubatch=512`, so lowering only `n_batch` overruns the compute buffer and it dies
inside a GGML kernel. **Always set both together** — see `llmLoad` in
`native/otjni.cpp`.

### `SIGABRT` — `Unexpected empty grammar stack after accepting piece: {`

`llama_sampler_sample()` **already calls** `llama_sampler_accept()` internally (it is
in the pseudocode above the declaration in `llama.h`). Calling accept again advances
the grammar twice per token and empties the stack. Do not call it.

Read the abort reason from the tombstone:

```bash
adb logcat -b crash -d | grep -i "abort message"
```

### The phone slows to a crawl, other apps get killed, sometimes reboots

A llama.cpp invocation without `-c`. Llama 3.2's trained context is 131072 tokens, so
the KV cache is sized for that — gigabytes. **Every** invocation must pass `-c 2048`
(or `-c 512` for a one-token probe).

### The app answers instantly with the same sentence every time

The transcript was empty, so the model answered from the pre-warmed prefix alone.
Check `bench/results/turnlogs/turn_*.json` for `audio_peak`. If it is ~0.000 the
microphone captured nothing.

---

## Model / quality

### Transcript is garbage but the audio sounds fine

Whisper's encoder always processes a **30-second window**, so short chunks are mostly
padding with no cross-chunk context. On identical audio:

| | |
|---|---|
| whole utterance | *"We have buses to Cox's Bazaar at 8 in the morning, 12 noon and 10 at night."* |
| 5 s chunks | `"Gohhtaka!"` |

The pipeline now buffers during capture and transcribes once at end of speech. Do not
reintroduce chunked streaming ASR without measuring it.

### The model invents facts

Expected — a 1B model will not honour "copied never invented" on instruction alone. It
returned `departure: "7 am"` and `price: "$50.00"` copied straight out of the prompt's
worked example, on a turn where neither was mentioned. `AgentFsm.supportedBy()`
rejects any value not supported by the transcript. Its own `done` flag is ignored for
the same reason.

### Output is prose instead of JSON

The grammar failed to parse and llama.cpp fell back to unconstrained sampling —
it logs `grammar failed to parse` and otherwise carries on silently. **Keep one rule
per line** in `agent.gbnf`; a multi-line `root` rule breaks the parser.

Validate a grammar without rebuilding the app:

```bash
adb shell "cd /data/local/tmp/ot && ./llama-cli -m Llama-3.2-1B-Instruct-Q4_0.gguf \
  -c 512 -n 80 -t 6 -st --grammar-file agent.gbnf -p 'test'"
```

### Model emits `"s":{"ac":":",":":":", ...` until the token limit

Unbounded repetition in the grammar. `slots` is capped at `{0,2}` extra pairs — an
objective never has more than three. This cut decode from 160 tokens to ~30.

### Bengali produces nonsense

Measured on device, whole utterance, `-l bn`: tiny → `"Keep it to soul."`,
base → `"ki kottisu"`. This is the models, not the pipeline, and matches published
67–110 % WER for Whisper on Bengali. English is near-perfect. **Do not spend time on
this** — record it in `docs/LANGUAGES.md` as a result and demo in Hindi or Spanish.
