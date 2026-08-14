# Devpost submission — copy and paste

Every field Devpost asks for, filled in. Text between the rules is meant to be pasted
as-is. Numbers trace to `bench/results/sweep_POCO_M2_Pro.csv` or to the app's on-screen
telemetry — **do not add a claim that doesn't.**

**Before submitting:** repo public · GitHub About shows Apache-2.0 · Release tagged with
`cram.apk` attached · video public on YouTube · **Mobile AI** track selected.

---

## Project name

```
Cram
```

## Tagline (Devpost: "elevator pitch", ~200 chars)

```
Ask your lecture slides a question and get an answer with the slide number — offline, on a 2020 budget phone. Built to prove where Arm CPU speed actually comes from, because KleidiAI does nothing here.
```

---

## Inspiration

It is the night before an exam, you have forty slides and no time, and the thing you
need is not a chatbot — it is the one slide that answers your question, plus proof it
really said that.

Every on-device LLM demo this year runs on a Snapdragon 8 Elite or an iPhone 17: silicon
with i8mm or SME2 matrix extensions. Those chips are a small minority of the world's Arm
devices. We wanted to know what is actually achievable on the phone a student in Dhaka
is holding — so we built on a **Poco M2 Pro**: Snapdragon 720G, 2× Cortex-A76 + 6×
Cortex-A55, Armv8.2-A, **no i8mm, no SVE, no SME, no NPU**, 6 GB RAM, released 2020.

Then Arm's own acceleration library turned out to do nothing on it, and that became the
more interesting project.

---

## What it does

Open a lecture PDF. Ask it anything.

- **Ask** — BM25 ranks every passage in the deck in **1–7 ms**, the matching slide
  appears *before* the model writes a word, and the answer cites the slide it came from.
- **Study** — turn the same slides into flashcards, scoped by whole deck, page range, or
  a topic picked from the deck's own headings. Every card names its source slide.
- **Practise** — self-grade, then **"Drill the 3 I missed"**. It remembers what you got
  wrong across sessions.
- **Where you are** — *"5 of 11 slides covered. Not looked at yet: 1, 2, 4, 7, 8, 10."*
  Cramming's real question is not "what is X" but "what have I still not opened".
- **Source** — read the original PDF or the extracted text, so you can check the working.

No network. No account. Nothing uploaded — the app declares **no `INTERNET` permission
at all**, which you can verify in App info or in a manifest that contains no
`<uses-permission>` line of any kind.

---

## The finding: KleidiAI is inert on Armv8.2-A

Arm markets **KleidiAI** as CPU acceleration for on-device AI. Its int4 microkernels
require **i8mm or SME**. Armv8.2-A has dotprod and neither — so GGML silently falls back
to generic kernels and nothing is accelerated.

The library says so itself, in a log line nobody reads:

```
kleidiai: no compatible q4 kernels found for CPU features mask 1
kleidiai: SME disabled
```

We did not stop at the log. We built the **same binary twice**, with the library on and
off, and ran both back to back on the same phone:

| Q4_0 weights | prefill tok/s | decode tok/s |
|---|---:|---:|
| KleidiAI **ON** | 18.75 | 8.82 |
| KleidiAI **OFF** | 17.48 | 9.17 |

Seven percent apart **with no consistent sign** — decode is marginally *faster* with it
switched off. That is run-to-run noise, not acceleration.

**This cliff runs straight through the mid-tier install base.** If you are shipping to
Armv8.2-A phones, KleidiAI will not save you, and the gains have to come from somewhere
else. The app shows this about *your* phone on its Settings screen rather than asking
anyone to take our word for it, and `docs/REPRODUCE.md` tells you how to disprove us.

**Scope, stated honestly:** the Q4_0/Q8_0 guidance *is* correct on i8mm or SME hardware.
We are not saying KleidiAI doesn't work — we are saying it does not engage here, and
"here" is a very large number of phones. Even a "Q4_0" GGUF carries `q6_K` embedding and
output tensors, so coverage would be partial even with i8mm.

---

## How we built it

**The measurement that shaped everything.** On a desktop GPU, prefill runs 10–50× faster
than decode, and every "just stuff more context in" RAG design assumes it. On this CPU
**prefill is only about twice decode — roughly 14 ms per character of prompt.** Sending
eight retrieved passages would cost half a minute before the first word. So Cram inverts
the usual design:

- **Retrieve many, send few.** BM25 ranks the whole deck in 1–7 ms; only the best one or
  two passages are ever paid for in tokens.
- **Greedy budget allocation.** The top passage takes what it needs and the runner-up
  gets the remainder — an even split truncated the winner to make room for a passage
  that went unused.
- **Evidence first.** The matching slide renders while the model is still writing. The
  retrieval *is* the answer for most questions; the model only phrases it.
- **KV prefix cache.** The system prompt is identical every time and about a hundred
  tokens — a quarter of the prefill. Keeping it resident and re-sending only the
  question took first word from **14.8 s to 12.6 s**.

First word overall: **34.5 s → ~11 s**, with retrieval never exceeding 7 ms.

**Threads, measured across 24 cells:**

| threads | prefill pp128 | decode tg32 |
|---:|---:|---:|
| 6 | 18.74 | **10.95** |
| 8 | **20.48** | **4.57 — a 58% collapse** |

Using all 8 cores makes decode **58% slower** than using 6: every layer ends in a
barrier, so the two fast A76s sit waiting on the six slow A55s. Prefill and decode want
opposite thread counts, so we run 8 and 6 respectively — and derive both from the
device's own core split rather than hard-coding ours.

**It sizes itself to the phone it is on.** At startup Cram times a real prefill and
derives its prompt budget from the result. Settings shows the measurement, the values
chosen from it, and the cost in seconds of overriding them.

**Q4_0 over Q4_K_M — for the right reason.** +15% prefill (18.1 vs 15.7), and it holds
with KleidiAI disabled in *both*. The gain is GGML's own aarch64 weight-repack path
(`use_extra_bufts`), not KleidiAI. Most write-ups get the recommendation right and the
reason wrong — our own earlier draft did too, until the A/B disproved it.

**Stack:** llama.cpp (GGML) · Llama 3.2 1B Instruct Q4_0 · `n_ctx` 2048 · BM25, no
embeddings and no vector DB · PdfBox-Android for text extraction · Kotlin + Jetpack
Compose. One JNI translation unit, ~440 lines.

---

## Challenges we ran into

**The same question gave different answers.** Sampling used a random seed per call, so
*"what algorithm avoids deadlock"* returned the Banker's algorithm once and something
else the next time — from identical retrieved text. A study tool you cannot trust twice
is not a study tool. Switching to greedy decoding fixed it *and* exposed two real
retrieval bugs that randomness had been masking: no stemming, so `avoids` could never
match `avoidance`; and a runner-up threshold loose enough that the "Deadlock handling
strategies" slide out-voted the slide that actually names the algorithm. Retrieval was
never wrong — it was drowned out.

**Optimizing for speed produced confident lies.** Calibrating purely on latency drove the
character budget to ~400, which truncated a four-item list — so the model answered fast
and *invented two of the four Coffman conditions*. The budget is now floored at 900 and
the Settings ladder only climbs from there. Speed that costs correctness is not a saving.

**A 1B model refused to write flashcards.** The prompt opened with stacked prohibitions
("Use ONLY the excerpts. Do not invent facts.") and Llama 3.2 1B replied that it could
not help with content that may promote illegal activities — terse prohibitions read like
a jailbreak to a small, heavily safety-tuned model. Phrasing it as a student's request
gets identical grounding and no refusal.

**Then it plagiarised our example.** Our worked example used deadlock terms, and the
model copied the example's answer verbatim into a real card: correct-sounding, and
unrelated to the slide it cited. The examples are now about plant biology, which no deck
here is about.

**MIUI inverted the whole app.** Answer text rendered white-on-white, which looked like a
Compose bug and was force-dark. One manifest line.

---

## Accomplishments we're proud of

A **negative result, properly measured** — the log line, a same-binary A/B, and a
reproduction guide so anyone can falsify it on their own phone. Negative results are
rarer than features and harder to fake.

An app that is **usable on six-year-old hardware**: ~11 s to first word, retrieval in
single-digit milliseconds, and a device-adaptive configuration that does not assume our
phone.

**Privacy that is structural rather than promised.** No internet permission, no storage
permission. It cannot leak your notes even by mistake.

---

## What we learned

Vendor acceleration libraries have hardware floors that their marketing does not mention,
and the failure mode is *silence* — a log line and a graceful fallback, not an error. If
you have not A/B'd it on your actual target, you do not know whether it is doing
anything.

And the profile of a phone CPU is not a small GPU. Prefill being only 2× decode inverts
the standard RAG trade-off: on this hardware, retrieval quality is cheap and context is
expensive, so the winning move is to retrieve aggressively and send almost nothing.

---

## What's next for Cram

Reproductions on **i8mm hardware** — the claim is that no single configuration is right
for every Arm phone, so disagreeing CSVs are the useful outcome. Then caching the passage
prefix for follow-up questions on the same slide, and a KV cache A/B at `q8_0`.

---

## Built with

```
kotlin, jetpack-compose, llama.cpp, ggml, llama-3.2, arm, kleidiai, android, ndk, c++, bm25, pdfbox
```

## Links

- **Repo:** https://github.com/ashfaqstu/cram-offline
- **Video:** *(paste the public YouTube URL)*
- **Try it:** APK on the Releases page; the README has a phone-only install path
- **Reproduce the benchmarks:** `docs/REPRODUCE.md`

## "Built with Llama"

Required by the Llama 3.2 Community License, and present in `README.md` and `NOTICE`.
Model weights are not redistributed.
