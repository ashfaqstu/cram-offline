# OmniTalk Edge — Full Spec Tracker

> **This is the ambitious version, kept for after the minimal build ships.**
> The active day-to-day list is [`PROGRESS.md`](PROGRESS.md) — work from that first.
> Come back here once the minimal app is finished and there is time left to widen scope.
>
> Boxes below reflect what has **actually** been done as of 2026-08-13, with real
> measured values in place of the original `____` blanks.

- **Deadline:** 2026-08-14 16:00 PT = **2026-08-15 05:00 Dhaka**
- **Spec:** [`docs/plan/SPEC.md`](docs/plan/SPEC.md)
- **Owners:** `[N]` native/C++ · `[A]` Android/Kotlin · `[B]` benchmarks · `[D]` docs/video

**Status:** 🔴 not started · 🟡 in progress · 🟢 done · ⚫ cut

| Phase | Status | Owner | Gate | Where it stands |
|---|---|---|---|---|
| 0 Environment | 🟢 | N | GATE 0 | one-command `scripts/setup.sh` |
| 1 Risk kill | 🟢 | N | GATE 1,2,3 | all three passed |
| 2 Native layer | 🟢 | N | GATE 4 | `libotjni.so` 7.06 MB |
| 3 Android app | 🟢 | A | GATE 5 | working; naive-vs-turbo traces still to capture |
| 4 Prompts + grammar | 🟡 | A | GATE 6 | works; formal 20-reply eval not run |
| 5 Optimizations + bench | 🟡 | B | GATE 7 | Poco sweep done; Narzo column + O4 pending |
| 6 Documentation | 🟡 | D | — | README, HANDOVER, TROUBLESHOOTING done; 4 left |
| 7 Video | 🔴 | D | GATE 8 | not started |
| 8 Draft submit | 🔴 | D | — | **do this first** |
| 9 Final submit | 🔴 | D | — | |

---

## ⛔ Gates

- [x] **GATE 0** `adb devices` lists the phone — `4178ab90 POCO_M2_Pro`
      ⚠️ TTS-in-airplane-mode never verified, and **no offline Bengali voice exists** on the device
- [x] **GATE 1** Text generated on the phone by `llama-cli`
      *(`-no-cnv` takes a single dash in this build; `-st`/`--single-turn` is the flag for scripted runs)*
- [x] **GATE 2** Whisper RTF — tiny **0.459** ✅ · base **0.915** ❌ → ship tiny
- [x] **GATE 3** Concurrency — solo `12.89/8.74` vs concurrent `12.91/8.72` = **99.8 %** ✅✅
      *HetPipe's premise is measured fact: ASR on the LITTLE cluster costs the LLM nothing.*
- [x] **GATE 4** `libotjni.so` builds (7.06 MB, arm64) and generates text from Kotlin
- [x] **GATE 5** End-to-end turn works — ⚠️ `trace_naive.json` **not captured**, so O4 has no baseline yet
- [ ] **GATE 6** Formal 20-reply eval not run. Informally: JSON valid every turn, slots extract
      correctly, invented values rejected — see `bench/results/turnlogs/`
- [x] **GATE 7** `bench/results/sweep_POCO_M2_Pro.csv` committed (24 cells); optimal config
      hard-coded (`Q4_0`, 6 decode / 8 prefill threads, ASR pinned to LITTLE)
- [ ] **GATE 8** Video not started

---

## Phase 0 — Environment `[N]` · Aug 11

- [ ] Android Studio installed
- [ ] NDK (side-by-side) 27.x+ installed · version: `____`
- [ ] CMake + Platform-Tools installed
- [ ] Android 14 / API 34 platform installed
- [ ] `ANDROID_HOME` and `ANDROID_NDK` env vars set
- [ ] USB debugging enabled, RSA prompt accepted
- [ ] `adb devices` shows the phone
- [ ] Spanish offline TTS voice data installed
- [ ] **Verified Spanish TTS speaks with airplane mode ON**

---

## Phase 1 — Risk kill `[N]` · Aug 11–12

### Silicon facts (record these — they go in the README verbatim)
- [ ] `/proc/cpuinfo` Features line captured → `bench/results/device_info.txt`
- [ ] `asimddp` present? **____** · `i8mm` absent? **____** · `sve`/`sme` absent? **____**
- [ ] Per-core max frequencies captured
- [ ] **BIG_MASK = `0x____`** · **LITTLE_MASK = `0x____`** ← if not `0xC0`/`0x3F`, update every mask in SPEC.md
- [ ] Model / Android version / total RAM recorded

### Builds
- [ ] llama.cpp cloned · **pinned commit `____________`**
- [ ] llama.cpp built for arm64-v8a with `GGML_CPU_KLEIDIAI=ON`
- [ ] llama.cpp built again with `GGML_CPU_KLEIDIAI=OFF` (needed for O2)
- [ ] No SIGILL (if there was: `-march` used = `____________`)
- [ ] whisper.cpp cloned · **pinned commit `____________`**
- [ ] whisper.cpp built for arm64-v8a

### Models fetched
- [ ] `Llama-3.2-1B-Instruct-Q4_0.gguf`
- [ ] `Llama-3.2-1B-Instruct-Q4_K_M.gguf` (O1 comparison arm)
- [ ] `Llama-3.2-1B-Instruct-Q8_0.gguf` (O1 comparison arm)
- [ ] `ggml-base-q5_1.bin`
- [ ] `ggml-tiny-q5_1.bin`

### On-device proof
- [ ] Binaries + models pushed to `/data/local/tmp/ot`
- [ ] **GATE 1** — Spanish generated on phone · screenshot at `____________`
- [ ] 16 kHz mono WAV test clip created and pushed
- [ ] **GATE 2** — Whisper RTF: base `____` · tiny `____`
- [ ] ASR model chosen: **base / tiny** (circle one)
- [ ] **GATE 3** — concurrency test: LLM solo `____` tok/s, concurrent `____` tok/s = **____ %**
- [ ] Decision recorded: speculative prefill **KEEP / DROP**

---

## Phase 2 — Native layer `[N]` · Aug 12

- [ ] Repo created, `.gitignore` excludes `*.gguf`, `*.bin`, `build/`
- [ ] llama.cpp + whisper.cpp added as pinned submodules
- [ ] `native/CMakeLists.txt` written
- [ ] Duplicate `ggml` target resolved (method used: `____________`)
- [ ] **API drift check run** — record actual symbol names:
  - [ ] model load: `____________________`
  - [ ] context init: `____________________`
  - [ ] get vocab: `____________________`
  - [ ] is-eog: `____________________`
  - [ ] kv clear: `____________________`
  - [ ] grammar sampler: `____________________`
- [ ] `otjni.cpp` — `setAffinity`
- [ ] `otjni.cpp` — `llmLoad` / `llmPrefill` / `llmGenerate` / `llmResetKv` / `llmTimings`
- [ ] `otjni.cpp` — `asrLoad` / `asrTranscribe`
- [ ] Token streaming callback works (needed for O4 overlap 3)
- [ ] **GATE 4** — JNI smoke test generates text from Kotlin

---

## Phase 3 — Android app `[A]` · Aug 12

### Plumbing
- [ ] Gradle project, `minSdk 28`, `abiFilters arm64-v8a`, CMake wired
- [ ] `RECORD_AUDIO` permission + runtime request
- [ ] Model import screen (copies from `/sdcard/Download/`) — **weights are not in the APK**
- [ ] `Native.kt` declarations match the JNI symbol names exactly

### Core
- [ ] `Audio.kt` — 16 kHz mono f32, 2 s chunks as a Flow
- [ ] `Pipeline.kt` — single-thread dispatchers, affinity set **before** model load
- [ ] `Pipeline.kt` — NAIVE path (8 threads, no overlap, KV reset each turn)
- [ ] `Pipeline.kt` — TURBO path (overlaps 1, 2, 3 + KV reuse)
- [ ] `Trace.kt` — stage marks + `firstAudioLatency()`
- [ ] `Tts.kt` — `QUEUE_ADD` sentence chunking
- [ ] `first_audio` marked from `UtteranceProgressListener.onStart` (**playback**, not synthesis)
- [ ] `Energy.kt` — battery current/voltage sampling at 200 ms

### Screens
- [ ] S1 Home — language pair, two mode buttons, live offline indicator
- [ ] S2 Agent — Objective Board with live slot ticks
- [ ] S2 — every utterance shown with an **English gloss**
- [ ] S2 — turn counter `Turn N of 6`
- [ ] S2 — **Core HUD** (F2)
- [ ] S2 — latency waterfall + `FIRST AUDIO __ s` readout
- [ ] S2 — **TURBO toggle** (F3)
- [ ] S3 Summary — English card + full transcript + run stats + share
- [ ] S4 Benchmark — quick/full run, live table, export JSON
- [ ] `/proc/stat` per-core readable? **YES / NO** → HUD mode: **real load / stage-driven**
- [ ] F4 Live Intercom mode
- [ ] **GATE 5** — end-to-end turn works; both traces exported

---

## Phase 4 — Prompts + grammar `[A]` · Aug 13

- [ ] `agent_system.txt`
- [ ] `summarize.txt`
- [ ] `translate.txt` (Live Intercom)
- [ ] `agent.gbnf` written and loads without a grammar parse error
- [ ] `AgentFsm.kt` — merge never overwrites a known slot with null
- [ ] Objective 1 — bus ticket
- [ ] Objective 2 — pharmacy
- [ ] Objective 3 — market haggle
- [ ] Free-text objective input
- [ ] 20-reply prompt evaluation run on device
- [ ] **GATE 6** — thresholds met (record scores above)
- [ ] Summary generation works and is English-only

---

## Phase 5 — Optimizations + benchmarks `[B]` · Aug 13

Fill in every number. **These blanks are the submission.**

| # | Optimization | Metric | Before | After | Δ | Done |
|---|---|---|---|---|---|---|
| O1 | Q4_K_M → Q4_0 | prefill tok/s | ____ | ____ | ____ | [ ] |
| O2 | KleidiAI OFF → ON | decode tok/s | ____ | ____ | ____ | [ ] |
| O3 | 8 threads all-core → __ threads on big | decode tok/s | ____ | ____ | ____ | [ ] |
| O4 | Serial → HetPipe overlap | end-of-speech → first audio (s) | ____ | ____ | ____ | [ ] |
| O5 | Full re-prefill → KV reuse | prefill tokens, turn 3 | ____ | ____ | ____ | [ ] |
| O6 | No grammar → GBNF | JSON failures / 100 | ____ | ____ | ____ | [ ] |
| O7 | Naive → optimized | mJ per turn | ____ | ____ | ____ | [ ] |

**Headline number:** `____ s → ____ s` (**____×**)

- [ ] `bench/otbench.sh` written and runs unattended
- [ ] Full sweep completed with 20 s cooldowns
- [ ] `bench/results/sweep.csv` committed
- [ ] **O1 smoking gun**: KleidiAI fallback warning screenshotted for Q4_K_M **and** absent for Q4_0
- [ ] `bench/analyze.py` written
- [ ] Chart 1 — the money chart (cumulative optimization bars)
- [ ] Chart 2 — prefill/decode tok/s by quant × KleidiAI
- [ ] Chart 3 — tok/s vs thread count, one line per affinity mask
- [ ] Chart 4 — RSS over a full conversation vs the device ceiling
- [ ] Chart 5 — thermal curve over 10 minutes
- [ ] Peak app RSS measured: **____ MB** (budget ≤ 1600 MB)
- [ ] Zero LMK kills across **____** full-conversation runs
- [ ] Optimal config identified: quant `____` · threads `____` · mask `0x____`
- [ ] **Optimal config hard-coded as the app default** and stated in the README
- [ ] **GATE 7**

---

## Phase 6 — Documentation `[D]` · Aug 14

- [ ] `LICENSE` — **verbatim** Apache-2.0
- [ ] GitHub About sidebar reads **Apache-2.0** (not "Other")
- [ ] `NOTICE` with model licences + **"Built with Llama"**
- [ ] `git ls-files | grep -E "gguf|\.bin$"` returns **nothing**
- [ ] README §1 title + one-liner
- [ ] README §2 hero GIF with airplane-mode icon visible
- [ ] README §3 headline number above the fold
- [ ] README §4 money chart
- [ ] README §5 device panel with the `/proc/cpuinfo` Features line
- [ ] README §6 quickstart (5 commands)
- [ ] README §7 "flip the TURBO switch yourself"
- [ ] README §8 architecture diagram
- [ ] README §9 seven-optimization table
- [ ] README §10 reproduce link
- [ ] README §11 what we cut and why
- [ ] README §12 licence + attribution
- [ ] `docs/ARCHITECTURE.md`
- [ ] `docs/OPTIMIZATION.md` — one section per O#, each with method + number + code link
- [ ] `docs/BENCHMARKS.md` — protocol stated verbatim + full tables
- [ ] `docs/LANGUAGES.md` — capability matrix **including the measured Bengali negative result**
- [ ] `docs/REPRODUCE.md` — tested from a clean clone
- [ ] "Open an issue with your device's CSV" invitation present
- [ ] Repo topics set
- [ ] APK built and attached to a GitHub Release

---

## Phase 7 — Video `[D]` · Aug 14

- [ ] **Demo footage shot BEFORE benchmarks** (benchmarks heat the phone)
- [ ] Agent conversation rehearsed ≥ 5 times; clerk's Spanish replies scripted
- [ ] Shot: airplane mode toggled on camera
- [ ] Shot: "nothing is sped up" card
- [ ] Shot: Agent Mode, one unbroken take, Objective Board in frame
- [ ] Shot: Core HUD close-up
- [ ] Shot: TURBO toggle A/B with stopwatch overlay
- [ ] Shot: money chart + KleidiAI screenshot pair
- [ ] Shot: `otbench.sh` running + repo URL
- [ ] End card with repo URL and "Built with Llama"
- [ ] **English subtitles burned onto every Spanish utterance**
- [ ] Voiceover recorded separately, quiet room
- [ ] No music, or CC0 only
- [ ] No third-party logos/trademarks visible
- [ ] No speed-up during any inference footage
- [ ] Runtime **____** (must be < 3:00 including end card)
- [ ] Uploaded to YouTube as **Public** (not unlisted)
- [ ] **GATE 8** — outsider watch-test passed

---

## Phase 8 — Draft submit `[D]` · **Aug 13 evening, non-negotiable**

- [ ] Repo public
- [ ] Devpost entry created
- [ ] **Mobile AI track** selected
- [ ] Repo URL pasted
- [ ] Saved as draft

---

## Phase 9 — Final submit `[D]` · Aug 14

- [ ] Project Overview written (WOW + Impact)
- [ ] Functionality / Output written, leading with the optimization table
- [ ] Setup Instructions written (validatable on the judge's own hardware)
- [ ] Video URL added
- [ ] **Every `<MEASURED>` replaced with a real number**
- [ ] Commit history spans Aug 11–14 (not squashed)
- [ ] Scoring self-audit run (SPEC.md Part 15) — every cell has evidence
- [ ] **SUBMITTED** at `____________` (target: Aug 15 00:00 Dhaka / Aug 14 14:00 PT)

### Never-claim audit
- [ ] No mention of i8mm or SME2 acceleration anywhere
- [ ] No mention of Arm Performix
- [ ] No mention of XNNPACK near whisper.cpp
- [ ] No latency figure that was not measured on this phone

---

## Decision log

Record anything that deviates from SPEC.md, with the reason.

| Date | Decision | Reason |
|---|---|---|
| | | |

---

## Blocker log

| Date | Blocker | Owner | Resolution |
|---|---|---|---|
| | | | |

---

## Cut log

Cut in this order when behind. Record what actually went.

- [ ] Live Intercom (F4)
- [ ] Energy measurement (O7)
- [ ] Free-text objectives
- [ ] Speculative prefill (O4 overlap 2)
- [ ] Third objective
- [ ] Core HUD animation (keep static)

**NEVER CUT:** otbench sweep · TURBO toggle · draft submit · video
