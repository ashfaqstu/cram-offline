# OmniTalk Edge — Complete Implementation Specification

> **This document is the only source of truth.** Follow it top to bottom. Every decision has already been made. If something here conflicts with `00-VERDICT.md`, `01-PLAN.md` or `02-SUBMISSION.md`, **this file wins** — those are the reasoning; this is the build.
>
> **Track progress in `PROGRESS.md` only.** Do not create other tracking files.

---

## PART 0 — How to use this document

**If you are an AI agent:** execute phases in order. Never skip a `⛔ GATE`. After each task, tick its box in `PROGRESS.md`. If a gate fails, go to that phase's *Fallback* and continue — do not stop, do not invent new scope.

**If you are a human:** same rules. The document assumes you know Kotlin and C++ basics and nothing about this project.

**Who does what.** You hold a Poco M2 Pro; your friend is remote with the faster, more reliable Narzo 50 Pro.

- **You own the entire build.** Every phase, every gate, every build-measure-tune cycle runs on the Poco over local adb. Never wait on a remote device during development — a twenty-iteration loop cannot survive it.
- **The Narzo produces the published numbers and the video** (Phase 5 final sweep, Phase 7). See the scheduling risk in Part 2.1 — resolve it today.
- **Your friend's jobs:** run the final `otbench` sweep or the in-app Benchmark screen; shoot the video if you cannot get the phone; second pair of eyes on the README before submit.

Because both phones are 2 big + 6 LITTLE Armv8.2-A, **anything tuned on the Poco is correct on the Narzo.** Only the numbers change, never the code.

**Conventions**
- `[N]` = native/C++ work · `[A]` = Android/Kotlin · `[B]` = benchmarks · `[D]` = docs/video
- `⛔ GATE` = do not proceed until this passes
- `⚠️ TRAP` = a known way to lose hours
- `🔍 VERIFY` = check reality before trusting this document (APIs drift)

**Timeline.** Deadline **2026-08-14 16:00 PT = 2026-08-15 05:00 Dhaka**. Phases 0–3 must be done by end of Aug 12. Phase 9 (draft submit) happens Aug 13 regardless of state.

---

## PART 1 — The product

### 1.1 One sentence

**OmniTalk Edge is an offline AI agent that holds a goal-directed conversation in a language you don't speak, on a mid-range Arm phone, in airplane mode — and shows you the Arm CPU doing it, live.**

### 1.2 Why this wins (grounded in last year's results)

Arm's inaugural challenge drew 142 submissions and 6 winners. Arm's own write-up named the qualities it rewarded: **privacy-first fully-offline workflows, performance-per-watt efficiency, real-world functionality in low-connectivity environments, creative on-device AI.** The 2nd-prize project was an offline geo-query engine for humanitarian workers on a Pi 5, pitched explicitly at places "where infrastructure fails."

This year the rubric changed: it is now the **Optimization** challenge, and the brief demands "measurable improvements." So the winning shape is:

> **a genuinely useful offline product + rigorous measured optimization + a demo a judge understands in 30 seconds.**

We hit all three, and we add one thing nobody else will: **the optimization is visible and interactive inside the app itself.**

### 1.3 The three features that make it 10/10

| | Feature | Why it exists |
|---|---|---|
| **F1** | **Agent Mode** — you give a goal, the app runs the whole conversation in the foreign language, tracks what it still needs, asks its own follow-ups, hands back an English summary | The product. The WOW. |
| **F2** | **Live Core HUD** — a real-time strip showing all 8 CPU cores (2 big / 6 LITTLE), lighting up to show ASR on the LITTLE cluster and the LLM on the big cluster, plus a per-stage latency waterfall | **The single best idea in this build.** It makes the optimization *visible* to an Arm judge during the demo. Fuses the app and the benchmark into one image. |
| **F3** | **The Turbo switch** — a toggle in the app header: `NAIVE ⇄ OPTIMIZED`. Flipping it re-runs the same pipeline with all optimizations off. The judge feels the 3× difference themselves in 5 seconds | Converts a claim into an experience. Enormous UX/DX score. Costs almost nothing — both code paths already exist. |

Plus **F4 Live Intercom** (plain push-to-talk speech-to-speech) — free once the pipeline exists, gives judges an easy thing to try first.

### 1.4 The comprehension problem, and its solution

**A judge does not speak Spanish.** If the screen shows only Spanish, they cannot tell whether the agent is working. Everything below exists to solve this:

- Every utterance is shown **twice**: the target language, and an English gloss directly under it in muted type.
- The **Objective Board** shows the goal's slots as a checklist, filling in live with a tick and the extracted value as the conversation proceeds. A judge watching slots go `? → ✓ 1500 BDT` understands the agent instantly, in any language.
- The **turn counter** shows `Turn 2 of max 6` so the loop is legible.
- The final **Summary card** is always English.

> **Design rule for the whole app: a judge who speaks only English, watching on mute, must understand what is happening.** Reject any UI decision that violates this.

### 1.5 Screen inventory (exactly four — build no others)

**S1 — Home**
- Big language pair selector: `EN → ES` (tap to swap/change)
- Two large buttons: **Agent Mode**, **Live Intercom**
- Status strip: `● OFFLINE · airplane mode` (green when the device really has no connectivity — read it, don't fake it), model names, RAM in use
- Small link: **Benchmark** (S4)

**S2 — Agent Mode**
```
┌────────────────────────────────────────────┐
│ ✈ OFFLINE      OmniTalk        [TURBO ⚡ON] │  ← F3 toggle
├────────────────────────────────────────────┤
│ OBJECTIVE                                  │
│ "Bus to Cox's Bazar: time, AC, price"      │
│  ✓ departure   8:00, 12:00, 22:00          │  ← F1/comprehension
│  ✓ has AC      only 22:00                  │
│  ○ price       —                           │
│  Turn 2 of 6                               │
├────────────────────────────────────────────┤
│ 🤖 "¿Alguno tiene aire acondicionado?"     │
│    Does any of them have AC?               │  ← English gloss
│                                            │
│ 👤 "Sólo el de las diez."                  │
│    Only the ten o'clock one.               │
├────────────────────────────────────────────┤
│ ▓▓░░░░░░  big0 big1 │ L0 L1 L2 L3 L4 L5    │  ← F2 Core HUD
│ ASR 380ms │ PREFILL 210ms │ DECODE 640ms   │
│ FIRST AUDIO 1.24 s                         │
├────────────────────────────────────────────┤
│         [  HOLD TO SPEAK  ]                │
└────────────────────────────────────────────┘
```

**S3 — Summary** — English summary card, full transcript with glosses, **Share as text**, and a `Run stats` block (turns, total time, peak RSS, energy per turn).

**S4 — Benchmark** — in-app harness. Buttons: `Quick (30 s)` / `Full (5 min)`. Shows the results table live and offers **Export JSON**. This is what a judge taps when they want to verify you.

### 1.6 The three built-in objectives (ship exactly these + free text)

1. **Bus ticket** — slots: `departure_times`, `has_ac`, `price`
2. **Pharmacy** — slots: `has_medicine`, `price`, `dosage_instructions`
3. **Market haggle** — slots: `asking_price`, `best_price`, `accepts_offer`

---

## PART 2 — Hardware contract

### 2.1 Two devices, one architecture class

**This is a gift, not a complication.** Both phones are 2 big + 6 LITTLE Armv8.2-A with dotprod and no i8mm. Every optimization, every core mask, and the entire thesis transfer between them unchanged — and having two vendors turns a single-device claim into a **cross-vendor result**, which is far stronger evidence.

| | **DEV / TEST** | **HERO** |
|---|---|---|
| Device | Poco M2 Pro (2020) | **Realme Narzo 50 Pro 5G** (2021) |
| Owner | **You — in hand, full adb** | Friend — remote |
| SoC | Snapdragon 720G (Qualcomm, 8 nm) | Dimensity 920 (MediaTek, 6 nm) |
| Big cluster | 2× **Cortex-A76** @ 2.3 GHz | 2× **Cortex-A78** @ ~2.5 GHz |
| LITTLE cluster | 6× Cortex-A55 @ 1.8 GHz | 6× Cortex-A55 @ ~2.0 GHz |
| Arch | **Armv8.2-A** · `asimddp` ✅ · `i8mm` ❌ · SVE/SME ❌ | **Armv8.2-A** · same |
| RAM | 4 GB **or** 6 GB — ⚠️ **VERIFY** | 6 GB |
| Role | **Every build-measure-tune cycle.** All gates, all development, the naive/turbo traces. | **Headline numbers, the money chart, the video.** Second benchmark column. |

**Develop on the Poco, publish on the Narzo.** The dev loop needs a phone on your desk; the submission needs the faster, more reliable device. Because both are 2 big + 6 LITTLE Armv8.2-A, code tuned on one is correct on the other — nothing needs re-engineering, only re-measuring.

**Publish both columns regardless.** A result confirmed on Qualcomm *and* MediaTek silicon is far harder to dismiss than a single-phone claim, and it costs you one extra CSV. The Poco being slower is useful evidence, not an embarrassment: it shows the optimizations matter *more* as hardware gets weaker.

> ⚠️ **SCHEDULING RISK — decide this today, not on Aug 14.** The headline benchmarks **and** the video both need the Narzo, and both land on Aug 14. The friend is remote. Either:
> - **(a)** you get the Narzo in hand by Aug 13 evening — cleanest, and it lets you shoot the video yourself; or
> - **(b)** your friend runs the final `otbench` sweep *and* shoots the video, following the shot list in Part 12, with a second person to play the shopkeeper.
>
> If neither is certain by **Aug 13 midday**, fall back: **ship the Poco as the hero device** and use whatever Narzo data arrives as the second column. A video you actually have beats a faster phone you don't.

### 2.2 Design constants (verified in Phase 1)

| Property | Value | Design consequence |
|---|---|---|
| Big cluster | 2 cores | LLM lives here. **Only 2 threads.** |
| LITTLE cluster | 6 cores | ASR lives here. 4 threads (leave 2 for UI/OS). |
| ISA | `asimddp` yes, **no i8mm/SVE/SME2** | Never claim i8mm. Q4_0 + KleidiAI dotprod is the ceiling. |
| Core numbering | expected `cpu0–5` LITTLE, `cpu6–7` big → big `0xC0`, LITTLE `0x3F` | **VERIFY per device in Phase 1.** Masks may differ between the two phones — record both. |

> ⚠️ **RAM check, do this first.** `adb shell cat /proc/meminfo | head -1`. If the Poco is the **4 GB** variant, drop `n_ctx` to **1024** (KV cache ~65 MB) and keep total app RSS under **~1.1 GB**. If it is 6 GB, use `n_ctx = 2048` as specified. Record which in `PROGRESS.md`.
>
> On a 4 GB device also watch for **mmap eviction**: the 770 MB of weights live in the page cache, and under memory pressure Android can evict them, forcing a re-read from UFS mid-generation and causing a visible stall. If you see erratic decode speed on the 4 GB variant, that is the cause — close background apps and reboot before demoing.

### 2.3 One APK, any Arm phone — detect the topology at runtime

**Do not hard-code `0xC0`.** The same APK must run on both phones and on whatever a judge owns, which is also what makes `docs/REPRODUCE.md` credible. Detect the clusters on first launch by reading max frequencies and grouping:

```kotlin
data class Topology(val bigMask: Long, val littleMask: Long, val nBig: Int, val nLittle: Int)

fun detectTopology(): Topology {
    val n = Runtime.getRuntime().availableProcessors()
    val freq = (0 until n).map { i ->
        runCatching {
            File("/sys/devices/system/cpu/cpu$i/cpufreq/cpuinfo_max_freq").readText().trim().toLong()
        }.getOrDefault(0L)
    }
    val maxF = freq.maxOrNull() ?: 0L
    // "big" = within 15% of the fastest core. Handles 2+6, 4+4, and 1+3+4 layouts.
    var big = 0L; var little = 0L; var nb = 0; var nl = 0
    freq.forEachIndexed { i, f ->
        if (maxF > 0 && f >= maxF * 0.85) { big = big or (1L shl i); nb++ }
        else                              { little = little or (1L shl i); nl++ }
    }
    if (nb == 0 || nb == n) {            // uniform SoC or unreadable cpufreq
        return Topology(-1L, -1L, n, n)  // -1 = "no pinning", use all cores
    }
    return Topology(big, little, nb, nl)
}
```

Then `llmThreads = nBig` and `asrThreads = max(2, nLittle - 2)`. Show the detected layout on the Home screen — a judge seeing *"detected 2× big @ 2.30 GHz, 6× LITTLE @ 1.80 GHz"* on their own phone is a strong DX signal, and it costs you nothing.

### 2.4 Honest latency budget — plan against these, not Arm's flagship figures

Decode is memory-bandwidth bound: ~770 MB of Q4_0 weights against LPDDR4X. Prefill runs on two dotprod cores with no i8mm.

| | Poco (A76 @ 2.3) | Narzo (A78 @ 2.5) |
|---|---|---|
| Decode | ~7–10 tok/s | ~9–13 tok/s |
| Prefill | ~40–60 tok/s | ~55–75 tok/s |

| Stage | Naive | With O4/O5/O8/O9 |
|---|---|---|
| ASR residual after end-of-speech | ~1.0–1.2 s | ~0.3 s (chunked on LITTLE during capture) |
| Prefill | ~250 tok ⇒ **4–6 s** | ~40 tok ⇒ **0.3–0.5 s** (pre-warm + KV reuse + speculative) |
| Decode to first spoken word | full object ~60 tok ⇒ **5–7 s** | `"q"` first, ~12 tok ⇒ **1.2–1.5 s** |
| TTS start | ~0.3 s | ~0.2 s |
| **end-of-speech → first audio** | **≈ 11–14 s** | **≈ 2.0–2.5 s** |

**~2 s is a natural conversational pause — perfectly usable.** Note what the modest hardware does for the story: the naive path is *unusable* at 12 s, so the ~6× improvement reads as **necessary engineering**, not polish. On a flagship the naive path would be ~3 s and the same work would look like a nice-to-have. Do not chase 1.5 s; measure the honest number and show the gap.

Publishing both devices also gives you a real finding: **the weaker the silicon, the larger the win.** That is a genuinely useful conclusion for anyone deploying to the mid-tier install base.

**Memory budget (must hold):**

| Item | Budget |
|---|---|
| Llama-3.2-1B-Instruct Q4_0 (mmap) | ~770 MB |
| KV cache @ `n_ctx = 2048` | ~130 MB |
| whisper base q5_1 + state | ~130 MB |
| Audio ring buffer (30 s @ 16 kHz f32) | ~2 MB |
| Compose UI + ART heap | ~300 MB |
| **Total** | **~1.33 GB** ✅ |

---

### 2.5 On-device storage budget

Check first: `adb shell df -h /data /sdcard`

| Artefact | Size | Where | Needed for |
|---|---|---|---|
| `llama-cli`, `llama-bench`, `llama-bench-nokleidi`, `whisper-cli` | ~40 MB | `/data/local/tmp/ot` | Phases 1–2 |
| `Llama-3.2-1B-Instruct-Q4_0.gguf` | **~770 MB** | `/data/local/tmp/ot` | everything |
| `Llama-3.2-1B-Instruct-Q4_K_M.gguf` | **~810 MB** | `/data/local/tmp/ot` | **O1** — the headline finding |
| `Llama-3.2-1B-Instruct-Q8_0.gguf` | **~1.32 GB** | `/data/local/tmp/ot` | O1 third arm — **optional, skip if tight** |
| `ggml-base-q5_1.bin` | ~60 MB | `/data/local/tmp/ot` | ASR |
| `ggml-tiny-q5_1.bin` | ~31 MB | `/data/local/tmp/ot` | ASR fallback |
| APK | ~40–60 MB | app | Phase 3+ |
| App's own copy of Q4_0 + whisper base | **~830 MB** | app `filesDir` | Phase 3+ |

> ⚠️ The CLI benchmarks read from `/data/local/tmp/ot`, which the app **cannot** read — shell-owned, restrictive permissions. So during Phases 3–5 you hold **two copies** of the weights. Budget for it.

**Peak:** ~3.9 GB with all three quants · ~2.6 GB skipping Q8_0.
**Steady state after Phase 5:** ~900 MB (app copy only).
**Recommended free space before starting: 5 GB.** Never fill the phone — Android needs headroom for cache and updates, and a full `/data` causes weird failures that look like bugs in your code.

**If space is tight, sequence it:**
1. Push Q4_0 + Q4_K_M + whisper, run the Phase 2 sweep, **commit the CSVs**.
2. `adb shell rm /data/local/tmp/ot/*Q4_K_M*` — the numbers are captured, the file is not needed again.
3. Install the app and import Q4_0 + whisper base.
4. Skip Q8_0 entirely unless you have ≥ 6 GB free. O1's finding is Q4_0 vs Q4_K_M; Q8_0 only adds a size/quality data point.

**Import flow, to avoid a duplicate:** the app copies from `/sdcard/Download/` into `filesDir`, then **deletes the Download copy**. Without that delete you carry an extra 830 MB for nothing.

## PART 3 — Architecture: HetPipe

### 3.1 The idea in one paragraph

A 2+6 core split is unusual and extreme. Naive inference gives every thread to one model and wastes 75 % of the cores. **HetPipe treats the two clusters as two independent compute pools and keeps both busy**: audio is transcribed in 2-second chunks on the LITTLE cluster *while the user is still speaking*, the LLM prefills the partial transcript on the big cluster *before they finish*, and each finished sentence is spoken *while the model is still decoding the next one*. This is not a library flag — it is a scheduler designed for this specific silicon.

### 3.2 Naive vs HetPipe

```
NAIVE (what everyone ships)                    time ──►
 record ████████
                ASR ██████
                          prefill ███
                                     decode ████████
                                                     TTS ███
                                                            play ▶
 end-of-speech ──────────────────────────────────────────► first audio   ≈ 4.5 s

HETPIPE (ours)
 record ████████
   LITTLE  ASR ▓▓ ▓▓ ▓▓ ▓▓ (chunked, concurrent)
   BIG          prefill ▓▓▓▓▓ (speculative, on partial)
   BIG                  decode ████
   TTS                    ▶ sentence 1 ▶ sentence 2
 end-of-speech ──────► first audio                                       ≈ 1.3 s
```

### 3.3 Thread and core allocation (the contract)

| Worker | Cores | Threads | Set by |
|---|---|---|---|
| Audio capture | any | 1 | Android `AudioRecord` |
| ASR (whisper) | LITTLE `0x3F` | 4 | affinity set on its dispatcher thread **before** `whisper_init` |
| LLM (llama) | big `0xC0` | 2 | affinity set on its dispatcher thread **before** `llama_model_load` |
| TTS | any | — | Android `TextToSpeech` |
| UI | any | 1 | Compose main |

> ⚠️ **TRAP — the affinity mechanism.** GGML spawns its own worker threads. Setting affinity on a thread *after* the pool exists does nothing. On Linux, **child threads inherit the creating thread's affinity mask**, so the rule is: **set the mask, then load the model, then only ever call inference from that same thread.** In Kotlin that means one dedicated single-thread dispatcher per model, and every native call for that model goes through it. This is enforced in `Pipeline.kt`.

### 3.4 State machine for a turn

```
IDLE
 └─ user presses HOLD ──► RECORDING
      ├─ every 2 s of audio → ASR queue (LITTLE)
      └─ ASR emits partial → if ≥ 8 new tokens, speculative prefill (BIG)
 └─ user releases ──────► FINALIZING
      ├─ flush last audio chunk through ASR
      ├─ prefill only the delta tokens (KV cache retained)
      └─ decode with GBNF grammar
            └─ on each sentence boundary → TTS.speak(QUEUE_ADD)   ◄── first audio here
 └─ decode done ────────► MERGE
      ├─ parse JSON → update slots
      └─ all slots filled OR turn > 6 ? → SUMMARY : speak next_question → IDLE
```

---

## PART 4 — Repository manifest

Create every file listed. `∅` = create empty/stub in Phase 2, fill later.

```
omnitalk-edge/
├── LICENSE                              Apache-2.0, VERBATIM, unmodified
├── NOTICE                               model licences + "Built with Llama"
├── README.md                            the judged document
├── .gitignore                           *.gguf, *.bin, build/, .gradle/
├── .gitmodules                          llama.cpp + whisper.cpp, pinned
├── PROGRESS.md                          the only tracker
├── docs/
│   ├── ARCHITECTURE.md                  HetPipe + core map diagram
│   ├── OPTIMIZATION.md                  O1..O7, one section each
│   ├── BENCHMARKS.md                    method + full tables
│   ├── LANGUAGES.md                     measured capability matrix
│   └── REPRODUCE.md                     judge-facing 10-minute path
├── bench/
│   ├── otbench.sh                       adb sweep → CSV
│   ├── analyze.py                       CSV → markdown + PNG
│   └── results/                         device_info.txt, *.csv, *.png
├── native/
│   ├── CMakeLists.txt
│   └── otjni.cpp                        the ONE JNI translation unit
├── android/
│   ├── settings.gradle.kts
│   ├── build.gradle.kts
│   └── app/
│       ├── build.gradle.kts
│       └── src/main/
│           ├── AndroidManifest.xml
│           ├── java/dev/omnitalk/
│           │   ├── MainActivity.kt      nav between S1..S4
│           │   ├── Native.kt            external fun declarations
│           │   ├── Pipeline.kt          ★ HetPipe lives here
│           │   ├── AgentFsm.kt          slots + turn logic
│           │   ├── Tts.kt               sentence-chunked TTS
│           │   ├── Audio.kt             AudioRecord → FloatArray ring
│           │   ├── Trace.kt             stage timestamps → JSON
│           │   ├── Energy.kt            BatteryManager sampling
│           │   ├── CoreHud.kt           ★ F2 the live core strip
│           │   ├── BenchScreen.kt       S4
│           │   └── ui/                  S1, S2, S3 composables
│           └── assets/
│               ├── grammars/agent.gbnf
│               └── prompts/*.txt
└── scripts/
    ├── fetch_models.sh                  download + sha256 (NEVER commit weights)
    └── build_android.sh
```

---

## PART 5 — Phase 0: environment `[N]` · 2–3 h · Aug 11 tonight

### ⚠️ Two constraints that shaped this setup — read before deviating

**1. Disk space is the binding constraint.** C: has ~9 GB free, D: has ~15 GB. The full toolchain plus repos, builds, Gradle caches and model weights needs roughly 18–20 GB. **Android Studio is therefore NOT installed** — it would cost ~4 GB and the build never needs it. Everything runs through the Android **command-line tools** + Gradle CLI. Edit Kotlin in VS Code. If space frees up later, Studio can be added, but it is not on the critical path.

**2. The system JDK is Java 26 — too new for the Android Gradle Plugin.** AGP supports JDK 17/21. **Temurin 21 is installed to `D:\dev\jdk-21` and `JAVA_HOME` points at it.** Gradle uses `JAVA_HOME`, so it will pick the right one even though `java` on `PATH` may still resolve to 26. Do not "fix" `PATH` by removing the system Java — just leave `JAVA_HOME` alone.

### Installed layout (everything on D:)

| Path | What |
|---|---|
| `D:\dev\jdk-21` | Temurin JDK 21 — `JAVA_HOME` |
| `D:\Android\Sdk` | SDK root — `ANDROID_HOME`, `ANDROID_SDK_ROOT` |
| `D:\Android\Sdk\cmdline-tools\latest` | `sdkmanager`, `avdmanager` |
| `D:\Android\Sdk\platform-tools` | **`adb`** |
| `D:\Android\Sdk\ndk\27.3.13750724` | NDK — `ANDROID_NDK`, `ANDROID_NDK_HOME` |
| `D:\Android\Sdk\cmake\3.31.6\bin` | host CMake for the standalone llama.cpp/whisper.cpp builds |
| `D:\Android\Sdk\cmake\3.22.1` | the version AGP expects for the in-app native build |
| `D:\dev\gradle` | `GRADLE_USER_HOME` — keeps the Gradle cache off C: |
| `D:\omnitalk` | **clone the repo here**, not on C: |

### If you need to reinstall from scratch

```powershell
New-Item -ItemType Directory -Force -Path 'D:\dev','D:\Android\Sdk','D:\omnitalk'
winget install -e --id EclipseAdoptium.Temurin.21.JDK --location 'D:\dev\jdk-21' `
  --accept-package-agreements --accept-source-agreements --disable-interactivity

# command-line tools
Invoke-WebRequest 'https://dl.google.com/android/repository/commandlinetools-win-13114758_latest.zip' -OutFile 'D:\dev\clt.zip'
Expand-Archive 'D:\dev\clt.zip' 'D:\dev\clt-tmp' -Force
New-Item -ItemType Directory -Force -Path 'D:\Android\Sdk\cmdline-tools'
Move-Item 'D:\dev\clt-tmp\cmdline-tools' 'D:\Android\Sdk\cmdline-tools\latest'
```

Then run `D:\dev\install-sdk.ps1`, which accepts the licences non-interactively (piping a file of `y` lines — `sdkmanager --licenses` reads stdin, and the PowerShell tool runs with stdin closed, so a here-string will not work) and installs `platform-tools`, `platforms;android-34`, `build-tools;34.0.0`, `cmake;3.22.1`, `cmake;3.31.6`, `ndk;27.3.13750724`.

### Environment variables (already set at User scope)

```powershell
[Environment]::SetEnvironmentVariable('JAVA_HOME','D:\dev\jdk-21','User')
[Environment]::SetEnvironmentVariable('ANDROID_HOME','D:\Android\Sdk','User')
[Environment]::SetEnvironmentVariable('ANDROID_SDK_ROOT','D:\Android\Sdk','User')
[Environment]::SetEnvironmentVariable('ANDROID_NDK','D:\Android\Sdk\ndk\27.3.13750724','User')
[Environment]::SetEnvironmentVariable('ANDROID_NDK_HOME','D:\Android\Sdk\ndk\27.3.13750724','User')
[Environment]::SetEnvironmentVariable('GRADLE_USER_HOME','D:\dev\gradle','User')
# PATH gains: D:\dev\jdk-21\bin, D:\Android\Sdk\platform-tools,
#             D:\Android\Sdk\cmdline-tools\latest\bin, D:\Android\Sdk\cmake\3.31.6\bin
```

> **Open a new terminal before continuing** — a shell started before these were set will not see them.

### Bash-tool note

The build commands in Phase 1 use `$ANDROID_NDK` in Git Bash. Windows env vars are visible there, but the value is a Windows path with backslashes. If CMake complains, use the forward-slash form: `ANDROID_NDK="D:/Android/Sdk/ndk/27.3.13750724"`.

### Disk budget — check before each big download

```powershell
(Get-CimInstance Win32_LogicalDisk -Filter "DeviceID='D:'").FreeSpace/1GB
```
Approximate remaining costs: llama.cpp + whisper.cpp clones and builds ~3 GB · model weights ~1.7 GB (Q4_0 + Q4_K_M + whisper base/tiny) · Gradle cache ~1.5 GB. **`Q8_0` (1.3 GB) is optional — skip it unless space allows; it is only a secondary comparison arm for O1.**

Phone: **Settings → About → tap Build number ×7 → Developer options → USB debugging ON**. Plug in, accept the RSA fingerprint prompt.

```powershell
adb devices    # must show "<serial>  device"
```

**Also now:** install the Spanish offline TTS voice — **Settings → System → Languages & input → Text-to-speech output → Google TTS → Install voice data → Español**. Then **turn on airplane mode and confirm it still speaks.** If it needs the network, that is a Phase 5 problem you want to know about tonight.

> ⛔ **GATE 0** — `adb devices` lists the phone AND Spanish TTS speaks in airplane mode.
> **Fallback:** USB blocked → `adb tcpip 5555` over Wi-Fi. TTS offline fails → try Samsung/Vivo built-in engine, else see Phase 5 fallback.

---

## PART 6 — Phase 1: risk kill `[N]` · 2 h · ⛔ NOTHING ELSE STARTS UNTIL THIS PASSES

### 6.1 Read the silicon (this is data for the paper, not just a check)

```bash
adb shell "cat /proc/cpuinfo | grep -m1 Features" | tee -a device_info.txt
# EXPECT present: asimddp        EXPECT absent: i8mm, sve, sme

adb shell "for i in 0 1 2 3 4 5 6 7; do echo -n \"cpu\$i \"; cat /sys/devices/system/cpu/cpu\$i/cpufreq/cpuinfo_max_freq; done"
# The two HIGHEST-frequency cores are the A78s.
# If they are cpu6,cpu7 → BIG_MASK=0xC0  LITTLE_MASK=0x3F
# ⚠️ If they are cpu0,cpu1 → BIG_MASK=0x03  LITTLE_MASK=0xFC — UPDATE EVERY MASK IN THIS DOC.

adb shell getprop ro.product.model; adb shell getprop ro.build.version.release
adb shell "cat /proc/meminfo | head -3"
```
Save all output to `bench/results/device_info.txt`. It goes in the README verbatim.

### 6.2 Build llama.cpp

```bash
git clone https://github.com/ggml-org/llama.cpp && cd llama.cpp
git rev-parse HEAD    # ← RECORD THIS COMMIT. Pin it. Never float master mid-hackathon.

cmake -B build-android \
  -DCMAKE_TOOLCHAIN_FILE="$ANDROID_NDK/build/cmake/android.toolchain.cmake" \
  -DANDROID_ABI=arm64-v8a -DANDROID_PLATFORM=android-28 \
  -DCMAKE_BUILD_TYPE=Release -DBUILD_SHARED_LIBS=OFF \
  -DGGML_CPU_KLEIDIAI=ON -DGGML_OPENMP=OFF -DGGML_LLAMAFILE=OFF -DLLAMA_CURL=OFF
cmake --build build-android -j
```

> ⚠️ **TRAP — the SIGILL trap.** The official `docs/android.md` example passes `-DCMAKE_C_FLAGS="-march=armv8.7a"`. **Do not copy it.** Cortex-A76/A78 are Armv8.2-A; an armv8.7 build emits instructions the chip does not have and dies with **signal 4 (SIGILL)** that looks like a mystery crash. Build with no explicit `-march` first. If it still SIGILLs, pin:
> `-DCMAKE_C_FLAGS="-march=armv8.2-a+dotprod+fp16" -DCMAKE_CXX_FLAGS="-march=armv8.2-a+dotprod+fp16"`

> ### ⚠️ TRAP — the default build will fill your disk (hit for real, Aug 12)
> `cmake --build build-android` with no `--target` builds **520 targets** — every example, tool, test and the server. Each is statically linked *and* the NDK toolchain adds `-g`, so `llama-cli` came out at **196 MB** and `llama-bench` at **120 MB**. The two llama.cpp build trees reached **8 GB and 4.4 GB**, filled the disk, and linking died with:
>
> ```
> LLVM ERROR: IO failure on output stream: No space left on device
> PLEASE submit a bug report to https://github.com/android-ndk/ndk/issues
> ```
>
> That message reads like an NDK bug. **It is a full disk.** Always build with an explicit target list and strip:
>
> ```
> -DLLAMA_BUILD_TESTS=OFF -DLLAMA_BUILD_EXAMPLES=OFF -DLLAMA_BUILD_SERVER=OFF
> -DCMAKE_C_FLAGS=-g0 -DCMAKE_CXX_FLAGS=-g0 -DCMAKE_EXE_LINKER_FLAGS=-s
> cmake --build build-android -j 6 --target llama-cli llama-bench
> ```
>
> `-g0` stops debug info being generated, `-s` strips at link, and capping parallelism at `-j 6` avoids an unbounded link fan-out spiking disk use. `scripts/build_native.ps1` does all of this — use it rather than hand-rolling the commands.

> ### ⚠️ TRAP — `LLAMA_BUILD_SERVER=OFF` deletes `llama-cli` (hit for real, Aug 12)
> In this revision, `tools/CMakeLists.txt` puts the CLI inside the server block:
> ```cmake
> if (LLAMA_BUILD_SERVER)
>     add_subdirectory(ui)
>     add_subdirectory(cli)
>     add_subdirectory(server)
> endif()
> ```
> So turning the server off to save build time silently removes the target and ninja fails with **`unknown target 'llama-cli', did you mean 'llama-app'?`** — which reads like the binary was renamed. It was not; it was never configured.
>
> **Fix:** keep `-DLLAMA_BUILD_SERVER=ON` for the build that needs `llama-cli`. It only affects *configure*; ninja still builds nothing beyond the targets you name, so you do not pay for the server or the web UI. The KleidiAI-OFF control build needs only `llama-bench`, so leave the server off there.
>
> **General lesson:** after any configure, verify your targets exist before assuming a name — `ninja -C <builddir> -t targets | grep <name>`.

> ### 🚨 TRAP — always pass `-c`. Omitting it can take the phone down.
> **Llama 3.2's trained context is 131,072 tokens.** With no `-c`, llama.cpp sizes the KV cache for the model's full context — many gigabytes on a phone whose `MemAvailable` is ~1.7 GB. The result is not a clean error: the Low Memory Killer starts killing *other* apps, the device thrashes on swap, and the whole phone feels broken while you stare at a hung `llama-cli`.
>
> **Every llama invocation in this project passes `-c 2048`** (or `-c 512` for a one-token probe). Treat a missing `-c` as a bug in review. `n_ctx = 2048` gives ~130 MB of KV cache, which is the figure the memory budget in Part 2 is built on.
>
> Related device courtesy, all implemented in `scripts/phase1_device.ps1`:
> - one model resident at a time; each measurement is a separate short-lived process
> - a 5–10 s cooldown between runs, both for thermals and to let the page cache settle
> - `MemAvailable` logged before and after each stage, so a leak or eviction is visible
> - `pkill -f llama- ; pkill -f whisper-` at the end, so nothing is left running
>
> The phone is a shared, borrowed resource *and* the demo device. Do not leave it wedged.

Also build the **KleidiAI-OFF** variant now — you need it for O2 and it costs one command:

```bash
cmake -B build-android-nokleidi \
  -DCMAKE_TOOLCHAIN_FILE="$ANDROID_NDK/build/cmake/android.toolchain.cmake" \
  -DANDROID_ABI=arm64-v8a -DANDROID_PLATFORM=android-28 \
  -DCMAKE_BUILD_TYPE=Release -DBUILD_SHARED_LIBS=OFF \
  -DGGML_CPU_KLEIDIAI=OFF -DGGML_OPENMP=OFF -DGGML_LLAMAFILE=OFF -DLLAMA_CURL=OFF
cmake --build build-android-nokleidi -j
```

### 6.3 Models

```bash
B=https://huggingface.co/bartowski/Llama-3.2-1B-Instruct-GGUF/resolve/main
curl -L -o Llama-3.2-1B-Instruct-Q4_0.gguf   "$B/Llama-3.2-1B-Instruct-Q4_0.gguf"
curl -L -o Llama-3.2-1B-Instruct-Q4_K_M.gguf "$B/Llama-3.2-1B-Instruct-Q4_K_M.gguf"   # O1 comparison arm
curl -L -o Llama-3.2-1B-Instruct-Q8_0.gguf   "$B/Llama-3.2-1B-Instruct-Q8_0.gguf"     # O1 comparison arm

W=https://huggingface.co/ggerganov/whisper.cpp/resolve/main
curl -L -o ggml-base-q5_1.bin "$W/ggml-base-q5_1.bin"
curl -L -o ggml-tiny-q5_1.bin "$W/ggml-tiny-q5_1.bin"
```
If a URL 404s, open the repo's file list in a browser — quant filenames occasionally change case.

### 6.4 First token on the phone

```bash
adb shell mkdir -p /data/local/tmp/ot
adb push build-android/bin/llama-cli build-android/bin/llama-bench /data/local/tmp/ot/
adb push build-android-nokleidi/bin/llama-bench /data/local/tmp/ot/llama-bench-nokleidi
adb push Llama-3.2-1B-Instruct-Q4_0.gguf /data/local/tmp/ot/
adb shell chmod +x /data/local/tmp/ot/llama-*

adb shell "cd /data/local/tmp/ot && ./llama-cli -m Llama-3.2-1B-Instruct-Q4_0.gguf \
  -p 'Translate to Spanish: Where is the bus station?' -n 48 -t 2 -C c0 --cpu-strict 1 --no-cnv"
```

> ⛔ **GATE 1** — Spanish text appears, generated on the phone. **Screenshot it.** 80 % of technical risk is now gone.
> **Fallback:** SIGILL → explicit `-march`. `-C` flag unknown → drop it for now, `taskset c0 ./llama-cli ...`. OOM → `-c 1024`. Total build failure → install Termux (F-Droid) and build on-device.

### 6.5 Build whisper.cpp

```bash
git clone https://github.com/ggml-org/whisper.cpp && cd whisper.cpp
git rev-parse HEAD   # ← pin this too
cmake -B build-android \
  -DCMAKE_TOOLCHAIN_FILE="$ANDROID_NDK/build/cmake/android.toolchain.cmake" \
  -DANDROID_ABI=arm64-v8a -DANDROID_PLATFORM=android-28 \
  -DCMAKE_BUILD_TYPE=Release -DBUILD_SHARED_LIBS=OFF -DGGML_OPENMP=OFF
cmake --build build-android -j
adb push build-android/bin/whisper-cli ggml-base-q5_1.bin ggml-tiny-q5_1.bin /data/local/tmp/ot/
adb shell chmod +x /data/local/tmp/ot/whisper-cli
```

Record ~10 s of Spanish speech, convert to **16 kHz mono 16-bit WAV** (whisper.cpp accepts nothing else), push, run:

```bash
adb shell "cd /data/local/tmp/ot && taskset 3f ./whisper-cli -m ggml-base-q5_1.bin -f test16k.wav -t 4 -l es"
```

> ⛔ **GATE 2** — Spanish transcript appears, and **RTF = processing_time ÷ audio_duration < 0.7**.
> **Fallback:** RTF too high → use `ggml-tiny-q5_1.bin` and note the quality cost in `docs/LANGUAGES.md`.

### 6.6 The concurrency proof (this validates HetPipe before you build it)

Run both at once, on different clusters, and confirm neither collapses:

```bash
adb shell "cd /data/local/tmp/ot && taskset 3f ./whisper-cli -m ggml-base-q5_1.bin -f test16k.wav -t 4 -l es" &
adb shell "cd /data/local/tmp/ot && taskset c0 ./llama-bench -m Llama-3.2-1B-Instruct-Q4_0.gguf -t 2 -p 128 -n 64 -r 2"
wait
```
Compare each one's solo numbers to its concurrent numbers.

> ⛔ **GATE 3** — concurrent LLM throughput is **≥ 80 %** of solo throughput.
> **Fallback:** if it collapses, HetPipe's overlap 2 (speculative prefill) is dropped; keep overlaps 1 and 3, which still win. Record the measurement either way — a negative result here is publishable content for `docs/OPTIMIZATION.md`.

---

## PART 7 — Phase 2: native layer `[N]` · 4 h

### 7.1 `native/CMakeLists.txt`

```cmake
cmake_minimum_required(VERSION 3.22)
project(otjni CXX C)

set(CMAKE_CXX_STANDARD 17)
set(LLAMA_DIR ${CMAKE_CURRENT_SOURCE_DIR}/../third_party/llama.cpp)
set(WHISPER_DIR ${CMAKE_CURRENT_SOURCE_DIR}/../third_party/whisper.cpp)

# both vendored as git submodules, pinned to the commits recorded in Phase 1
set(GGML_CPU_KLEIDIAI ON  CACHE BOOL "" FORCE)
set(GGML_OPENMP       OFF CACHE BOOL "" FORCE)
set(GGML_LLAMAFILE    OFF CACHE BOOL "" FORCE)
set(LLAMA_CURL        OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TOOLS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_TESTS    OFF CACHE BOOL "" FORCE)
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)

add_subdirectory(${LLAMA_DIR}   ${CMAKE_BINARY_DIR}/llama)
add_subdirectory(${WHISPER_DIR} ${CMAKE_BINARY_DIR}/whisper)

add_library(otjni SHARED otjni.cpp)
target_link_libraries(otjni llama whisper log android)
```

> ⚠️ **TRAP** — whisper.cpp and llama.cpp both vendor `ggml`. Building both as subdirectories can produce duplicate `ggml` targets. If CMake errors with a duplicate target, set `WHISPER_USE_SYSTEM_GGML=ON` (or the equivalent option in the pinned commit — `grep -n "GGML" third_party/whisper.cpp/CMakeLists.txt`). **Fallback that always works:** build whisper.cpp separately as a prebuilt static lib and `add_library(whisper STATIC IMPORTED)`.

### 7.2 `native/otjni.cpp`

```cpp
#include <jni.h>
#include <android/log.h>
#include <sched.h>
#include <unistd.h>
#include <string>
#include <vector>
#include <mutex>
#include <cstring>
#include "llama.h"
#include "whisper.h"

#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, "otjni", __VA_ARGS__)

// ── affinity ────────────────────────────────────────────────────────────────
// MUST be called on a thread BEFORE that thread loads a model. GGML worker
// threads inherit the creating thread's mask, which is how we pin a whole
// model to one cluster.
extern "C" JNIEXPORT jint JNICALL
Java_dev_omnitalk_Native_setAffinity(JNIEnv*, jobject, jlong mask) {
    cpu_set_t set; CPU_ZERO(&set);
    for (int i = 0; i < 8; ++i) if (mask & (1LL << i)) CPU_SET(i, &set);
    int rc = sched_setaffinity(gettid(), sizeof(set), &set);
    LOGI("setAffinity tid=%d mask=0x%llx rc=%d", gettid(), (unsigned long long)mask, rc);
    return rc;
}

// ── LLM ─────────────────────────────────────────────────────────────────────
struct LlmCtx {
    llama_model*   model = nullptr;
    llama_context* ctx   = nullptr;
    const llama_vocab* vocab = nullptr;
    int n_past = 0;              // O5: tokens already in the KV cache
    double last_prefill_ms = 0, last_decode_ms = 0;
    int last_prefill_tok = 0, last_decode_tok = 0;
    std::mutex mu;
};

static double now_ms() {
    timespec t; clock_gettime(CLOCK_MONOTONIC, &t);
    return t.tv_sec * 1e3 + t.tv_nsec / 1e6;
}

extern "C" JNIEXPORT jlong JNICALL
Java_dev_omnitalk_Native_llmLoad(JNIEnv* env, jobject, jstring jpath, jint n_ctx, jint n_threads) {
    static bool inited = false;
    if (!inited) { llama_backend_init(); inited = true; }

    const char* path = env->GetStringUTFChars(jpath, nullptr);
    auto* L = new LlmCtx();

    llama_model_params mp = llama_model_default_params();
    mp.use_mmap = true;                 // keeps RSS low — do not disable
    L->model = llama_model_load_from_file(path, mp);   // 🔍 see API DRIFT
    env->ReleaseStringUTFChars(jpath, path);
    if (!L->model) { delete L; return 0; }

    L->vocab = llama_model_get_vocab(L->model);        // 🔍 see API DRIFT

    llama_context_params cp = llama_context_default_params();
    cp.n_ctx       = n_ctx;
    cp.n_threads   = n_threads;
    cp.n_threads_batch = n_threads;
    cp.n_batch     = 256;
    L->ctx = llama_init_from_model(L->model, cp);      // 🔍 see API DRIFT
    if (!L->ctx) { llama_model_free(L->model); delete L; return 0; }
    return reinterpret_cast<jlong>(L);
}

static std::vector<llama_token> tokenize(LlmCtx* L, const std::string& s, bool add_bos) {
    int n = -llama_tokenize(L->vocab, s.c_str(), (int)s.size(), nullptr, 0, add_bos, true);
    std::vector<llama_token> out(n);
    llama_tokenize(L->vocab, s.c_str(), (int)s.size(), out.data(), n, add_bos, true);
    return out;
}

// O5: prefill ONLY the delta. Caller passes the *new* text since the last call.
extern "C" JNIEXPORT jint JNICALL
Java_dev_omnitalk_Native_llmPrefill(JNIEnv* env, jobject, jlong h, jstring jtext) {
    auto* L = reinterpret_cast<LlmCtx*>(h);
    std::lock_guard<std::mutex> lk(L->mu);
    const char* t = env->GetStringUTFChars(jtext, nullptr);
    auto toks = tokenize(L, t, L->n_past == 0);
    env->ReleaseStringUTFChars(jtext, t);

    double t0 = now_ms();
    for (size_t i = 0; i < toks.size(); i += 256) {
        int n = (int)std::min<size_t>(256, toks.size() - i);
        llama_batch b = llama_batch_get_one(toks.data() + i, n);
        if (llama_decode(L->ctx, b) != 0) return -1;
        L->n_past += n;
    }
    L->last_prefill_ms = now_ms() - t0;
    L->last_prefill_tok = (int)toks.size();
    return (jint)toks.size();
}

// Streams tokens back via cb.onToken(String). Grammar may be null.
extern "C" JNIEXPORT jstring JNICALL
Java_dev_omnitalk_Native_llmGenerate(JNIEnv* env, jobject, jlong h, jint max_tokens,
                                     jstring jgrammar, jobject cb) {
    auto* L = reinterpret_cast<LlmCtx*>(h);
    std::lock_guard<std::mutex> lk(L->mu);

    auto sp = llama_sampler_chain_default_params();
    llama_sampler* chain = llama_sampler_chain_init(sp);
    if (jgrammar) {                                     // O6
        const char* g = env->GetStringUTFChars(jgrammar, nullptr);
        llama_sampler_chain_add(chain, llama_sampler_init_grammar(L->vocab, g, "root"));
        env->ReleaseStringUTFChars(jgrammar, g);
    }
    llama_sampler_chain_add(chain, llama_sampler_init_top_k(40));
    llama_sampler_chain_add(chain, llama_sampler_init_temp(0.4f));
    llama_sampler_chain_add(chain, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));

    jclass cbc = cb ? env->GetObjectClass(cb) : nullptr;
    jmethodID onTok = cb ? env->GetMethodID(cbc, "onToken", "(Ljava/lang/String;)V") : nullptr;

    std::string out;
    double t0 = now_ms(); int n = 0;
    for (; n < max_tokens; ++n) {
        llama_token tok = llama_sampler_sample(chain, L->ctx, -1);
        if (llama_vocab_is_eog(L->vocab, tok)) break;   // 🔍 see API DRIFT
        char buf[256];
        int len = llama_token_to_piece(L->vocab, tok, buf, sizeof(buf), 0, true);
        std::string piece(buf, len > 0 ? len : 0);
        out += piece;
        if (onTok) {                                    // O4: stream for sentence-chunked TTS
            jstring js = env->NewStringUTF(piece.c_str());
            env->CallVoidMethod(cb, onTok, js);
            env->DeleteLocalRef(js);
        }
        llama_sampler_accept(chain, tok);
        llama_batch b = llama_batch_get_one(&tok, 1);
        if (llama_decode(L->ctx, b) != 0) break;
        L->n_past++;
    }
    L->last_decode_ms = now_ms() - t0;
    L->last_decode_tok = n;
    llama_sampler_free(chain);
    return env->NewStringUTF(out.c_str());
}

extern "C" JNIEXPORT void JNICALL
Java_dev_omnitalk_Native_llmResetKv(JNIEnv*, jobject, jlong h) {
    auto* L = reinterpret_cast<LlmCtx*>(h);
    std::lock_guard<std::mutex> lk(L->mu);
    llama_memory_clear(llama_get_memory(L->ctx), true);  // 🔍 see API DRIFT
    L->n_past = 0;
}

extern "C" JNIEXPORT jstring JNICALL
Java_dev_omnitalk_Native_llmTimings(JNIEnv* env, jobject, jlong h) {
    auto* L = reinterpret_cast<LlmCtx*>(h);
    char b[512];
    snprintf(b, sizeof(b),
      "{\"prefill_ms\":%.1f,\"prefill_tok\":%d,\"prefill_tps\":%.1f,"
      "\"decode_ms\":%.1f,\"decode_tok\":%d,\"decode_tps\":%.1f,\"n_past\":%d}",
      L->last_prefill_ms, L->last_prefill_tok,
      L->last_prefill_ms > 0 ? L->last_prefill_tok * 1000.0 / L->last_prefill_ms : 0.0,
      L->last_decode_ms, L->last_decode_tok,
      L->last_decode_ms > 0 ? L->last_decode_tok * 1000.0 / L->last_decode_ms : 0.0,
      L->n_past);
    return env->NewStringUTF(b);
}

// ── ASR ─────────────────────────────────────────────────────────────────────
extern "C" JNIEXPORT jlong JNICALL
Java_dev_omnitalk_Native_asrLoad(JNIEnv* env, jobject, jstring jpath) {
    const char* p = env->GetStringUTFChars(jpath, nullptr);
    whisper_context_params cp = whisper_context_default_params();
    cp.use_gpu = false;                       // CPU only — this is the whole point
    whisper_context* c = whisper_init_from_file_with_params(p, cp);
    env->ReleaseStringUTFChars(jpath, p);
    return reinterpret_cast<jlong>(c);
}

extern "C" JNIEXPORT jstring JNICALL
Java_dev_omnitalk_Native_asrTranscribe(JNIEnv* env, jobject, jlong h,
                                       jfloatArray jpcm, jstring jlang, jint n_threads) {
    auto* c = reinterpret_cast<whisper_context*>(h);
    jsize n = env->GetArrayLength(jpcm);
    std::vector<float> pcm(n);
    env->GetFloatArrayRegion(jpcm, 0, n, pcm.data());
    const char* lang = env->GetStringUTFChars(jlang, nullptr);

    whisper_full_params p = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    p.n_threads        = n_threads;
    p.language         = lang;
    p.translate        = false;
    p.print_progress   = false;
    p.print_realtime   = false;
    p.no_context       = true;      // chunked mode: each chunk stands alone
    p.single_segment   = false;
    p.suppress_blank   = true;

    std::string out;
    if (whisper_full(c, p, pcm.data(), (int)pcm.size()) == 0) {
        for (int i = 0; i < whisper_full_n_segments(c); ++i)
            out += whisper_full_get_segment_text(c, i);
    }
    env->ReleaseStringUTFChars(jlang, lang);
    return env->NewStringUTF(out.c_str());
}
```

### 7.3 🔍 API DRIFT CHECKLIST — do this before compiling

llama.cpp renames C-API symbols regularly. **Grep the pinned header and fix any mismatch.** Old → new pairs seen in the wild:

```bash
grep -nE "llama_model_load_from_file|llama_load_model_from_file" third_party/llama.cpp/include/llama.h
grep -nE "llama_init_from_model|llama_new_context_with_model"     third_party/llama.cpp/include/llama.h
grep -nE "llama_model_get_vocab|llama_n_vocab"                     third_party/llama.cpp/include/llama.h
grep -nE "llama_vocab_is_eog|llama_token_is_eog"                   third_party/llama.cpp/include/llama.h
grep -nE "llama_memory_clear|llama_kv_self_clear|llama_kv_cache_clear" third_party/llama.cpp/include/llama.h
grep -n  "llama_sampler_init_grammar"                              third_party/llama.cpp/include/llama.h
```
Use whichever name the header actually declares. **This 5-minute check prevents an hour of confusing build errors.**

> ⛔ **GATE 4** — `otjni.so` builds for `arm64-v8a` and a JNI smoke test generates text from Kotlin.

---

## PART 8 — Phase 3: Android app `[A]` · 8 h

### 8.1 Gradle essentials

`app/build.gradle.kts`: `minSdk 28`, `targetSdk 34`, `ndk { abiFilters += "arm64-v8a" }`, `externalNativeBuild { cmake { path = file("../../native/CMakeLists.txt") } }`, Compose BOM, `RECORD_AUDIO` permission in the manifest.

**Model files are not in the APK** (they are ~900 MB). `MainActivity` checks for them in `filesDir/models/` on first launch and, if missing, shows a one-tap **Import models** screen that copies from `/sdcard/Download/`. `scripts/fetch_models.sh` + one `adb push` puts them there.

### 8.2 `Native.kt`

```kotlin
package dev.omnitalk
object Native {
    init { System.loadLibrary("otjni") }
    external fun setAffinity(mask: Long): Int
    external fun llmLoad(path: String, nCtx: Int, nThreads: Int): Long
    external fun llmPrefill(h: Long, text: String): Int
    external fun llmGenerate(h: Long, maxTokens: Int, grammar: String?, cb: TokenCb?): String
    external fun llmResetKv(h: Long)
    external fun llmTimings(h: Long): String
    external fun asrLoad(path: String): Long
    external fun asrTranscribe(h: Long, pcm: FloatArray, lang: String, nThreads: Int): String
    interface TokenCb { fun onToken(piece: String) }
}
```

### 8.3 `Pipeline.kt` — ★ the core of the project

```kotlin
package dev.omnitalk

import kotlinx.coroutines.*
import java.util.concurrent.Executors

// ⚠️ Each model gets ONE dedicated thread. Affinity is set on that thread
// BEFORE the model loads, so GGML's worker threads inherit the cluster mask.
// Every native call for a model MUST go through its own dispatcher.
class Pipeline(private val cfg: Config) {

    data class Config(
        val bigMask: Long = 0xC0,      // VERIFIED IN PHASE 1
        val littleMask: Long = 0x3F,   // VERIFIED IN PHASE 1
        val llmThreads: Int = 2,
        val asrThreads: Int = 4,
        val nCtx: Int = 2048,
        val turbo: Boolean = true      // F3: false = NAIVE path
    )

    private val llmExec = Executors.newSingleThreadExecutor { Thread(it, "llm-big") }
    private val asrExec = Executors.newSingleThreadExecutor { Thread(it, "asr-little") }
    private val llmDisp = llmExec.asCoroutineDispatcher()
    private val asrDisp = asrExec.asCoroutineDispatcher()

    private var llm = 0L
    private var asr = 0L

    suspend fun load(llmPath: String, asrPath: String) = coroutineScope {
        val a = async(llmDisp) {
            if (cfg.turbo) Native.setAffinity(cfg.bigMask)     // O3 — before load
            llm = Native.llmLoad(llmPath, cfg.nCtx, if (cfg.turbo) cfg.llmThreads else 8)
        }
        val b = async(asrDisp) {
            if (cfg.turbo) Native.setAffinity(cfg.littleMask)  // O3 — before load
            asr = Native.asrLoad(asrPath)
        }
        a.await(); b.await()
    }

    /**
     * O4 — the overlapped turn.
     * @param audio     hot flow of 2-second PCM chunks (16 kHz mono f32) while the user holds
     * @param onPartial UI callback for live transcript
     * @param onSentence fires the instant a sentence is complete → TTS speaks it
     */
    suspend fun runTurn(
        audio: kotlinx.coroutines.flow.Flow<FloatArray>,
        promptPrefix: String,
        grammar: String?,
        lang: String,
        trace: Trace,
        onPartial: (String) -> Unit,
        onSentence: (String) -> Unit
    ): String = coroutineScope {

        val transcript = StringBuilder()
        var speculated = 0

        // ── OVERLAP 1: chunked ASR on the LITTLE cluster during capture ──────
        val asrJob = launch(asrDisp) {
            audio.collect { chunk ->
                trace.mark("asr_chunk_start")
                val piece = Native.asrTranscribe(asr, chunk, lang, cfg.asrThreads)
                transcript.append(piece)
                trace.mark("asr_chunk_end")
                onPartial(transcript.toString())

                // ── OVERLAP 2: speculative prefill on the BIG cluster ────────
                if (cfg.turbo) {
                    val text = transcript.toString()
                    if (text.length - speculated > 40) {          // ~8+ tokens of new text
                        val delta = text.substring(speculated)
                        speculated = text.length
                        launch(llmDisp) { Native.llmPrefill(llm, delta) }   // O5: delta only
                    }
                }
            }
        }
        asrJob.join()
        trace.mark("end_of_speech")

        // ── final prefill: only what speculation did not already cover ───────
        val full = promptPrefix + transcript.toString().substring(minOf(speculated, transcript.length))
        withContext(llmDisp) { Native.llmPrefill(llm, full) }
        trace.mark("prefill_done")

        // ── OVERLAP 3: sentence-chunked TTS while still decoding ─────────────
        val sb = StringBuilder(); val sentence = StringBuilder(); var firstAudio = false
        val result = withContext(llmDisp) {
            Native.llmGenerate(llm, 160, grammar, object : Native.TokenCb {
                override fun onToken(piece: String) {
                    sb.append(piece); sentence.append(piece)
                    if (cfg.turbo && piece.any { it in ".?!।\n" }) {
                        val s = sentence.toString().trim()
                        sentence.clear()
                        if (s.isNotEmpty()) {
                            if (!firstAudio) { trace.mark("first_audio"); firstAudio = true }
                            onSentence(s)
                        }
                    }
                }
            })
        }
        trace.mark("decode_done")
        // NAIVE path speaks only at the very end — this is the O4 baseline
        if (!cfg.turbo) { trace.mark("first_audio"); onSentence(result) }
        result
    }

    fun timings(): String = Native.llmTimings(llm)
    fun resetKv() = Native.llmResetKv(llm)          // between objectives, not between turns
    fun close() { llmExec.shutdown(); asrExec.shutdown() }
}
```

> **The `turbo` flag is F3 and the O3/O4/O5 baseline generator at the same time.** One boolean gives you the judge-facing toggle *and* the before/after measurements. Do not implement them separately.
>
> **In NAIVE mode:** no affinity (8 threads, all cores), no chunked ASR overlap (collect all audio then transcribe once), no speculative prefill, no sentence-chunked TTS, and `resetKv()` before every turn. That is the honest naive baseline every other submission ships.

### 8.4 `Trace.kt`

```kotlin
class Trace {
    private val marks = mutableListOf<Pair<String, Long>>()
    private val t0 = System.nanoTime()
    fun mark(name: String) { synchronized(marks) { marks += name to (System.nanoTime() - t0) } }
    fun ms(name: String) = marks.lastOrNull { it.first == name }?.second?.div(1_000_000.0)
    /** THE headline metric. */
    fun firstAudioLatency(): Double? {
        val e = ms("end_of_speech") ?: return null
        val f = ms("first_audio") ?: return null
        return f - e
    }
    fun toJson(): String = marks.joinToString(",", "{", "}") { "\"${it.first}\":${it.second / 1_000_000.0}" }
}
```

### 8.5 `Audio.kt`

`AudioRecord`, `MediaRecorder.AudioSource.VOICE_RECOGNITION`, **16 000 Hz, mono, `ENCODING_PCM_16BIT`**. Convert to `FloatArray` normalised to −1..1 (`sample / 32768f`). Emit a chunk every **2.0 s** (32 000 samples) as a `Flow<FloatArray>`, plus a final partial chunk on release.

> ⚠️ **TRAP** — whisper.cpp accepts **only** 16 kHz mono float32. Any other rate produces confident nonsense, not an error.

### 8.6 `Tts.kt`

```kotlin
class Tts(ctx: Context, private val onStart: () -> Unit) {
    private var tts: TextToSpeech? = null
    private var ready = false
    init { tts = TextToSpeech(ctx) { if (it == TextToSpeech.SUCCESS) ready = true } }
    fun setLang(tag: String) { tts?.language = Locale.forLanguageTag(tag) }
    /** QUEUE_ADD is what makes sentence-chunked playback continuous. */
    fun speak(text: String, first: Boolean) {
        if (!ready) return
        if (first) onStart()                      // trace "first_audio" fires HERE, not at synthesis
        tts?.speak(text, TextToSpeech.QUEUE_ADD, null, "ot-${System.nanoTime()}")
    }
    fun stop() { tts?.stop() }
}
```

> **Measurement honesty:** `first_audio` must be marked when playback *starts*, not when the sentence is handed to the engine. Use `setOnUtteranceProgressListener` and mark inside `onStart(utteranceId)`. A judge who checks this will respect it.

### 8.7 `CoreHud.kt` — ★ F2, the wow element

Sample `/proc/stat` every 250 ms on a background thread. Per CPU line `cpuN user nice system idle ...`, compute busy fraction between samples:

```kotlin
fun readCoreLoads(): FloatArray {  // returns 8 values in 0..1
    val out = FloatArray(8)
    File("/proc/stat").forEachLine { line ->
        if (!line.startsWith("cpu") || line.startsWith("cpu ")) return@forEachLine
        val p = line.split(Regex("\\s+"))
        val n = p[0].removePrefix("cpu").toIntOrNull() ?: return@forEachLine
        if (n !in 0..7) return@forEachLine
        val idle = p[4].toLong(); val total = p.drop(1).take(7).sumOf { it.toLong() }
        val dIdle = idle - prevIdle[n]; val dTot = total - prevTotal[n]
        prevIdle[n] = idle; prevTotal[n] = total
        out[n] = if (dTot > 0) (1f - dIdle.toFloat() / dTot).coerceIn(0f, 1f) else 0f
    }
    return out
}
```

Render with Compose `Canvas`: eight vertical bars, the two big cores wider and in the accent colour, the six LITTLE cores narrower and muted, each labelled `A78`/`A55`. Overlay the active stage name on whichever cluster is working.

> ⚠️ **TRAP** — some Android builds restrict per-core `/proc/stat` to the app's own usage or return zeros. **Test this on the phone in Phase 1.** If it returns nothing useful, fall back to a *stage-driven* HUD: light the big cluster while the LLM dispatcher is active and the LITTLE cluster while the ASR dispatcher is active. This is still truthful (you pinned them there) — just label it "stage activity", not "CPU load". **Do not fake load numbers.**

### 8.8 `Energy.kt`

```kotlin
// µA (sign convention varies by vendor — take abs and say so in BENCHMARKS.md)
val bm = ctx.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
val uA = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
val uV = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
          ?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0    // mV
// power_mW = |uA| / 1000 * uV / 1000 ; integrate over the turn → mJ per turn
```
Sample at 200 ms during a turn. Report **mJ per agent turn** and **mJ per generated token** — Arm explicitly praised performance-per-watt last year, and almost nobody measures it.

> ⛔ **GATE 5** — you speak English into the phone, it speaks Spanish back, the Core HUD moves, and `trace_naive.json` **and** `trace_turbo.json` are both exported. **Capture the naive trace before optimising further — you cannot reconstruct it later.**

---

## PART 9 — Phase 4: prompts and grammar `[A]` · 2 h

### 9.1 `assets/prompts/agent_system.txt`

> ⚠️ **Every prompt token costs ~25 ms of prefill on 2× Cortex-A76.** A 300-token system prompt is 7.5 seconds. Keep the static prefix under **~90 tokens** and put everything variable at the end so the static part can be pre-warmed (see 9.6). Resist the urge to add explanation — measure instead.

**Static prefix** (pre-warmed once per objective, never re-prefilled):

```
You speak {TARGET_LANG} to a local person for an English-speaking traveller.
Goal: {OBJECTIVE}
Reply with one JSON object: q = your next short question in {TARGET_LANG};
g = its English translation; s = facts you just learned, values short and in
English, copied never invented; d = true only when nothing is missing.
```

**Per-turn suffix** (the only thing prefilled each turn — usually 30–60 tokens):

```
Still needed: {MISSING_SLOTS}
Known: {KNOWN_SLOTS}
They said: "{HEARD}"
```

### 9.2 `assets/grammars/agent.gbnf` — O6 **and** O8

```gbnf
# FIELD ORDER IS A LATENCY OPTIMIZATION — DO NOT REORDER.
# "q" is emitted FIRST so TTS can start speaking after ~12 tokens
# instead of waiting for the whole object (~60 tokens).
# Short keys are deliberate: every saved token is ~125 ms of decode
# on 2× Cortex-A76.  q = next_question, g = English gloss, s = slots, d = done
root  ::= "{" ws "\"q\"" ws ":" ws str ws ","
              ws "\"g\"" ws ":" ws str ws ","
              ws "\"s\"" ws ":" ws obj ws ","
              ws "\"d\"" ws ":" ws bool ws "}"
obj   ::= "{" ws (pair (ws "," ws pair)*)? ws "}"
pair  ::= str ws ":" ws (str | "null")
str   ::= "\"" ([^"\\] | "\\" ["\\/bfnrt])* "\""
bool  ::= "true" | "false"
ws    ::= [ \t\n]*
```

> **O8 — schema latency design.** Two free wins that only exist because decoding is the bottleneck on this device:
>
> 1. **`q` first.** The token stream reaches the end of the question after ~12 tokens. `Pipeline.kt` detects the closing quote of `q` and hands it straight to TTS — audio starts while `s` and `d` are still being generated. Emitting `slots` first would delay first audio by ~45 tokens ≈ **5.6 s** on the Poco. This single reordering is worth more than every library flag combined.
> 2. **Single-letter keys.** `"next_question"` costs ~4 tokens every turn; `"q"` costs 1. Across four fields that is ~10 tokens ≈ **1.2 s** saved per turn, for free.
>
> The model never has to work out *which* slot is missing — `AgentFsm` computes that in Kotlin and states it in the prompt. So generating the question before the slot extraction costs no quality.
>
> **Measure O8 like everything else:** same 20 replies, long-keys-slots-first grammar vs this one, report mean output tokens and time-to-first-audio.

### 9.3 `assets/prompts/summarize.txt`

```
Summarise this completed conversation for the traveller, in English, in at most three
short sentences. State only facts that appear in the slots below. Do not add advice.

Objective: {OBJECTIVE}
Facts gathered: {SLOTS_JSON}
```

### 9.4 `AgentFsm.kt`

```kotlin
data class Objective(val title: String, val slots: LinkedHashMap<String, String?>)

class AgentFsm(private val obj: Objective, private val maxTurns: Int = 6) {
    var turn = 0; private set
    fun missing() = obj.slots.filterValues { it == null }.keys.toList()
    fun known()   = obj.slots.filterValues { it != null }
    fun merge(updates: Map<String, String?>) {
        updates.forEach { (k, v) ->
            // never overwrite a known fact with null; never accept an unknown key
            if (obj.slots.containsKey(k) && v != null && obj.slots[k] == null) obj.slots[k] = v
        }
        turn++
    }
    fun done() = obj.slots.values.all { it != null } || turn >= maxTurns
}
```

### 9.6 O9 — pre-warm the static prefix

The first turn otherwise pays for the whole system prompt. Prefill it during **dead time** — the moment the user picks an objective, while they are still reading the screen and reaching for the button:

```kotlin
// called from the objective picker, NOT from the turn loop
suspend fun prewarm(staticPrefix: String) = withContext(llmDisp) {
    Native.llmResetKv(llm)
    Native.llmPrefill(llm, staticPrefix)   // KV now holds the prefix; n_past > 0
}
```
After this, turn 1's prefill is only the per-turn suffix. Combined with O5, **every** turn prefills 30–60 tokens instead of 200–350. Measure it: log `prefill_tok` for turn 1 with and without pre-warming.

### 9.5 Prompt-quality gate — **do this before building the UI around it**

Run 20 realistic Spanish replies through the prompt on-device via `llama-cli` with `--grammar-file`. Fix the prompt until: valid JSON **20/20**, slot extraction correct **≥ 16/20**, `next_question` in Spanish and on-topic **≥ 18/20**.

> ⛔ **GATE 6** — thresholds met. **Fallback:** if slot extraction is weak, split into two smaller calls (extract-only, then ask-only). Two easy calls beat one hard call for a 1 B model, at the cost of ~200 ms.

---

## PART 10 — Phase 5: the seven optimizations `[B]` · measure each one

Each row becomes a chart bar, a table row, and a section of `docs/OPTIMIZATION.md` written as: **what was slow → why, on this microarchitecture → what we changed → the number → link to the code.**

> ### 🔴 REVISED Aug 13 after first measurements — read this before touching O1/O2
>
> **KleidiAI does not engage on this hardware at all.** Straight from the device:
>
> ```
> kleidiai: no compatible q4 kernels found for CPU features mask 1
> kleidiai: no compatible q8 kernels found for CPU features mask 1
> kleidiai: no compatible f32 kernels found for CPU features mask 1
> kleidiai: SME disabled
> kleidiai: no kernel for tensor type q6_K, not accelerated by KleidiAI
>           (kernels available for Q4_0 and Q8_0)
> ```
>
> KleidiAI's llama.cpp int4/int8 microkernels require **i8mm or SME**. On dotprod-only Armv8.2-A they never load, and GGML silently uses generic kernels. So the original O1 ("switch to Q4_0 and KleidiAI engages") is **false on this device**, and the original O2 ("KleidiAI ON vs OFF") will measure **zero difference**.
>
> **This is a better result than the one we planned for.** Reframe it as the project's headline finding:
>
> > Arm markets KleidiAI as CPU acceleration for on-device AI. On the Armv8.2-A phones that make up a large share of the real install base, it is inert — and it says so in a log line nobody reads. We measured where the cliff falls, and it falls right through the mid-tier.
>
> Report it as a **measured null result with the A/B to prove it**, not as a quote. Be precise about scope: the Q4_0/Q8_0 advice is correct *if* you have i8mm — state that, or an Arm engineer will (rightly) push back. Also note the secondary finding: even the "Q4_0" GGUF carries `q6_K` embedding/output tensors, so KleidiAI coverage would be partial even on i8mm hardware.
>
> All real speedups must now come from **our own scheduling and pipeline work**, which is a stronger submission for an optimization challenge than "we set a library flag."

| # | Change | How to measure | Status |
|---|---|---|---|
| **O1** | **The KleidiAI cliff.** Null result: `llama-bench` vs `llama-bench-nokleidi` on identical Q4_0 weights, plus the `no compatible q4 kernels` log. Also Q4_0 vs Q4_K_M showing no KleidiAI-driven gap. | sweep B in `otbench.ps1` | ✅ confirmed on device |
| **O2** | **Thread-count tuning + the big.LITTLE decode cliff.** 8 threads is *slower* than 6 for decode; prefill keeps scaling to 8. | sweep A, `-t 2/4/6/8` × affinity | ✅ measured: see below |
| **O3** | **Split prefill/decode threading** — `-t 6 -tb 8`, since the two phases have opposite optima. | `llama-cli` with `-t`/`-tb`, end-to-end turn timing | to measure |
| **O4** | **HetPipe overlap** — chunked ASR on LITTLE during capture, speculative prefill on big, sentence-chunked TTS. | in-app `turbo` on/off, `Trace.firstAudioLatency()`, 10 runs each | ✅ premise validated (GATE 3) |
| **O5** | **Phase-adaptive core allocation** — big-only while ASR still runs, all-core once it finishes. | trace per-phase thread counts, A/B | to measure |
| **O6** | **KV cache reuse across turns** | log `n_past` / `prefill_tok` per turn | to measure |
| **O7** | **GBNF grammar-constrained decoding** | 100 turns with/without, count JSON parse failures | to measure |
| **O8** | **Schema latency design** — `q` first, single-letter keys | verbose-schema grammar vs ours; output tokens + first-audio | **likely largest single win** |
| **O9** | **Static-prefix pre-warm** during objective selection | `prefill_tok` on turn 1, with/without | to measure |
| **O10** | **Energy per turn** | `Energy.kt`, turbo on/off | to measure |

### Measured on the Poco M2 Pro (Aug 13) — Q4_0, unpinned, `-p 128 -n 32 -r 2`

| threads | prefill pp128 t/s | decode tg32 t/s |
|---:|---:|---:|
| 2 | 12.82 | 8.77 |
| 4 | 15.98 | 10.04 |
| 6 | 19.01 | **11.03** ← decode optimum |
| 8 | **21.24** ← prefill optimum | **7.14** ← 35 % collapse |

**Two findings to write up.** (a) Adding the last two cores makes decode *35 % slower* — the fast cores stall on the slow ones at every layer barrier. That is the counterintuitive chart. (b) Prefill and decode have **opposite** thread optima, which is exactly what `-t` / `-tb` exist for.

This also **corrects an assumption in Part 3**: decode is not fastest on the big cluster alone (8.74 t/s at 2 big cores) — 6 threads across both clusters wins (11.03 t/s). HetPipe still holds, because GATE 3 showed ASR-on-LITTLE costs the LLM nothing (12.91/8.72 concurrent vs 12.89/8.74 solo, **99.8 %**), but the schedule becomes: **big-only during the overlap window, all-core once ASR is done.** That is O5.

**O1's smoking gun.** GGML logs a one-shot warning when a weight type has no KleidiAI microkernel and it silently falls back:

```bash
adb shell "cd /data/local/tmp/ot && ./llama-cli -m Llama-3.2-1B-Instruct-Q4_K_M.gguf -p hi -n 1 --no-cnv 2>&1" | grep -i kleidi
adb shell "cd /data/local/tmp/ot && ./llama-cli -m Llama-3.2-1B-Instruct-Q4_0.gguf   -p hi -n 1 --no-cnv 2>&1" | grep -i kleidi
```
**Screenshot both.** Warning present vs absent, side by side, is the most persuasive single image in the whole submission — it *proves* the finding instead of asserting it.

### `bench/otbench.sh`

```bash
#!/usr/bin/env bash
set -euo pipefail
DEV=/data/local/tmp/ot
OUT=bench/results/sweep_$(date +%Y%m%d_%H%M).csv
mkdir -p bench/results
echo "quant,kleidiai,threads,affinity,test,tps,stddev" > "$OUT"

for M in Q4_0 Q4_K_M Q8_0; do
for BIN in llama-bench llama-bench-nokleidi; do
  K=$([ "$BIN" = "llama-bench" ] && echo on || echo off)
  for T in 1 2 3 4 6 8; do
  for A in none c0 3f; do
    CMD="./$BIN -m Llama-3.2-1B-Instruct-$M.gguf -t $T -p 256 -n 128 -r 3 -o csv"
    [ "$A" != none ] && CMD="taskset $A $CMD"
    echo "▶ $M k=$K t=$T aff=$A"
    adb shell "cd $DEV && $CMD" | tail -n +2 | \
      awk -F, -v m=$M -v k=$K -v t=$T -v a=$A '{print m","k","t","a","$0}' >> "$OUT"
    sleep 20          # thermal cooldown — DO NOT REMOVE, it invalidates the numbers
  done; done
done; done
echo "wrote $OUT"
```

### Benchmark protocol — state this verbatim in `docs/BENCHMARKS.md`

> Airplane mode · unplugged · battery > 80 % · screen on at fixed brightness · no other apps · 20 s cooldown between cells · 3 repetitions per cell, median reported · device at room temperature at the start of each sweep.

Rigour is cheap and highly visible. **Shoot the demo video before running benchmarks** — benchmarks heat the phone, and throttled footage looks slow.

> ⛔ **GATE 7** — `sweep.csv` committed; the optimal `(quant, kleidi, threads, mask)` is identified and **hard-coded as the app's default**, with a README line saying so.

---

## PART 11 — Phase 6: documentation `[D]` · 4 h

> **The judges read `README.md`. Assume nothing else.** 15 points of UX/DX and much of the 20 for Impact are decided in the first screenful.

### 11.1 `README.md` — exact order

1. **Title + one line** — *Offline agentic speech translation on a 6 GB mid-range Arm phone. No cloud, no NPU, no i8mm.*
2. **Animated GIF** of Agent Mode with the airplane-mode icon visible in the status bar. ≤ 8 MB.
3. **The headline number**, bold, above the fold: `4.6 s → 1.3 s` end-of-speech to first spoken word (use your real numbers).
4. **The money chart** — cumulative bars, naive → +O1 → +O2 → +O3 → +O5 → +O4.
5. **Device panel** — the verbatim `/proc/cpuinfo` Features line, with `asimddp` present and `i8mm` absent highlighted. *This is your thesis in one screenshot.*
6. **Quickstart** — five copy-pasteable commands.
7. **Try the optimization yourself** — "install the APK, flip the TURBO switch." One sentence, enormously effective.
8. **Architecture diagram** — HetPipe stages mapped onto big/LITTLE.
9. **The seven optimizations** table, each linking to `docs/OPTIMIZATION.md#oN`.
10. **Reproduce on your own device** → `docs/REPRODUCE.md`.
11. **What we cut and why** — signals engineering judgement and pre-empts "where's the OCR?"
12. **Licence · Built with Llama · model attributions.**

### 11.2 `docs/REPRODUCE.md`

Must work for a judge holding a *different* Arm phone. Include: prerequisites, the `fetch_models.sh` + `build_android.sh` + `otbench.sh` sequence, the exact `llama-bench` invocations, the benchmark protocol, and an invitation: *"Open an issue with your device's CSV and we'll add it to the table."* That invitation is what turns a project into a community artifact — the "Potential Impact" criterion in one sentence.

### 11.3 `docs/LANGUAGES.md`

The measured capability matrix: for each of es/hi/fr/de/pt/it/th **and bn**, record measured ASR WER on 10 utterances, whether an offline TTS voice exists, and a verdict (`ship` / `experimental` / `not viable`). **Include the Bengali negative result with its number and the decision that followed.** Publishing a negative result you measured yourself is a credibility multiplier — it shows evidence-based engineering rather than shipping something that half-works.

### 11.4 `NOTICE`

```
OmniTalk Edge — Copyright 2026 <names>. Licensed under Apache-2.0.
Built with Llama.
Llama 3.2 1B Instruct — Llama 3.2 Community License (Meta). Weights not redistributed;
  downloaded by scripts/fetch_models.sh.
Whisper models — MIT (OpenAI).
llama.cpp, whisper.cpp, ggml — MIT.
```

> ⚠️ **TRAP** — `LICENSE` must be the **verbatim, unmodified** Apache-2.0 text. Edit one line and GitHub's detector shows "Other", and the rules explicitly require the licence be visible in the About section.

---

## PART 12 — Phase 7: the video `[D]` · 4 h

> The rules say the video is optional. **It is not.** 25 points of WOW are nearly impossible to earn from text. Judges are not required to watch past three minutes.

### 12.1 Hard constraints

≤ **3:00** · public on YouTube (**not unlisted**) · real device footage · **no speed-up during inference** · **no copyrighted music** (use silence or CC0) · **no third-party trademarks** on screen (avoid showing the Realme/Google boot logos and brand marks).

### 12.2 Shot list

| Time | Shot | Line |
|---|---|---|
| 0:00–0:12 | Close-up: thumb toggles airplane mode ON, on camera | "This is a 2021 mid-range phone. Six gigs of RAM. Two big cores. Armv8.2 — no i8mm, no SME, no NPU. And from here on, no internet." |
| 0:12–0:22 | Full-screen card | "Everything you see runs on this CPU. Nothing is sped up." |
| 0:22–1:20 | **Agent Mode, ONE UNBROKEN TAKE.** Two people. Objective typed, agent speaks Spanish, gets a reply, asks its own follow-up, finishes, shows the English summary. Objective Board ticking in frame. | "I give it a goal, not a sentence. It runs the conversation. Watch the checklist — it noticed the price was still missing and asked for it on its own." |
| 1:20–1:38 | Screen recording, zoomed on the **Core HUD** | "These are the eight cores. Speech recognition on the six little ones. The language model on the two big ones. At the same time — that's where the speed comes from." |
| 1:38–2:05 | **The TURBO toggle**, same sentence twice, stopwatch overlay | "Same app, same phone. Optimizations off: `X` seconds. On: `Y`." |
| 2:05–2:40 | Money chart animating; then the two KleidiAI log screenshots side by side | "Seven optimizations. The one every Arm developer should know: KleidiAI only has kernels for Q4_0 and Q8_0. The Q4_K_M everyone recommends silently falls back to generic code. One letter in a filename — `Z` percent more prefill throughput." |
| 2:40–2:55 | `otbench.sh` scrolling in a terminal, then the repo URL | "The benchmark harness is in the repo. Run it on your phone and send us your numbers." |
| 2:55–3:00 | End card: name, repo URL, "Built with Llama" | — |

### 12.3 Production notes

- **Two devices**: film the phone with a second camera for the human moments, and use `adb shell screenrecord --bit-rate 8000000` for the UI close-ups. Cut between them.
- **Rehearse the agent conversation at least five times** and use the take where it works. Script the "clerk's" Spanish replies so they are clear and in-vocabulary.
- **Burn English subtitles** onto every Spanish utterance. A judge who cannot follow the conversation cannot score it.
- Record voiceover separately from a quiet room; phone-mic narration reads as amateur.
- Export 1080p, ≤ 3:00 **including** the end card.

> ⛔ **GATE 8** — video is public, under 3:00, and a person who does not know the project can explain what it does after watching once. **Test this on someone.**

---

## PART 13 — Phase 8/9: submission `[D]`

### 13.1 Aug 13 evening — DRAFT SUBMIT (non-negotiable)

1. Repo **public** on GitHub.
2. `LICENSE` verbatim Apache-2.0 → confirm the About sidebar reads **Apache-2.0**.
3. `NOTICE` present; **no `.gguf` or `.bin` committed** (check `git ls-files | grep -E "gguf|\.bin$"` returns nothing).
4. README v1 with whatever numbers exist.
5. **Create the Devpost entry, select the Mobile AI track, paste the repo URL, save.** Devpost allows edits until the deadline.
6. Repo topics: `arm`, `kleidiai`, `llama-cpp`, `whisper-cpp`, `on-device-ai`, `edge-ai`, `offline-first`.

### 13.2 Devpost copy

Use the three sections drafted in `02-SUBMISSION.md` — Project Overview (targets WOW + Impact), Functionality/Output (targets Technological Implementation, lead with the optimization table), Setup Instructions (targets UX/DX, must let a judge validate on *their* hardware). Replace every `<MEASURED>` with a real number. **Ship no unmeasured claim.**

### 13.3 Final checklist

- [ ] Every `<MEASURED>` replaced
- [ ] APK attached to a GitHub Release so judges can install without building
- [ ] `docs/REPRODUCE.md` tested from a clean clone
- [ ] Commit history spans Aug 11–14, not one squashed commit
- [ ] Video public, ≤ 3:00, subtitled
- [ ] Submitted by **Aug 15 00:00 Dhaka** (Aug 14 14:00 PT) — two hours of margin

### 13.4 Never claim

❌ i8mm or SME2 acceleration — this device has neither
❌ Arm Performix — it is a Neoverse/cloud tool, wrong track
❌ XNNPACK anywhere near whisper.cpp — it uses GGML
❌ any latency figure not measured on this phone

---

## PART 14 — Fallback decision tree

Consult this instead of improvising.

```
Can't build llama.cpp with the NDK?        → build in Termux on-device
SIGILL at runtime?                         → -march=armv8.2-a+dotprod+fp16
CMake duplicate ggml target?               → build whisper.cpp separately, IMPORTED static lib
JNI symbols not found?                     → check package path matches Java_dev_omnitalk_Native_*
Whisper RTF > 1?                           → ggml-tiny-q5_1, document the quality cost
Concurrency test failed (GATE 3)?          → drop speculative prefill, keep overlaps 1 + 3
No offline Spanish TTS voice?              → try Hindi / French; else on-screen text + subtitles
1B agent unreliable after prompt tuning?   → split into extract-call + ask-call
Still unreliable?                          → 2-turn agent with a fixed question order
/proc/stat per-core unavailable?           → stage-driven HUD, relabel "stage activity"
Behind schedule?                           → cut in this order:
     Live Intercom → Energy (O7) → free-text objectives → speculative prefill (O4.2)
     → 3rd objective → Core HUD animation (keep it static)
NEVER CUT: otbench sweep · TURBO toggle · draft submit · video
```

---

## PART 15 — Scoring self-audit (run this Aug 14 morning)

| Criterion | Pts | Evidence that must exist |
|---|---|---|
| Technological Implementation | 40 | 7 optimizations each with a measured before/after; benchmark protocol stated; naive baseline traces committed; clean JNI/Kotlin separation; correct claims about the ISA |
| UX / DX | 15 | README readable in 60 s; `REPRODUCE.md` works on another device; APK in Releases; TURBO toggle lets a judge validate in 5 s; in-app benchmark screen |
| Potential Impact | 20 | `otbench` runs on any Arm phone; the Q4_0/KleidiAI finding written up as reusable guidance; prompt assets + GBNF grammar published; measured language matrix; open invitation for device results |
| WOW | 25 | Agent completes a real task offline; Core HUD makes the silicon visible; airplane mode on camera; the mid-tier thesis; the KleidiAI screenshot pair |

**If any cell has no evidence, fix that before polishing anything else.**
