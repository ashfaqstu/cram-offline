# Cram

**Ask your lecture slides a question and get an answer with the slide number, offline, on a 2020 budget phone.**

**3× faster to first word than our first working build — and none of it came from Arm's own KleidiAI, which we measured doing nothing at all on this CPU.**

Open a PDF of a lecture. Ask it anything. Cram finds the passage that answers you, shows you which slide it came from, and writes the answer from that passage — no network, no account, no upload. Then it turns the same slides into flashcards you can revise from.

Built and measured on a **Poco M2 Pro** — Snapdragon 720G, 2× Cortex-A76 + 6× Cortex-A55, **Armv8.2-A with no i8mm, no SVE, no SME, no NPU path**, 6 GB RAM. Not a flagship. The phone most of the world is actually holding.

| | | |
|---|---|---|
| ![Asking a question](bench/results/ui_ask.png) | ![Flashcards](bench/results/ui_cards.png) | ![Settings](bench/results/ui_settings.png) |
| Answer, with the slide it came from | Cards made from the slides you chose | What it measured about your phone |

---

## What we optimized, and by how much

**Time to first word: 34.5 s → ~11 s. Three times faster on hardware we did not change** — same phone, same model, same weights. Every line below was measured on the device, not estimated.

| Change | Measured effect | Where you can check it |
|---|---|---|
| **Retrieve many, send few** — BM25 ranks the whole deck, only 1–2 passages are ever sent | the bulk of **34.5 s → ~11 s** | `first word` timer, on screen |
| **Split prefill/decode thread counts** (8 and 6) | avoids a **58%** decode collapse | [`sweep_POCO_M2_Pro.csv`](bench/results/sweep_POCO_M2_Pro.csv) |
| **Q4_0 + GGML aarch64 repack** instead of Q4_K_M | **+15%** prefill (18.1 vs 15.7 tok/s) | [`sweep_POCO_M2_Pro.csv`](bench/results/sweep_POCO_M2_Pro.csv) |
| **KV prefix cache** for the system prompt | 14.8 s → 12.6 s (**~15%**) | `first word` timer, on screen |
| **Greedy budget allocation** | stopped truncating the winning passage | answer correctness |
| **Startup device calibration** | prompt budget sized to *your* phone, not ours | Settings screen |
| **Greedy decoding** | identical answer to an identical question | ask it three times |
| ~~KleidiAI~~ | **0%** — it does not engage on this CPU | same-binary A/B, below |

> The first-word figures come from different sessions on the same phone, so they do not add up into a clean waterfall — thermal state moves them a second or two either way. The end-to-end **34.5 s → ~11 s** is the number the app prints on its own screen, and `docs/REPRODUCE.md` is how you check the rest.

And the last row is the one worth the rest of this page.

---

## The finding we'd tell every Arm developer

**None of that speedup came from where Arm tells you to look.**

Arm markets **KleidiAI** as CPU acceleration for on-device AI. On this device it does nothing at all — and it says so in a log line nobody reads:

```
kleidiai: no compatible q4 kernels found for CPU features mask 1
kleidiai: no compatible q8 kernels found for CPU features mask 1
kleidiai: SME disabled
kleidiai: no kernel for tensor type q6_K, not accelerated by KleidiAI
          (kernels available for Q4_0 and Q8_0)
```

KleidiAI's int4/int8 microkernels require **i8mm or SME**. Armv8.2-A cores have dotprod and neither of those — so GGML silently falls back to generic kernels. We measured it rather than quoting it:

| Q4_0 weights | prefill tok/s | decode tok/s |
|---|---:|---:|
| KleidiAI **ON** | 18.75 | 8.82 |
| KleidiAI **OFF** | 17.48 | 9.17 |

Differences of ±7% **with no consistent sign** — decode is marginally *faster* with it off. That is run-to-run noise, not acceleration.

**The cliff falls straight through the mid-tier install base.** If you are deploying to Armv8.2-A phones, KleidiAI will not save you; the gains have to come from somewhere else.

The app tells you this about *your* phone, on the Settings screen, rather than asking you to take our word for it.

> **Scope, stated honestly:** the Q4_0/Q8_0 guidance *is* correct on i8mm or SME hardware. We are not saying KleidiAI doesn't work — we are saying it doesn't engage here, and "here" is a very large number of phones. Note too that even a "Q4_0" GGUF carries `q6_K` embedding and output tensors, so coverage would be partial even with i8mm.

![KleidiAI makes no difference on Armv8.2-A](bench/results/chart_kleidiai.png)

---

## The measurement that shaped the whole app

On a desktop GPU, prefill (reading your prompt) runs 10–50× faster than decode (writing the answer). Every "just stuff more context in" RAG design assumes that ratio.

**On this CPU, prefill runs at roughly twice decode — about 14 ms per character of prompt.**

That single number rules out the standard approach. Retrieving eight passages and sending them all would cost half a minute before the first word appeared. So Cram is built the other way around:

- **Retrieve many, send few.** BM25 ranks every passage in the deck in **1–7 ms**; only the best one or two are ever paid for in tokens.
- **Greedy budget allocation.** The top passage takes as much of the character budget as it needs, and the runner-up gets whatever is left — rather than splitting evenly, which truncated the winner to make room for a passage that wasn't going to be used.
- **Evidence first.** The matching slide text appears while the model is still writing. The retrieval *is* the answer for most questions; the model is there to phrase it.

First word went from **34.5 s to ~11 s** — 3× — with retrieval itself never exceeding 7 ms.

### The app sizes itself to the phone it is on

A budget baked in on our device is wrong on every other one. At startup Cram times a real prefill and derives its own prompt budget from the result. The Settings screen shows the measurement, the values chosen from it, and lets you override them with the cost of each choice in seconds.

---

## Where the rest of the speed came from

### More threads is not more speed

![Thread scaling and the decode collapse](bench/results/chart_threads.png)

| threads | prefill pp128 | decode tg32 |
|---:|---:|---:|
| 2 | 12.75 | 9.09 |
| 4 | 15.95 | 10.21 |
| 6 | 18.74 | **10.95** ← decode optimum |
| 8 | **20.48** ← prefill optimum | **4.57** ← **58% collapse** |

Two results worth carrying away:

1. **Using all 8 cores makes decode 58% slower than using 6.** Every layer ends in a barrier, so the two fast A76s spend their time waiting on the six slow A55s. Adding cores adds stalls.
2. **Prefill and decode want opposite thread counts** — prefill is compute-bound and scales to 8, decode is memory-bound and peaks at 6. llama.cpp exposes both (`n_threads`, `n_threads_batch`), so we run 6 and 8 respectively. This matters more here than in a chat app: reading the slides and writing the answer are separate phases of every single query.

### Two big cores beat all six little ones

![Cluster comparison](bench/results/chart_clusters.png)

The A76 pair delivers **2.5× the decode throughput of the entire A55 cluster** (8.64 vs 3.41 tok/s). This also corrected our own plan, which assumed decode belonged pinned to the big cluster. It doesn't: 6 unpinned threads (10.95) beat 2 pinned big threads (8.64), because the little cores do contribute real work at this model size.

> **A constraint worth knowing:** GGML fixes a thread pool's CPU affinity when the pool is created, and worker threads inherit it from whoever calls the load. Affinity must be set *before* the model loads, on the thread that will own it — and cannot be changed afterwards.

### Q4_0 over Q4_K_M — but not for the reason usually given

Q4_0 averages **18.1 tok/s prefill vs 15.7 for Q4_K_M (+15%)** — and that holds with KleidiAI *disabled in both*. The gain comes from **GGML's own aarch64 weight-repack path** (`use_extra_bufts`), not from KleidiAI. Right answer, wrong reason in most write-ups.

---

## Getting the answer right, not just fast

A fast wrong answer at 2 a.m. is worse than a slow right one. Three things Cram does that a generic PDF chatbot does not:

**Headings are ranked, not just matched.** Slide decks are written as "title, then content", so a slide called *The four Coffman conditions* is almost certainly the answer to *what are the four Coffman conditions*. Plain BM25 ranked a short Summary slide above the real definition, because length normalisation punished the longer slide that actually held the list. Heading terms and list structure carry extra weight.

**The budget is floored, not minimised.** Tuning purely for speed drove the character budget down to ~400 — not enough to hold a four-item list, so the model answered quickly and *invented two of the four conditions*. The floor is now 900 characters, and the Settings ladder only climbs from there. Speed that costs correctness isn't a saving.

**Cards are checked before they're shown.** A card whose back merely restates its front teaches nothing, and a slide that is only a list of bare terms tempts a 1B model into producing exactly that. Those are dropped rather than padded out. Every card and every answer carries the slide it came from, so you can check the working.

**The same question gives the same answer.** Decoding is greedy, not sampled. With a random seed per call, *"what algorithm avoids deadlock"* came back as the Banker's algorithm once and the ostrich algorithm the next time, from identical retrieved text — a study tool you cannot trust twice is not a study tool. Fixing it also exposed two real retrieval bugs that sampling had been hiding: no stemming (so `avoids` could not match `avoidance`), and a runner-up passage loose enough to out-vote the winner.

---

## Cramming is a session, not a query

The night before an exam you do not know what you do not know. So Cram remembers:

- **Cards survive.** They cost 90 seconds of the phone's time to write; they are stored with the deck, not held in memory until the app is backgrounded.
- **It remembers what you got wrong.** Finish a round and the next action is *"Drill the 3 I missed"* — because the highest-value thing a crammer can do is stop re-reading what they already know.
- **It tells you what is left.** *"5 of 11 slides covered. Not looked at yet: 1, 2, 4, 7, 8, 10."* Every answer already knew which slide it came from and every card which slide made it; that just used to be thrown away.

The sample deck ships with the app, so the first thing you see is a real question answered from real slides.

---

## Install it (no build required)

You need an **arm64 Android phone** (Android 9+, 3 GB RAM or more) and about **900 MB free**.

### 1. Install the app

Download **`cram.apk`** from [Releases](../../releases/latest) and open it on the phone. Android will warn about installing outside the Play Store — that is expected for a sideloaded APK; allow it for your browser or file manager.

*On Xiaomi/MIUI:* also turn on **Settings → Additional settings → Developer options → Install via USB** if you install over adb, or MIUI silently blocks it with `INSTALL_FAILED_USER_RESTRICTED`.

### 2. Get the model

Cram runs **Llama 3.2 1B Instruct, Q4_0** (~770 MB). We do not redistribute the weights.

Open Cram. On first run it shows a setup screen with two buttons:

1. **Download the model (opens your browser)** — hands the Hugging Face link to your browser, which downloads it to your Downloads folder. *(Hugging Face may ask you to accept Meta's licence first.)*
2. **Choose the model file** — opens the system file picker. Select the `.gguf` you just downloaded. Cram copies it into its own storage (a few seconds) and starts, so you can delete the download afterwards.

If you would rather fetch it yourself, it is
**[Llama-3.2-1B-Instruct-Q4_0.gguf](https://huggingface.co/bartowski/Llama-3.2-1B-Instruct-GGUF/resolve/main/Llama-3.2-1B-Instruct-Q4_0.gguf?download=true)**, and to check it is the file we measured against:
`sha256 = fa0390e7c043f89ae1847bd6682d748041a99d4ef3de0e0b27d33b6af97a8be8`

That is the whole setup. A sample lecture deck ships inside the app, so you can ask a question immediately without finding a PDF.

> ### Why doesn't it download the model itself?
>
> The button above opens your **browser**, and the download happens there — in an app that already has network access. Cram fetching the file itself would require an `INTERNET` permission, and this app declares **none at all**. That is what makes "your notes cannot leave this phone" a property of the app rather than a promise in a policy — verify it under **App info → Permissions**, or read [AndroidManifest.xml](android/app/src/main/AndroidManifest.xml), which has no `<uses-permission>` line of any kind.
>
> Handing a URL to the browser needs no permission, and the system file picker grants access to exactly the one file you choose. The cost is one tap back into the app. We think that is a fair price for the only guarantee here that nothing else offers.

<details>
<summary><b>Alternative: install the model over adb</b> (skips the in-app picker)</summary>

```bash
adb install -r cram.apk
adb push Llama-3.2-1B-Instruct-Q4_0.gguf \
         /sdcard/Android/data/dev.omnitalk/files/Llama-3.2-1B-Instruct-Q4_0.gguf
```

The filename must match exactly. That directory is app-specific external storage, so it needs no runtime permission and is removed when the app is uninstalled.
</details>

## Build it from source

```bash
git clone --recursive https://github.com/ashfaqstu/cram-offline && cd cram-offline
./scripts/fetch_models.sh            # downloads + sha256-verifies weights (never committed)
cd android && ./gradlew :app:assembleRelease
adb install -r app/build/outputs/apk/release/app-release.apk
```

Needs JDK 21 and the Android NDK. `scripts/setup.sh` provisions the whole toolchain if you would rather not install Android Studio (we never did).

**Reproduce the benchmarks on your own Arm phone:** [docs/REPRODUCE.md](docs/REPRODUCE.md) — both build configurations, the exact sweep, and how to read a sign-changing difference as noise. Prebuilt `llama-bench` binaries with and without KleidiAI are in [`prebuilt/`](prebuilt/) so you can run the A/B without an NDK. Open an issue with your device's CSV and we'll add it to the table.

---

## What we cut, and why

- **A vector database and an embedding model.** BM25 answers these decks in 1–7 ms. An embedding model would add a second model to load, hundreds of megabytes of RAM, and a prefill cost per chunk — to improve ranking on a corpus small enough that lexical overlap is already decisive.
- **Multi-turn chat.** Every retained turn is prompt tokens re-read at 14 ms per character. A study tool is asked questions, not engaged in conversation.
- **Mind maps.** A diagram is a lot of generated tokens for something a reader skims once; the slide list already gives the structure.
- **ExecuTorch** — no grammar-constrained decoding, and a ~1.9 GB RSS `.pte` does not fit safely in 6 GB.
- **Arm Performix** — it targets Neoverse server platforms, not phones.

---

## Licence

Apache-2.0 — see [LICENSE](LICENSE) and [NOTICE](NOTICE).

**Built with Llama.** Model weights are not redistributed; `scripts/fetch_models.sh` downloads and verifies them. llama.cpp and ggml are MIT; KleidiAI is Apache-2.0 and vendored under `third_party/` so the native build never needs the network. PDF text extraction uses PdfBox-Android (Apache-2.0).
