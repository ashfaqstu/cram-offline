# Demo script

Two things live here: a **validation pass** to confirm the app works before recording,
and the **video script** itself.

The video is ≤ 3:00, public on YouTube, recorded on a real device with no speed-ups.
Screen-record the phone (MIUI: Control Centre → Screen Recorder, or `adb shell screenrecord`).

---

# Part 1 — Validation pass

Run this before recording. It takes about five minutes and catches everything that has
broken before.

| # | Do this | Pass looks like |
|---|---|---|
| 1 | Cold-start the app | Landing page, sample deck listed, no crash |
| 2 | Settings tab | `prefill` shows a real number, not `...` or `0 t/s` |
| 3 | Ask → tap *"What are the four Coffman conditions?"* | **All four**: mutual exclusion, hold and wait, no preemption, circular wait |
| 4 | Look under the answer | `evidence` in single-digit ms, `first word` 10–15 s, slide number shown |
| 5 | Tap *"Make flashcards from slide N"* | Jumps to Study, scope pre-set to that slide, starts generating |
| 6 | Switch to Slides and back to Study | **Cards are still there** (this used to lose them) |
| 7 | Reveal a card | Back explains, never repeats the front. Provenance chip shows a slide |
| 8 | Open your own PDF | Suggested questions come from *your* deck, not the sample's |

**Item 3 is the one that matters.** A truncated context used to make the model invent
two of the four conditions — a confident, wrong, well-formatted answer. If any condition
is missing or invented, stop and check the character budget in Settings before recording.

If a deck has no real slide headings, the Topic picker offers a one-time LLM pass to
generate them. That takes a minute or two; do it **before** recording, not during.

---

# Part 2 — Video script

## The shape

Lead with the finding, not the app. Every submission in this track shows an app; almost
none shows a measurement that contradicts the platform vendor's own marketing. An
offline RAG study app has been built before — the reason to watch this one is the
number.

| Time | Beat |
|---|---|
| 0:00–0:20 | The problem, on the real phone |
| 0:20–1:00 | It works — ask a question, get a cited answer |
| 1:00–1:30 | Flashcards from the same slides |
| 1:30–2:30 | **The finding** — KleidiAI is inert here, and what we did instead |
| 2:30–3:00 | Privacy, and close |

## 0:00–0:20 — Open on the phone, not on slides

Hold up the phone. Airplane mode visible in the status bar.

> "This is a Poco M2 Pro from 2020. Two fast cores, six slow ones, no NPU, no i8mm.
> It's the phone most students actually own — and it's in airplane mode."

Open Cram. The sample deck is already there.

> "It's exam week. These are my lecture slides, and I have a question."

**Do not explain the architecture yet.** Show it working first.

## 0:20–1:00 — Ask, and show the evidence

Tap *"What are the four Coffman conditions?"*

The moment to point at is **the evidence appearing first**:

> "The matching slide comes back in five milliseconds. That's not the model — that's
> BM25 ranking every passage in the deck. The model is only there to phrase it."

Then the answer completes.

> "Four conditions, all correct, and it tells me they came from slide four. I can check
> its work. It cannot make something up without showing me where it didn't come from."

**Let the wait be visible.** Do not cut it. An honest eleven seconds on a six-year-old
phone is more credible than a suspicious jump cut, and the on-screen `first word` timer
makes any edit obvious anyway.

## 1:00–1:30 — Flashcards, via the cross-link

Tap **"Make flashcards from slide 4"** directly under the answer.

> "The question I just asked becomes the thing I revise. Same slides, same retrieval —
> it never leaves the document."

Reveal a card. Show the `from slide 4` chip.

> "Every card knows which slide it came from."

## 1:30–2:30 — The finding

Open **Settings**. Point at the *Measured on first run* card.

> "Arm markets KleidiAI as CPU acceleration for on-device AI. On this chip it does
> nothing at all — and the app says so, about your own phone."

Show the log line (cut to a terminal or an overlay):

```
kleidiai: no compatible q4 kernels found for CPU features mask 1
```

> "Its int4 kernels need i8mm or SME. Armv8.2-A has neither, so GGML silently falls
> back. We didn't just read the log — we built the same binary twice, with and without."

Show the A/B table: 18.75 vs 17.48 prefill, 8.82 vs 9.17 decode.

> "Seven percent apart, and decode is *faster* with it switched off. That's noise, not
> acceleration. That cliff runs straight through the mid-tier install base."

Then the payoff — where the speed did come from:

> "So we measured what does work. Eight threads makes decode fifty-eight percent
> *slower* than six, because the fast cores wait on the slow ones at every layer
> barrier. Prefill wants eight, decode wants six, so we run both.
>
> And prefill here is only twice decode — not the fifty times you get on a GPU. So
> the app retrieves from the whole deck and sends almost none of it. That's what took
> first word from thirty-four seconds to eleven."

Show the Brief / Balanced / Thorough presets.

> "And it doesn't hard-code any of that. It times a real prefill on *your* phone at
> startup and sizes itself to it."

## 2:30–3:00 — Close

> "No internet permission. No storage permission. Not a promise in a privacy policy —
> the app is structurally incapable of sending your notes anywhere.
>
> Everything you just saw ran on a six-year-old phone, in airplane mode, on the CPU."

---

## Recording notes

- **Plug the phone in and let it cool** before recording. Thermal throttling moves
  first-word latency from 11 s to 15 s and the timer is on screen.
- **Close Messenger and anything else running.** A background app measurably slows
  prefill, and a notification banner mid-take costs you the whole recording.
- Turn on **Do Not Disturb**.
- Airplane mode must be **visible in the status bar** during the ask — it is the claim.
- Record at 1080p portrait; do not crop out the status bar.
- The `evidence N ms` / `first word N.Ns` line must be legible. If it isn't, the central
  claim is unverifiable to a viewer.

## Do not say

- ❌ "KleidiAI is broken" — it is inert *on this hardware*, and correct on i8mm/SME
- ❌ "accelerated by i8mm / SME2" — this chip has neither
- ❌ any latency figure not visible on screen at that moment
- ❌ "the first app to do this" — it isn't, and the measurement is the point
