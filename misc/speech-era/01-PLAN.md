# OmniTalk Edge — End-to-End Execution Plan

**A plan written to be followed literally.** Every block has exact commands and a hard **exit criterion**. If an exit criterion fails, go to the block's *Fallback* and keep moving. Do not improvise new scope.

- **Deadline:** 2026-08-14 16:00 PT = **2026-08-15 05:00 Asia/Dhaka**
- **Team:** 2 people. `[BUILD]` = friend (native/Android). `[DOC]` = you (docs, video, benchmarks analysis, submission).
- **Rule of the plan:** a *measured* optimization is worth more than a *feature*. When behind schedule, cut features, never cut measurements.

---

## 0. Ground truth about the target device

Do not take these on faith — Block 1 verifies them on the phone. But this is what the plan assumes.

| Property | Value | Consequence |
|---|---|---|
| Device | Realme Narzo 50 Pro 5G | The demo device *and* the thesis |
| SoC | MediaTek Dimensity 920 (6 nm) | Mid-tier, 2021 |
| CPU | 2× Cortex-A78 @ ~2.5 GHz + 6× Cortex-A55 @ ~2.0 GHz | Heterogeneous → O3 (affinity) is a real win |
| Arch | **Armv8.2-A** | `asimddp` (SDOT/dotprod) **yes**; `i8mm` **no**; SVE/SME/SME2 **no** |
| RAM | 6 GB total | Budget **≤ 1.6 GB RSS** for the app. Hard ceiling. |
| Cores | Expect `cpu0–cpu5` = A55, `cpu6–cpu7` = A78 → big mask `0xC0` | **Verify in Block 1**, masks differ by vendor |

**Design consequence:** one model resident at a time is impossible (ASR and LLM must overlap for O4), so both must be small. Total resident target: Whisper base q5_1 (~60 MB) + Llama 3.2 1B Q4_0 (~770 MB mmap) + KV cache (~120 MB at 2048 ctx) + app (~300 MB) ≈ **1.25 GB**. Comfortable.

---

## 1. Final technology decisions (locked — do not revisit)

| Layer | Decision | Rationale | Rejected |
|---|---|---|---|
| Inference runtime | **llama.cpp + whisper.cpp** (both GGML) | One NDK toolchain for both. KleidiAI is a build flag. `llama-bench` produces publication-grade numbers for free. **GBNF grammars** (needed for O6) exist nowhere else. Ready-made `examples/llama.android` and `examples/whisper.android` to fork. | ExecuTorch (no WSL/NDK installed, day-long export, no grammars, 0.7→1.x API churn); MediaPipe; ONNX Runtime |
| LLM | **Llama-3.2-1B-Instruct, `Q4_0`** | Officially supports es/hi/fr/de/it/pt/th. **Q4_0 is one of only two quant types KleidiAI accelerates** — this is optimization O1. Auto-repacks to the Arm layout on load. | Q4_K_M (bypasses KleidiAI); 3B (too slow on 2×A78); Qwen3-1.7B (slower, though Apache-2.0 — keep as documented alternate) |
| ASR | **whisper.cpp, `ggml-base-q5_1.bin`** (60 MB, multilingual) | RTF ≈ 0.4–0.6 on A78 → real-time capable. `tiny` is faster but noticeably worse; `small` is RTF > 1 → unusable. | Whisper small/turbo (too slow here); Android SpeechRecognizer (needs network on most devices) |
| TTS | **Android `TextToSpeech`** with downloaded offline voices | Zero build cost, guaranteed to work on video, genuinely on-device. TTS is not our optimization target so spending a day on it buys nothing. | Piper / sherpa-onnx → **stretch only, P3** |
| Vision / OCR | **none** | Cut. See `00-VERDICT.md`. | PaddleOCR, KleidiCV, ML Kit |
| App | **Kotlin + Jetpack Compose**, single Activity, forked from `examples/llama.android` | Fastest path to a working UI with an existing JNI bridge | Flutter, React Native |
| Benchmarking | **`otbench`**: adb shell sweep → CSV → `analyze.py` → tables + PNGs, plus an in-app Benchmark screen | Judges can reproduce on *their* phone. This is the "reusable artifact" for Potential Impact. | Arm Performix (Neoverse/cloud only — **do not mention**) |
| Profiling (optional) | `simpleperf` from the NDK | One flamegraph screenshot is a nice-to-have | Arm Streamline (large install, not worth 3 days) |
| Demo language | **English ↔ Spanish** primary; packs for hi/fr/de/pt/it/th | Every component is strong. Zero chance of failing on camera. | Bengali in the demo — instead **measure it and publish the failure** (see `docs/LANGUAGES.md`) |

---

## 2. Repository layout

Create this exactly. Judges read `README.md` and `docs/REPRODUCE.md` and nothing else unless you impress them.

```
omnitalk-edge/
├── LICENSE                    # verbatim Apache-2.0, unmodified
├── NOTICE                     # model licences + "Built with Llama"
├── README.md                  # hero GIF, results table, 5-min quickstart
├── .gitmodules                # llama.cpp, whisper.cpp pinned to a commit
├── docs/
│   ├── ARCHITECTURE.md        # pipeline diagram + thread/core map
│   ├── OPTIMIZATION.md        # O1..O6: method, code pointer, before/after
│   ├── BENCHMARKS.md          # full tables, device info, methodology
│   ├── LANGUAGES.md           # measured per-language capability matrix
│   └── REPRODUCE.md           # "run this on YOUR Arm phone in 10 minutes"
├── bench/
│   ├── otbench.sh             # the adb sweep
│   ├── analyze.py             # CSV -> markdown + matplotlib PNGs
│   └── results/               # committed CSVs + PNGs + device_info.txt
├── native/
│   ├── CMakeLists.txt
│   └── otjni.cpp              # ONE JNI surface: asr, llm, grammar, affinity, timers
├── android/
│   └── app/src/main/java/dev/omnitalk/
│       ├── MainActivity.kt
│       ├── Pipeline.kt        # O4 lives here
│       ├── AgentFsm.kt        # slot-filling state machine
│       ├── Tts.kt
│       ├── Trace.kt           # stage timestamps -> exportable JSON
│       └── BenchScreen.kt
├── assets/
│   ├── grammars/agent.gbnf
│   └── prompts/               # "prompt assets" — literally named in the rubric
│       ├── translate.md
│       ├── agent_system.md
│       └── summarize.md
└── scripts/
    ├── fetch_models.sh        # download + sha256 verify (NEVER commit weights)
    └── build_android.sh
```

---

## 3. Schedule

Nine blocks. Times are Asia/Dhaka.

| Block | When | Owner | Output |
|---|---|---|---|
| **B0** Toolchain | Aug 11, tonight, 2–3 h | BUILD | NDK + adb working, phone connected |
| **B1** Risk kill | Aug 11–12, 2 h | BUILD | **A token generated on the phone.** 80 % of risk gone. |
| **B2** `otbench` | Aug 12 AM, 4 h | BUILD | O1/O2/O3 measured, CSV committed |
| **B3** Whisper on device | Aug 12 PM, 3 h | BUILD | RTF measured, thread/affinity chosen |
| **B4** App skeleton + **naive baseline** | Aug 12 PM/EVE, 5 h | BUILD | Serial pipeline works. **Baseline trace captured.** |
| **B5** O4 + O5 | Aug 13 AM, 5 h | BUILD | Overlapped pipeline, KV reuse, before/after trace |
| **B6** Agent FSM + GBNF (O6) | Aug 13 PM, 4 h | BUILD | Agent Mode works, JSON validity measured |
| **B7** **DRAFT SUBMIT** + docs | Aug 13 EVE, 3 h | DOC | Repo public, Devpost draft saved |
| **B8** Final bench + charts + docs | Aug 14 AM, 5 h | both | All tables and PNGs final |
| **B9** Video + final submit | Aug 14 PM, 4 h | DOC | Submitted with ≥ 6 h to spare |

**Non-negotiable:** B7 happens on Aug 13 even if the app is ugly. Devpost lets you edit after submitting. An imperfect submitted project beats a perfect unsubmitted one.

---

## B0 — Toolchain (Aug 11 tonight, 2–3 h) · `[BUILD]`

Host is Windows x86-64, currently missing everything. Downloads are multi-GB — **start them now, in parallel, before anything else.**

```powershell
winget install -e --id Google.AndroidStudio
winget install -e --id Kitware.CMake
winget install -e --id Git.Git
```

Then in Android Studio → **SDK Manager → SDK Tools**, tick and install:
- **NDK (Side by side)** — 27.x or newer
- **CMake**
- **Android SDK Platform-Tools**

Set env vars (adjust the NDK version to what actually installed):

```powershell
[Environment]::SetEnvironmentVariable("ANDROID_HOME","$env:LOCALAPPDATA\Android\Sdk","User")
$ndk = (Get-ChildItem "$env:LOCALAPPDATA\Android\Sdk\ndk" | Sort-Object Name -Descending | Select-Object -First 1).FullName
[Environment]::SetEnvironmentVariable("ANDROID_NDK","$ndk","User")
[Environment]::SetEnvironmentVariable("Path","$env:Path;$env:LOCALAPPDATA\Android\Sdk\platform-tools","User")
```

On the phone: **Settings → About → tap Build number 7×** → Developer options → **USB debugging ON**. Plug in, accept the RSA prompt.

```powershell
adb devices          # must list your device as "device", not "unauthorized"
```

> **Exit criterion:** `adb devices` shows the phone. `$env:ANDROID_NDK` points at a real directory.
> **Fallback:** if USB debugging is blocked, use `adb tcpip 5555` over Wi-Fi, or install Termux from F-Droid and build on-device (slower but works).

---

## B1 — Risk kill: a token on the phone (Aug 11–12, 2 h) · `[BUILD]`

This is the step your own `Arm.md` correctly identified as removing 80 % of the technical risk. **Nothing else starts until this passes.**

### B1.1 — Confirm the CPU story (this is data for the paper, not just a check)

```bash
adb shell "cat /proc/cpuinfo | grep -m1 Features"
# EXPECT: fp asimd ... asimddp ...   (asimddp = dotprod)
# EXPECT ABSENT: i8mm, sve, sme

adb shell "for c in /sys/devices/system/cpu/cpu*/cpufreq/cpuinfo_max_freq; do echo \$c \$(cat \$c); done"
# The 2 highest-frequency entries are your A78 big cores.
# If they are cpu6 and cpu7 -> big-core mask = 0xC0. RECORD THE ACTUAL MASK.

adb shell getprop ro.product.model
adb shell getprop ro.board.platform
adb shell "cat /proc/meminfo | head -3"
```

Save all of this to `bench/results/device_info.txt`. It goes in the README.

### B1.2 — Build llama.cpp for arm64

```bash
git clone --depth 1 https://github.com/ggml-org/llama.cpp
cd llama.cpp

cmake -B build-android \
  -DCMAKE_TOOLCHAIN_FILE="$ANDROID_NDK/build/cmake/android.toolchain.cmake" \
  -DANDROID_ABI=arm64-v8a \
  -DANDROID_PLATFORM=android-28 \
  -DCMAKE_BUILD_TYPE=Release \
  -DBUILD_SHARED_LIBS=OFF \
  -DGGML_CPU_KLEIDIAI=ON \
  -DGGML_OPENMP=OFF \
  -DGGML_LLAMAFILE=OFF \
  -DLLAMA_CURL=OFF

cmake --build build-android -j
```

> ### ⚠️ The one trap that will waste your whole night
> The official `docs/android.md` example passes `-DCMAKE_C_FLAGS="-march=armv8.7a"`. **Do not copy that.** Your Cortex-A78 is Armv8.2-A; an armv8.7 build can emit instructions the chip does not have and you will get **`SIGILL` (signal 4, illegal instruction)** at runtime, which looks exactly like a mysterious crash.
>
> Build with **no explicit `-march`** first (GGML dispatches on runtime CPU feature detection). If you hit SIGILL, pin it explicitly:
> `-DCMAKE_C_FLAGS="-march=armv8.2-a+dotprod+fp16" -DCMAKE_CXX_FLAGS="-march=armv8.2-a+dotprod+fp16"`
>
> `BUILD_SHARED_LIBS=OFF` gives static binaries so `adb push` is one file, not a library-path puzzle.

### B1.3 — Get the model (Q4_0, not Q4_K_M)

```bash
# from the host
curl -L -o Llama-3.2-1B-Instruct-Q4_0.gguf \
  "https://huggingface.co/bartowski/Llama-3.2-1B-Instruct-GGUF/resolve/main/Llama-3.2-1B-Instruct-Q4_0.gguf"

# also fetch Q4_K_M and Q8_0 — you need them as the O1/O2 comparison arms
curl -L -o Llama-3.2-1B-Instruct-Q4_K_M.gguf \
  "https://huggingface.co/bartowski/Llama-3.2-1B-Instruct-GGUF/resolve/main/Llama-3.2-1B-Instruct-Q4_K_M.gguf"
curl -L -o Llama-3.2-1B-Instruct-Q8_0.gguf \
  "https://huggingface.co/bartowski/Llama-3.2-1B-Instruct-GGUF/resolve/main/Llama-3.2-1B-Instruct-Q8_0.gguf"
```

If a filename 404s, list the repo's files first — quant filenames occasionally change case or suffix.

### B1.4 — Run it

```bash
adb shell mkdir -p /data/local/tmp/ot
adb push build-android/bin/llama-cli build-android/bin/llama-bench /data/local/tmp/ot/
adb push Llama-3.2-1B-Instruct-Q4_0.gguf /data/local/tmp/ot/
adb shell chmod +x /data/local/tmp/ot/llama-*

adb shell "cd /data/local/tmp/ot && ./llama-cli -m Llama-3.2-1B-Instruct-Q4_0.gguf \
  -p 'Translate to Spanish: Where is the bus station?' -n 48 -t 4 --no-cnv"
```

> **Exit criterion:** Spanish text appears in your terminal, generated on the phone. **Screenshot this — it is the first asset of your submission.**
> **Fallback if SIGILL:** rebuild with the explicit `-march=armv8.2-a+dotprod+fp16`.
> **Fallback if OOM/killed:** drop to `-c 1024`.
> **Fallback if the whole build fails:** install Termux on the phone and build natively there. Slower, but it is a guaranteed path and the same binaries result.

---

## B2 — `otbench`: the measurements that *are* the submission (Aug 12 AM, 4 h) · `[BUILD]`

Build the harness before the app. If the app never ships, this alone is a credible Mobile-AI-track submission.

### B2.1 — Two builds, one flag apart

You need a KleidiAI-OFF build to attribute O2 honestly.

```bash
cmake -B build-android-nokleidi \
  -DCMAKE_TOOLCHAIN_FILE="$ANDROID_NDK/build/cmake/android.toolchain.cmake" \
  -DANDROID_ABI=arm64-v8a -DANDROID_PLATFORM=android-28 \
  -DCMAKE_BUILD_TYPE=Release -DBUILD_SHARED_LIBS=OFF \
  -DGGML_CPU_KLEIDIAI=OFF -DGGML_OPENMP=OFF -DGGML_LLAMAFILE=OFF -DLLAMA_CURL=OFF
cmake --build build-android-nokleidi -j

adb push build-android-nokleidi/bin/llama-bench /data/local/tmp/ot/llama-bench-nokleidi
adb shell chmod +x /data/local/tmp/ot/llama-bench-nokleidi
```

### B2.2 — The sweep

`bench/otbench.sh` — run each cell 3× and keep the median. `llama-bench` already reports mean ± stddev over repetitions; use `-r 3`.

Sweep axes:
- **quant** ∈ {Q4_0, Q4_K_M, Q8_0} → **O1**
- **kleidi** ∈ {on, off} → **O2**
- **threads** ∈ {1, 2, 3, 4, 6, 8} × **affinity** ∈ {all cores, big only (`0xC0`), little only (`0x3F`)} → **O3**

```bash
for M in Q4_0 Q4_K_M Q8_0; do
  for BIN in llama-bench llama-bench-nokleidi; do
    for T in 1 2 3 4 6 8; do
      adb shell "cd /data/local/tmp/ot && ./$BIN \
        -m Llama-3.2-1B-Instruct-$M.gguf -t $T -p 256 -n 128 -r 3 -o csv"
    done
  done
done
```

For affinity, `llama-bench`/`llama-cli` expose `-C/--cpu-mask` and `--cpu-strict` — **check `./llama-bench --help` on device for the exact flag names in the commit you pinned**, they have moved before. If unavailable, wrap with `taskset`:

```bash
adb shell "cd /data/local/tmp/ot && taskset c0 ./llama-bench -m Llama-3.2-1B-Instruct-Q4_0.gguf -t 2 -p 256 -n 128 -r 3 -o csv"
```

### B2.3 — Capture the smoking gun for O1

Run with GGML logging visible and grep for the KleidiAI fallback warning that fires when a weight type has no KleidiAI microkernel:

```bash
adb shell "cd /data/local/tmp/ot && ./llama-cli -m Llama-3.2-1B-Instruct-Q4_K_M.gguf -p hi -n 1 --no-cnv 2>&1" | grep -i kleidi
```

**A screenshot of that warning next to the Q4_0 run with no warning is the single most persuasive image in your whole submission.** It proves the finding rather than asserting it.

### B2.4 — Thermals

Run a 10-minute sustained decode and sample temperature and clocks every 5 s:

```bash
adb shell "for i in \$(seq 1 120); do \
  echo \$(date +%s) \$(cat /sys/class/thermal/thermal_zone0/temp) \
  \$(cat /sys/devices/system/cpu/cpu7/cpufreq/scaling_cur_freq); sleep 5; done" > bench/results/thermal.txt
```

> **Exit criterion:** `bench/results/sweep.csv` committed, with a clear winner for (quant, kleidi, threads, affinity). You now know the optimal runtime config — **hard-code it as the app's default and say so in the README.**
> **Expected shape of results** (predictions, not promises — record what you actually get): Q4_0+KleidiAI beats Q4_K_M on prefill; 2–4 threads on big cores beats 8 threads across all cores; Q8_0 is ~2× the size for modest quality gain.

---

## B3 — Whisper on device (Aug 12 PM, 3 h) · `[BUILD]`

```bash
git clone --depth 1 https://github.com/ggml-org/whisper.cpp
cd whisper.cpp
cmake -B build-android \
  -DCMAKE_TOOLCHAIN_FILE="$ANDROID_NDK/build/cmake/android.toolchain.cmake" \
  -DANDROID_ABI=arm64-v8a -DANDROID_PLATFORM=android-28 \
  -DCMAKE_BUILD_TYPE=Release -DBUILD_SHARED_LIBS=OFF -DGGML_OPENMP=OFF
cmake --build build-android -j

curl -L -o ggml-base-q5_1.bin \
  "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-base-q5_1.bin"
curl -L -o ggml-tiny-q5_1.bin \
  "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-tiny-q5_1.bin"

adb push build-android/bin/whisper-cli ggml-base-q5_1.bin ggml-tiny-q5_1.bin /data/local/tmp/ot/
adb shell chmod +x /data/local/tmp/ot/whisper-cli
```

Record a 10 s test clip, convert to **16 kHz mono 16-bit WAV** (whisper.cpp requires this), push, and measure:

```bash
adb shell "cd /data/local/tmp/ot && ./whisper-cli -m ggml-base-q5_1.bin -f test16k.wav -t 4 -l es"
```

Measure **RTF = processing_time / audio_duration** for {tiny, base} × {1,2,4,6 threads} × {big mask, little mask}.

**Key experiment for O4:** run Whisper pinned to the **little cores** (`taskset 3f`) *simultaneously* with a llama.cpp decode pinned to the **big cores** (`taskset c0`) and confirm neither collapses. This is the empirical basis for the overlapped pipeline.

### The Bengali measurement (30 min, `[DOC]` can do this) — do not skip

Record 10 short Bengali utterances. Transcribe with tiny and base. Compute WER against your own reference transcripts. Put the number in `docs/LANGUAGES.md` with the honest conclusion. **Publishing a negative result you measured yourself is a credibility multiplier** — it shows you make evidence-based engineering decisions instead of shipping something that half-works.

> **Exit criterion:** you know your ASR model, thread count and core mask, and RTF < 0.7 for Spanish.
> **Fallback:** if base is too slow, use tiny and note the quality cost.

---

## B4 — App skeleton + the naive baseline (Aug 12 PM/EVE, 5 h) · `[BUILD]`

Fork the structure of `llama.cpp/examples/llama.android` into `android/`. Add whisper via a second JNI entry point in **one** `native/otjni.cpp` — do not build two separate JNI libraries.

JNI surface (keep it this small):

```cpp
// native/otjni.cpp
jlong  ot_llm_load(env, path, n_ctx, n_threads, cpu_mask);
jstring ot_llm_generate(env, handle, prompt, max_tokens, grammar_or_null);
void   ot_llm_reset_kv(env, handle);
jlong  ot_asr_load(env, path, n_threads, cpu_mask);
jstring ot_asr_transcribe(env, handle, float_pcm16k, lang);
void   ot_set_affinity(env, cpu_mask);         // sched_setaffinity on the calling thread
jstring ot_last_timings(env, handle);          // JSON: prefill_ms, decode_ms, tok/s, n_prefill
```

UI: one screen, big **push-to-talk** button, a transcript list, a mode toggle (Intercom / Agent), and a settings sheet exposing threads + core mask + language (judges love a visible knob they can turn).

**Build the naive serial pipeline first, and instrument it:**

```
[record] → [ASR full clip] → [LLM prefill] → [LLM decode all] → [TTS synthesize] → [play]
```

`Trace.kt` stamps `System.nanoTime()` at every stage boundary and can export the run as JSON.

> **Exit criterion:** you speak English, the phone speaks Spanish. **Export and commit `bench/results/trace_naive.json`.** You cannot claim O4 later without this baseline — capture it *before* you optimize. Also record a screen capture of the naive run for the video's before/after.
> **Fallback if Android TTS has no offline Spanish voice:** Settings → System → Languages → Text-to-speech → install voice data. If it still fails, fall back to on-screen text output and demo Agent Mode's *summary*, which is text anyway.

---

## B5 — O4 (overlapped pipeline) + O5 (KV reuse) (Aug 13 AM, 5 h) · `[BUILD]`

This block produces your headline number. Budget the most time here.

### O4 — three overlaps

1. **Chunked ASR during recording.** Feed Whisper 2-second audio chunks from the mic ring buffer as they arrive, pinned to the **little cores**. By the time the user releases the button, most of the audio is already transcribed.
2. **Speculative prefill.** As soon as a partial transcript is stable, start LLM prefill on `system_prompt + history + partial` on the **big cores**. If the final transcript extends it, only the delta needs prefilling (this composes with O5).
3. **Sentence-chunked TTS.** Stream decode; the moment a `.`/`?`/`!`/`।` boundary appears, hand that sentence to `TextToSpeech.speak(..., QUEUE_ADD, ...)`. Audio starts while the model is still generating.

**The metric to report: `end_of_speech → first_audio_sample_out`.** Not total time. This is what a human in a market actually experiences.

### O5 — KV cache reuse

The naive agent rebuilds `system + turn1 + turn2 + ... + turnN` and prefills all of it every turn. Instead keep the `llama_context` alive across turns and prefill only the new tokens. Log `n_prefill_tokens` per turn and put the per-turn series in `docs/OPTIMIZATION.md` — the drop from turn 1 to turn 3 is dramatic and easy to chart.

Watch the context ceiling: at `n_ctx=2048` a long conversation will fill up. Implement a simple policy — keep the system prompt + FSM state + last N turns, drop the middle — and document it.

> **Exit criterion:** `bench/results/trace_optimized.json` committed, and a before/after table with a real speedup on end-of-speech→first-audio.
> **Fallback:** if speculative prefill is unstable, ship overlaps 1 and 3 only. They are still a large win and much simpler.

---

## B6 — Agent Mode + GBNF (O6) (Aug 13 PM, 4 h) · `[BUILD]`

The crown jewel, made reliable by grammar constraints.

### The FSM

An objective is a set of **slots**. The model's only job each turn is to (a) fill slots from what it just heard, and (b) produce the next question. That is a small enough job for a 1 B model — *if* the output is machine-parseable, which is what the grammar guarantees.

```
Objective: "bus to Cox's Bazar — departure time, air conditioning, price"
Slots: { departure_times: null, has_ac: null, price: null }

loop:
  ask(next_unfilled_slot_question)   -> TTS in target language
  listen()                           -> ASR
  update = llm(state, reply, GRAMMAR)-> guaranteed-valid JSON
  merge(update)
  if all slots filled or turns > 6: break

summarize() -> English summary screen
```

### `assets/grammars/agent.gbnf`

Constrain sampling so malformed JSON is *structurally impossible*:

```gbnf
root        ::= "{" ws "\"slots\"" ws ":" ws slots ws "," ws "\"next_question\"" ws ":" ws string ws "," ws "\"done\"" ws ":" ws bool ws "}"
slots       ::= "{" ws (pair (ws "," ws pair)*)? ws "}"
pair        ::= string ws ":" ws (string | "null")
string      ::= "\"" ([^"\\] | "\\" .)* "\""
bool        ::= "true" | "false"
ws          ::= [ \t\n]*
```

Pass it through `ot_llm_generate`'s `grammar` argument (llama.cpp: `llama_sampler_init_grammar`).

### Measure O6 — this is a scoreable result

Run the same 100 agent turns **with** and **without** the grammar. Count JSON parse failures. Report:

| | Parse failures / 100 | Avg tokens emitted |
|---|---|---|
| Ungrammared 1B | *measure* | *measure* |
| GBNF-constrained | *expect 0* | *measure* |

That is "improve output quality for a given model size" — a named judging category — demonstrated with a hard number.

Ship **3 built-in objectives** (bus ticket, pharmacy, market price) plus a free-text objective box.

> **Exit criterion:** a full 3-turn agent conversation completes and shows an English summary.
> **Fallback:** if multi-turn is flaky, ship a **2-turn** agent. Two turns still demonstrates the loop. Do not ship something that fails on camera.

---

## B7 — DRAFT SUBMIT (Aug 13 EVE, 3 h) · `[DOC]` — **NON-NEGOTIABLE**

1. Push the repo **public** on GitHub.
2. Add `LICENSE` — **verbatim Apache-2.0 text, unmodified.** Confirm GitHub's About sidebar shows "Apache-2.0". If it says "Other", you have failed a stated requirement.
3. Add `NOTICE`: Llama 3.2 Community Licence + **"Built with Llama"**, Whisper MIT, llama.cpp MIT, whisper.cpp MIT.
4. Write `README.md` v1 — even a skeleton with the B2 numbers already in it.
5. **Create the Devpost submission and save it as a draft with the repo URL.** Devpost allows edits until the deadline.
6. Verify commit history is spread across Aug 11–14 (rules ask you to document work done during the submission period). Do not squash.

> **Exit criterion:** a Devpost draft exists with a public repo URL. If everything catches fire after this point, you still have a submission.

---

## B8 — Final benchmarks, charts, docs (Aug 14 AM, 5 h) · both

`[BUILD]`: re-run the full sweep on the final binaries, phone at **>80 % battery, plugged out, airplane mode, screen at fixed brightness, 5-minute cooldown between cells** (state this methodology in `docs/BENCHMARKS.md` — rigour is visible and cheap).

`[DOC]`: `bench/analyze.py` → `matplotlib` → commit PNGs. Charts to produce:

1. **The money chart** — horizontal bars, naive → +O1 → +O2 → +O3 → +O5 → +O4, showing end-of-speech→first-audio falling at each step.
2. Prefill & decode tok/s by quant × KleidiAI on/off.
3. tok/s vs thread count, one line per affinity mask (the "8 threads is slower than 4" chart — counterintuitive charts get remembered).
4. RSS over a full agent conversation, with the 6 GB device's practical ceiling drawn as a line.
5. Thermal curve over 10 minutes.

Write `docs/OPTIMIZATION.md` with one section per O#: **what was slow → why, on this microarchitecture → what we changed → the number → link to the code**.

Write `docs/REPRODUCE.md` as a 10-minute path for a judge with any Arm phone. Make it work for a device they own, not just yours.

---

## B9 — Video and final submit (Aug 14 PM, 4 h) · `[DOC]`

Script in `02-SUBMISSION.md`. Hard rules: **≤ 3:00**, real device, **no speed-ups during inference**, no copyrighted music, no third-party trademarks on screen.

Submit by **Aug 15 ~00:00 Dhaka** (Aug 14 ~14:00 PT) — two hours of margin against Devpost's deadline traffic.

---

## 4. Risk register

| Risk | P | Impact | Mitigation | Trigger to fall back |
|---|---|---|---|---|
| `SIGILL` from over-wide `-march` | High | Blocks everything | Build with no `-march`; else pin `armv8.2-a+dotprod+fp16` | First crash in B1 |
| NDK/CMake install eats hours | Med | Blocks B1 | Start downloads B0, tonight | Not done by Aug 12 06:00 → Termux path |
| llama.cpp flag names moved (`--cpu-mask`) | Med | Blocks O3 | Pin a submodule commit; read `--help` on device | Flag missing → use `taskset` |
| Android TTS lacks offline voice | Med | Kills audio out | Install voice data in B4 | Fails → text output + subtitles in video |
| 1 B model too weak for the agent | Med | Kills Agent Mode | GBNF grammar + tight slot prompts | 2-turn agent, or scripted-objective mode |
| LMK kills the app | Low-Med | Kills demo | `n_ctx=2048`, one model class at a time, monitor RSS | Drop to `n_ctx=1024` |
| Thermal throttling mid-demo | Med | Slow demo footage | Cool phone before shooting; shoot the demo first, benchmarks after | — |
| Time overrun | **High** | Missed deadline | B7 draft submit on Aug 13 | — |

**Scope kill order when behind** (cut top-down): Piper TTS → OCR (already cut) → free-text objectives → speculative prefill (O4.2) → third built-in objective → Live Intercom mode → down to 2-turn agent. **Never cut:** B2 benchmarks, B7 draft submit, the video.

---

## 5. Definition of done

- [ ] Public GitHub repo, About sidebar reads **Apache-2.0**
- [ ] `README.md` opens with a GIF and the headline before/after number in the first screenful
- [ ] `docs/REPRODUCE.md` works on a phone that is not yours
- [ ] `bench/results/*.csv` + PNGs committed; `otbench.sh` runs unattended
- [ ] O1–O6 each have a number, a method, and a code link
- [ ] `docs/LANGUAGES.md` includes the measured Bengali negative result
- [ ] `NOTICE` present; **"Built with Llama"** in README and app About; **no model weights committed**
- [ ] Video ≤ 3:00, public, real-time footage, no music/trademarks
- [ ] Devpost submitted with ≥ 6 h margin
- [ ] Commit history spans Aug 11–14

---

## 6. Stretch goals — **do not start any of these until B9 is complete**

- **P3a** Piper/sherpa-onnx TTS with a KleidiAI-accelerated ONNX Runtime build (would add a 7th measured optimization)
- **P3b** ML Kit OCR feeding the same translation pipeline (~2 h; label it clearly as platform-provided, not your optimization)
- **P3c** `simpleperf` flamegraph showing time inside KleidiAI microkernels
- **P3d** A second device's benchmark column (borrow any other Android phone — a two-device table showing the mid-tier gap is very strong)
