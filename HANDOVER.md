# Handover — read this first

You are picking up **OmniTalk Edge**, an entry for the Arm Create: AI Optimization
Challenge 2026 (Mobile AI track).

**Deadline: 2026-08-14 16:00 PT — that is 2026-08-15 05:00 Dhaka time.**

Everything below is written so you can run it without knowing the history. Nothing
here requires an AI assistant.

---

## 1. What this is, in one paragraph

An Android app that runs a **goal-directed conversation** entirely offline on a
mid-range Arm phone. You give it an objective — *"find out when the bus leaves,
whether it has AC, and the price"* — and it asks a local person questions in their
language, extracts the facts, tracks what is still missing in a state machine, and
produces an English summary. Speech recognition is **whisper.cpp**, the language
model is **llama.cpp with Llama 3.2 1B Q4_0**, both on the CPU. No network — the app
has no `INTERNET` permission at all.

The submission's headline is a **measured finding**: Arm's KleidiAI does nothing on
Armv8.2-A phones because its int4/int8 kernels need i8mm or SME, which this class of
silicon lacks. See `README.md`.

---

## 2. Set up your machine (one command)

Open **Git Bash** (comes with Git for Windows) and run:

```bash
git clone --recursive <REPO_URL> && cd omnitalk-edge
bash scripts/setup.sh
```

That installs, all onto `D:` because `C:` is usually too small:

| | |
|---|---|
| Temurin **JDK 21** | AGP needs 17 or 21; a newer system JDK breaks the build |
| Android **command-line tools** | Android Studio is deliberately *not* installed — costs 4 GB, never needed |
| **NDK 27.3.13750724** | 2.2 GB, the slow step |
| CMake 3.22.1 + 3.31.6, platform-tools (`adb`), platform 34, build-tools 34 | |
| **Gradle 8.11.1** | |
| Python `matplotlib pandas tabulate` | only for regenerating charts |
| **Model weights** ~1.6 GB | sha256-verified, never committed |

Needs about **12 GB free on D:**. Re-running is safe — every step checks first.

**Then open a NEW terminal** so the environment variables take effect.

---

## 3. Set up the phone

Your device is a **Realme Narzo 50 Pro 5G** (Dimensity 920 — 2× Cortex-A78 +
6× Cortex-A55, Armv8.2-A). Same architecture class as the development phone, so
everything transfers unchanged; only the numbers differ.

1. Settings → About phone → tap **Build number** 7×
2. Developer options → **USB debugging** ON
3. Plug in, **accept the RSA prompt on the phone screen** (easy to miss)
4. On Xiaomi/POCO/Redmi/Realme also enable **Install via USB** and **USB debugging
   (Security settings)** — both usually demand a Mi/Realme account sign-in
5. Check: `adb devices` must show your phone as `device`, not `unauthorized`

Then:

```bash
bash scripts/deploy.sh --models    # build, install, push the ~800 MB of models
```

Later rebuilds are just `bash scripts/deploy.sh`.

---

## 4. Check it works

```bash
bash scripts/deploy.sh --test
```

This runs a **3-turn conversation from pre-recorded audio** — no speaking required —
and writes the results to `bench/results/turnlogs/`. Open `turn_002.json`; you should
see slots filled from genuinely spoken values:

```json
"transcript": "Only the 10 o'clock bus has air conditioning and a ticket costs 1500 Taka.",
"slots": { "departure": "10 am", "ac": "yes", "price": "$1500.00" }
```

For a manual test, follow `docs/DEMO_SCRIPT.md` — it gives you the exact sentences to
say and what should happen after each one.

---

## 5. What is done, and what is left

Full detail in `PROGRESS.md`. Short version:

**Done**
- Toolchain, repo, Apache-2.0 licence, pinned submodules, verified model downloads
- Benchmark sweep on a Poco M2 Pro: 24 measured cells + 4 charts
- Three findings: the **KleidiAI cliff**, the **58 % decode collapse at 8 threads**,
  and **99.8 % concurrency** between the two CPU clusters
- Working Android app: agent loop, grammar-constrained decoding, evidence-checked
  slot extraction, TURBO/NAIVE toggle, per-turn telemetry
- `README.md` written around the measured results

**Left**
1. **Push the repo public and save a Devpost draft** — do this first, it is the only
   irreversible deadline
2. Re-run the benchmark sweep on the Narzo → a second, cross-vendor column
3. Measure **TURBO vs NAIVE** end-to-end latency (needs a person speaking)
4. `docs/OPTIMIZATION.md`, `BENCHMARKS.md`, `LANGUAGES.md`, `REPRODUCE.md`
5. Video, ≤ 3 minutes
6. Devpost text

---

## 6. Things that will bite you

All of these were hit for real. Full list with exact error text in
`docs/TROUBLESHOOTING.md`.

- **Never omit `-c` on a llama.cpp command.** Llama 3.2's trained context is 131072
  tokens; defaulting to it allocates a multi-GB KV cache and Android's low-memory
  killer starts killing apps. The phone can reboot.
- **`INSTALL_FAILED_USER_RESTRICTED`** — MIUI/Realme blocking adb installs. Enable
  *Install via USB*.
- **`LLVM ERROR: IO failure ... No space left on device`** — not an NDK bug, a full
  disk. The default llama.cpp build produces 520 targets and 8 GB of build tree.
- **Bengali does not work.** Measured on device: whisper-tiny returns
  `"Keep it to soul."` and base returns `"ki kottisu"` for clear Bengali speech.
  This is the models, not our code. English is near-perfect; Hindi is the demo
  candidate. Do not spend time trying to fix Bengali.

---

## 7. Layout

```
README.md              the judged document — written around measured results
PROGRESS.md            checklist of what is done and what is next
HANDOVER.md            this file
docs/
  DEMO_SCRIPT.md       exact sentences for a manual test
  TROUBLESHOOTING.md   every error we hit, with fixes
  plan/                original strategy, spec, submission copy
scripts/
  setup.sh             one-command machine setup
  deploy.sh            build + install + push + launch + self-test
  watch.sh             live per-turn telemetry
  fetch_models.sh      sha256-verified weight download
  otbench.ps1          the benchmark sweep
bench/
  analyze.py           CSV -> charts + tables
  results/             committed measurements — these ARE the submission
android/               the app
native/otjni.cpp       single JNI layer over llama.cpp + whisper.cpp
third_party/           llama.cpp, whisper.cpp (submodules), kleidiai (vendored)
```

**Read the comments in `native/otjni.cpp` and `android/.../Pipeline.kt` before
changing them.** Both carry the reasoning for decisions that look wrong until you
know why — for example the LLM is deliberately *not* pinned to the big cores, and
`llama_sampler_accept` must *not* be called after `llama_sampler_sample`.
