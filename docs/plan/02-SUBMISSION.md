# Submission Package: copy, video script, and checklists

Fill every `<MEASURED>` with a real number before publishing. **Do not ship a single unmeasured claim** — that is the mistake the original blueprint made, and these judges audit numbers.

---

## 1. Devpost text, mapped to the rubric

Devpost asks for Project Overview, Functionality/Output, and Setup Instructions. Write each one so it visibly answers a specific criterion.

### Project Overview — *targets WOW (25) and Potential Impact (20)*

> **OmniTalk Edge — an offline agentic translator engineered for the Arm phones people actually own.**
>
> Every on-device LLM demo this year runs on a Snapdragon 8 Elite or an iPhone 17 — silicon with i8mm or SME2 matrix extensions. Those chips are in a small minority of the world's Arm devices. The people who most need offline, private AI — a traveller in a rural market, a clinic in a low-connectivity district — are holding a 6 GB mid-tier phone with dotprod and nothing else.
>
> We built OmniTalk Edge on exactly that phone: a **Realme Narzo 50 Pro (MediaTek Dimensity 920, 2× Cortex-A78 + 6× Cortex-A55, Armv8.2-A, no i8mm, no SVE, no SME, no NPU path, 6 GB RAM)**, in airplane mode.
>
> It does two things. **Live Intercom** is push-to-talk speech-to-speech translation. **Agent Mode** is the part we care about: you give it a goal — *"find out when the bus leaves, whether it has AC, and the price"* — and it runs the conversation itself. It speaks to the other person in their language, tracks which facts it still needs in a finite state machine, asks its own follow-up questions, and hands you an English summary. All of it on the CPU, offline.
>
> We got there with six measured optimizations, not by picking a better model. The headline: end-of-speech to first spoken word fell from **`<MEASURED>` s to `<MEASURED>` s**, a **`<MEASURED>`× improvement**, on unchanged hardware.
>
> The most reusable finding is embarrassingly simple. Arm's KleidiAI ships int4 microkernels for **`Q4_0` and `Q8_0` only**. Almost every quantization guide online recommends **`Q4_K_M`** as the quality/size sweet spot — and on Arm, `Q4_K_M` silently falls back to generic GGML kernels and never touches KleidiAI at all. Switching one letter in a filename gave us **`<MEASURED>`%** more prefill throughput. We think a lot of Arm deployments are leaving that on the table right now.
>
> Built with Llama.

### Functionality / Output — *targets Technological Implementation (40)*

Lead with the table. Judges scan.

> **What we shipped**
>
> 1. **OmniTalk Edge** — an Android app (Kotlin/Compose + JNI) running whisper.cpp and llama.cpp entirely on the CPU, fully offline.
> 2. **`otbench`** — a reproducible on-device benchmark harness (adb sweep + in-app benchmark screen) that measures TTFT, prefill/decode tok/s, RSS and thermals across quantization × KleidiAI × thread count × CPU affinity. Runs on any Arm Android device, not just ours.
> 3. **Measured results for a device class nobody benchmarks** — mid-tier Armv8.2 without i8mm — published as CSV and charts.
> 4. **Prompt assets and a GBNF grammar** that make a 1 B model reliable enough to drive a state machine.
>
> **The six optimizations, each measured independently on the same device:**
>
> | # | Optimization | Metric | Before | After | Δ |
> |---|---|---|---|---|---|
> | O1 | `Q4_K_M` → `Q4_0` so KleidiAI microkernels engage | prefill tok/s | `<M>` | `<M>` | `<M>` |
> | O2 | KleidiAI ON vs OFF, identical weights | decode tok/s | `<M>` | `<M>` | `<M>` |
> | O3 | Thread count + big.LITTLE affinity (8 threads → `<M>` pinned to A78) | decode tok/s | `<M>` | `<M>` | `<M>` |
> | O4 | Overlapped pipeline: chunked ASR on LITTLE during capture, speculative prefill on big, sentence-chunked TTS | end-of-speech → first audio | `<M>` s | `<M>` s | `<M>` |
> | O5 | KV-cache reuse across agent turns | prefill tokens, turn 3 | `<M>` | `<M>` | `<M>` |
> | O6 | GBNF grammar-constrained decoding | JSON parse failures / 100 turns | `<M>` | 0 | — |
>
> **Peak RSS `<M>` GB** on a 6 GB device — no OS memory-killer events across `<M>` runs.
>
> **Methodology:** airplane mode, unplugged, >80 % battery, fixed brightness, 5-minute cooldown between cells, 3 repetitions per cell, median reported. Full method and raw CSVs in `docs/BENCHMARKS.md`.
>
> **What we deliberately did not build,** and why — camera OCR, a second TTS engine, and an ExecuTorch export path were all cut so that every remaining component could be measured properly. `docs/LANGUAGES.md` documents the languages this device *cannot* serve, including our measured Whisper WER on Bengali, and explains the model choice that follows from it.

### Setup Instructions — *targets UX/DX (15)*

Must let a judge validate on hardware **they** own.

> **Run the benchmarks in 10 minutes (any Arm64 Android phone):**
> ```
> git clone --recursive https://github.com/<you>/omnitalk-edge && cd omnitalk-edge
> ./scripts/fetch_models.sh          # downloads + sha256-verifies weights (not committed — see NOTICE)
> ./scripts/build_android.sh         # needs ANDROID_NDK
> ./bench/otbench.sh                 # adb sweep -> bench/results/<your-device>.csv
> python bench/analyze.py            # tables + charts
> ```
> **Install the app:** `./gradlew :app:installRelease`, or grab the APK from Releases.
> **Reproduce our exact numbers:** `docs/REPRODUCE.md` lists device state, thermal protocol, and the exact `llama-bench` invocations.
> **Tested on:** Realme Narzo 50 Pro (Dimensity 920, Android `<M>`). Report results for your device in an issue — we will add it to the table.

---

## 2. README.md structure

Order matters. Assume a judge reads the first screenful and decides.

1. **Title + one line** — "Offline agentic speech translation on a 6 GB mid-tier Arm phone. No cloud, no NPU, no i8mm."
2. **Animated GIF** of Agent Mode, airplane-mode indicator visible in the status bar
3. **The headline number**, bold, above the fold: `<M>` s → `<M>` s end-of-speech to first spoken word
4. **The money chart** (stacked bars, one per optimization)
5. **Quickstart** (5 commands, copy-pasteable)
6. **Architecture diagram** — pipeline stages mapped onto big/LITTLE cores
7. **The six optimizations** table, each linking to `docs/OPTIMIZATION.md#oN`
8. **Reproduce on your device** → `docs/REPRODUCE.md`
9. **What we cut and why** — signals engineering judgement, and preempts "where's the OCR?"
10. **Licence + "Built with Llama" + model attributions**

---

## 3. Video script — 2:55

No music. No trademarks on screen. No speed-up during inference — say so on screen.

| Time | Shot | Voiceover / on-screen |
|---|---|---|
| 0:00–0:15 | Close-up: phone, airplane mode toggled on, on camera | "This is a 2021 mid-range phone. Six gigs of RAM. A Dimensity 920 — two big cores, Armv8.2. No i8mm. No SME. No NPU. And from here on, no internet." |
| 0:15–0:30 | Full-screen text over the app | "Everything you're about to see runs on this CPU. Nothing is sped up." |
| 0:30–1:25 | **Agent Mode, one unbroken take.** Two people. Goal typed in, agent speaks Spanish, gets a reply, asks its own follow-up, ends, shows the English summary. | "I give it a goal, not a sentence. It runs the conversation. It's tracking three facts it still needs — it noticed the price is missing and asked for it on its own." |
| 1:25–1:45 | Live Intercom, quick back-and-forth | "The same pipeline does plain speech-to-speech." |
| 1:45–2:05 | Screen recording: naive build vs optimized build, **side by side, same utterance, stopwatch overlay** | "This is the same app before our optimizations. `<M>` seconds to first word. Now `<M>`." |
| 2:05–2:40 | The money chart animating in, then the KleidiAI fallback-warning screenshot | "Six optimizations. The one we'd tell every Arm developer about: KleidiAI only has microkernels for Q4_0 and Q8_0. The Q4_K_M everyone recommends silently falls back to generic kernels. One letter in a filename — `<M>` percent more prefill throughput." |
| 2:40–2:55 | Terminal: `otbench.sh` running, then the repo URL | "The benchmark harness is in the repo. Run it on your phone and send us the numbers." |

**Shoot the demo footage before running benchmarks** — benchmarks heat the phone and throttled footage looks slow.

---

## 4. Pre-submit checklist

**Rules compliance**
- [ ] Repo public; About sidebar shows **Apache-2.0** (verbatim licence text — an edited file makes GitHub show "Other")
- [ ] `NOTICE` lists Llama 3.2 Community Licence, Whisper MIT, llama.cpp MIT, whisper.cpp MIT
- [ ] **"Built with Llama"** in README and app About screen
- [ ] **No model weights committed** — `fetch_models.sh` downloads them
- [ ] Commit history spans Aug 11–14, not one squashed commit
- [ ] Video ≤ 3:00, public on YouTube, unlisted is **not** sufficient — it must be publicly visible
- [ ] Video has no copyrighted music and no third-party logos
- [ ] Setup instructions describe building/running on an Arm device
- [ ] Track selected: **Mobile AI**

**Quality**
- [ ] Every `<MEASURED>` replaced with a real number
- [ ] `docs/REPRODUCE.md` tested on a second device or at least a clean checkout
- [ ] APK attached to a GitHub Release so judges can install without building
- [ ] Repo description + topics set (`arm`, `kleidiai`, `llama-cpp`, `whisper-cpp`, `on-device-ai`, `edge-ai`)
- [ ] Devpost submitted with ≥ 6 h margin

**Never claim**
- ❌ i8mm or SME2 acceleration — the demo device has neither
- ❌ Arm Performix — it is a Neoverse/cloud tool, wrong track
- ❌ Any latency figure you did not measure on your own phone
- ❌ "XNNPACK" anywhere near whisper.cpp — it uses GGML

---

## 5. Anticipated judge questions

| Question | Answer |
|---|---|
| "Why llama.cpp and not ExecuTorch?" | Grammar-constrained decoding, which we need for reliable agent state; one toolchain shared with whisper.cpp; and a `.pte` at ~1.9 GB RSS does not fit safely in 6 GB. `docs/ARCHITECTURE.md` documents the trade-off. |
| "Isn't this just Google Translate offline?" | Translation is the vehicle. Agent Mode pursues a goal across turns, tracks unfilled slots, and generates its own follow-ups. No offline translator does that. |
| "Your numbers are lower than Arm's published Llama 3.2 benchmarks." | Correct, and deliberate. Those are i8mm-class flagships. Our device is Armv8.2 with dotprod only. That gap is the point of the project. |
| "Why not Bengali, given where you built this?" | We measured it: Whisper-tiny/base WER on Bengali on this device is `<M>` %, and Whisper-small runs slower than real time on two A78 cores. `docs/LANGUAGES.md` shows the data and the decision. |
| "Where's the camera OCR from your original concept?" | Cut so the rest could be measured properly. `README#what-we-cut` explains it. |
