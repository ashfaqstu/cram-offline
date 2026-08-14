# Submission package — Devpost copy and checklists

Every number here is measured on the dev device and traceable to
`bench/results/sweep_POCO_M2_Pro.csv` or to the app's own on-screen telemetry.
**Do not add a claim that isn't.**

> **Correction carried forward.** An earlier draft of this file claimed the Q4_0
> speedup came from KleidiAI engaging, and pitched it as "switch one letter in the
> filename". Our own A/B disproved that: Q4_0 beats Q4_K_M *with KleidiAI disabled
> in both*. The gain is GGML's aarch64 repack path. The old wording was the right
> recommendation for the wrong reason — the exact error the rest of this submission
> argues against.

---

## 1. Devpost text

### Project Overview — *targets WOW and Potential Impact*

> **Cram — ask your lecture slides a question, offline, on the phone you already own.**
>
> Open a lecture PDF. Ask it anything. Cram finds the passage that answers you, shows
> you which slide it came from, and writes the answer from that passage. No network, no
> account, nothing uploaded. Then it turns the same slides into flashcards.
>
> Every on-device LLM demo this year runs on a Snapdragon 8 Elite or an iPhone 17 —
> silicon with i8mm or SME2 matrix extensions. Those chips are a small minority of the
> world's Arm devices. We built and measured this on a **Poco M2 Pro (Snapdragon 720G,
> 2× Cortex-A76 + 6× Cortex-A55, Armv8.2-A, no i8mm, no SVE, no SME, no NPU, 6 GB RAM)**
> — a 2020 midrange phone, in airplane mode.
>
> **The finding we'd tell every Arm developer: KleidiAI does nothing on this hardware.**
> Arm markets it as CPU acceleration for on-device AI. Its int4 microkernels require
> i8mm or SME; Armv8.2-A has dotprod and neither, so GGML silently falls back to generic
> kernels. We built the same binary twice, with and without:
>
> | Q4_0 weights | prefill tok/s | decode tok/s |
> |---|---:|---:|
> | KleidiAI **ON** | 18.75 | 8.82 |
> | KleidiAI **OFF** | 17.48 | 9.17 |
>
> Seven percent apart with **no consistent sign** — decode is marginally *faster* with it
> off. That is run-to-run noise, not acceleration. The device says so itself:
> `kleidiai: no compatible q4 kernels found for CPU features mask 1`. That cliff runs
> straight through the mid-tier install base. The app displays this about the user's own
> phone rather than asking anyone to take our word for it.
>
> **So we measured where the speed actually comes from.** First word fell from **34.5 s
> to ~11 s — 3×** on unchanged hardware, with retrieval never exceeding 7 ms.
>
> Built with Llama.

### Functionality — *targets Technological Implementation*

> **The measurement the whole app follows from.** On a desktop GPU, prefill runs 10–50×
> faster than decode, and every "just stuff more context in" RAG design assumes it. On
> this CPU **prefill is only about twice decode — roughly 14 ms per character of prompt.**
> Retrieving eight passages and sending them all would cost half a minute before the
> first word. So Cram inverts it:
>
> - **Retrieve many, send few.** BM25 ranks every passage in the deck in **1–7 ms**;
>   only the best one or two are ever paid for in tokens.
> - **Greedy budget allocation.** The top passage takes what it needs and the runner-up
>   gets the remainder, rather than an even split that truncated the winner to make room
>   for a passage that went unused.
> - **Evidence first.** The matching slide appears while the model is still writing. The
>   retrieval *is* the answer for most questions; the model only phrases it.
>
> **Thread scaling, measured, 24 cells:**
>
> | threads | prefill pp128 | decode tg32 |
> |---:|---:|---:|
> | 6 | 18.74 | **10.95** |
> | 8 | **20.48** | **4.57 — 58% collapse** |
>
> Using all 8 cores makes decode **58% slower** than using 6: every layer ends in a
> barrier, so the two fast A76s wait on the six slow A55s. Prefill and decode want
> opposite thread counts, so we run 8 and 6 respectively.
>
> **It sizes itself to the phone it's on.** A budget baked in on our device is wrong on
> every other one. At startup Cram times a real prefill and derives its prompt budget
> from the result. Settings shows the measurement, the values chosen from it, and the
> cost in seconds of overriding them.
>
> **Q4_0 over Q4_K_M — for the right reason.** +15% prefill (18.1 vs 15.7), and it holds
> with KleidiAI disabled in both. The gain is GGML's own aarch64 weight-repack path
> (`use_extra_bufts`), not KleidiAI. Most write-ups get the recommendation right and the
> reason wrong.
>
> **Correctness is not traded for speed.** Tuning purely for latency drove the character
> budget to ~400 — too small to hold a four-item list, so the model answered fast and
> *invented two of the four Coffman conditions*. The budget is floored at 900 and the
> Settings ladder only climbs from there. Every answer and every flashcard cites its
> slide, so the working can be checked.
>
> **Privacy is structural.** The app declares no internet permission and no storage
> permission. It cannot send a document anywhere, even by mistake.

### Setup Instructions — *targets UX/DX*

Must let a judge validate on hardware **they** own.

> ```bash
> git clone --recursive https://github.com/ashfaqstu/cram-offline && cd cram-offline
> ./scripts/fetch_models.sh     # sha256-verified weights, never committed
> cd android && ./gradlew :app:assembleRelease
> adb install -r app/build/outputs/apk/release/app-release.apk
> ```
>
> A sample deck ships with the app, so the first launch has something to ask questions
> about. Open any text-based PDF to use your own.
>
> **Reproduce the benchmarks on your own Arm phone:** `docs/REPRODUCE.md` gives both
> build configurations, the exact sweep command with its repetition count, and how to
> read a sign-changing difference as noise. If you have an i8mm device we want your CSV —
> the claim is that one hard-coded configuration cannot be right for every Arm phone, and
> disagreement across devices is the useful outcome.

---

## 2. Pre-submission checklist

- [ ] Repo **public**, GitHub About shows **Apache-2.0**
- [ ] **Mobile AI** track selected
- [ ] Video ≤ 3:00, **public** on YouTube, real device, no speed-ups
- [ ] Video URL pasted into Devpost (a private/unlisted link fails eligibility)
- [ ] `README.md` renders on GitHub — confirm the charts and screenshots load
- [ ] `docs/REPRODUCE.md` linked from the README
- [ ] "Built with Llama" present in README and NOTICE
- [ ] No claim of i8mm, SME2, NPU, or Performix anywhere
- [ ] Every latency figure traceable to the CSV or visible on screen in the video

## 3. Positioning

An offline RAG study app **has been submitted to an Arm challenge before** — Pinguin,
Arm AI Developer Challenge: Electron + LangChain + ChromaDB + Ollama on a Snapdragon X
Elite laptop, 30–50 s per query, no prize.

So do not sell the idea. Sell:

1. **The measurement** — KleidiAI inert on Armv8.2-A, proven by log line and zero-delta
   A/B, displayed live on the judge's own hardware.
2. **The device class** — a 2020 midrange phone, not a laptop-class flagship.
3. **The latency** — ~11 s to first word against their 30–50 s, with retrieval shown on
   screen in single-digit milliseconds.

If asked why there is no Word/EPUB/OCR ingest: OCR costs 20–30 minutes on that hardware.
It was a latency decision, not a missing feature.

---

## 4. README structure

Order matters. Assume a judge reads the first screenful and decides.

1. **Title + one line** — what it does, on what hardware, offline
2. **Three screenshots** — ask, cards, settings
3. **The KleidiAI finding**, above the fold, with the A/B table and the log line
4. **The prefill≈2×decode measurement** and the design that follows from it
5. **Thread scaling, cluster split, Q4_0 repack** — the rest of the optimization work
6. **Getting the answer right** — heading-aware ranking, the floored budget, card checks
7. **Quickstart**, copy-pasteable
8. **Reproduce on your device** → `docs/REPRODUCE.md`
9. **What we cut and why** — signals judgement, preempts "where's the OCR?"
10. **Licence + "Built with Llama"**
