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

## What actually won last time, and what it means for us

From [Arm's own announcement](https://newsroom.arm.com/blog/arm-ai-dev-challenge) of the
first Arm AI Developer Challenge — 142 submissions, six winners. Judged on
**technological implementation, user experience, potential impact, and "wow" factor**.

| Place | Project | What it was |
|---|---|---|
| 1st | **Chuck'it** | On-device bookmarking — save a screenshot or link, AI organises and semantically searches it |
| 2nd | DreamMeridian | Natural-language map queries on a Raspberry Pi, offline |
| 2nd | InstaMeme | Photos → memes, fully on-device iOS |
| 3rd | **Jackqr** | **On-device study tool: scanned PDFs → clean searchable text, simplification, flashcards, spaced repetition** |
| 3rd | Epictetus | Android chatbot **using KleidiAI**, XNNPack, MediaPipe |
| 3rd | Pocket Garden | Gardening advice on a Pi |

Three things to take from that list, and they should shape every second of the video.

**1. Our category has already been done, and it placed third.** Jackqr is a
flashcard-generating offline study app. Being an offline study app is therefore worth
about third place. *The idea is not the pitch.* If the video spends its first minute
explaining that studying from slides is hard, we are competing on the axis Jackqr
already won and lost on.

**2. The winner was instantly graspable.** Chuck'it can be explained in one sentence
with one gesture — save a thing, find it later, privately. Not "more features": one
loop, shown working. Cram's equivalent is **ask → cited answer → flashcard → drill**,
and it must be shown as one continuous motion, not as a tour of five tabs.

**3. This year the theme changed, and it changed in our favour.** That contest was the
*AI Developer* Challenge. This one is the *AI **Optimization*** Challenge. Measurement
is no longer a supporting detail — it is the subject. Jackqr's feature list would score
the same today; our KleidiAI result would not.

And one pointed detail: a third-place project last time **used KleidiAI as a selling
point**. We have measured, on hardware two-thirds of the Android market is holding,
that it does nothing at all — with the library's own log line as the witness. That is
not a feature. That is a finding, and no app-shaped submission can manufacture one.

## The shape

**Lead with the finding, not the app.** Every entry shows an app. Almost none shows a
measurement that contradicts the platform vendor's own marketing on the vendor's own
hardware.

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

## 1:00–1:30 — The loop, in one unbroken take

**Do not cut during this.** It is the Chuck'it lesson: one gesture, one loop, obviously
useful. Tap **"Make flashcards from slide 6"** directly under the answer.

> "The question I just asked becomes the thing I revise."

Cards appear. Reveal one — show the `from slide 6` chip.

> "Every card knows which slide it came from. And when I get one wrong—"

Tap **Practise**, mark one wrong, finish, and land on **"Drill the 3 I missed"**.

> "—it remembers, and drills only those. Close the app, come back: still there."

Then the coverage strip on Ask:

> "And it tells me what I haven't looked at yet. That's the actual question at 1 a.m. —
> not 'what is a deadlock', but 'what have I still not opened'."

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
- ❌ "the first app to do this" — Jackqr placed third doing something adjacent, and a
  judge who knows that will stop trusting the rest of the video

## The thumbnail and title

The judge sees these before a single frame. Put the finding in both.

- **Title:** `Cram — I measured Arm's KleidiAI on a 2020 phone. It does nothing.`
- **Thumbnail:** the phone in hand, airplane mode visible, one line of text:
  **"KleidiAI: 0% faster"** — with the app's answer on screen behind it.

Not "Cram: an offline study app". That title is Jackqr's, and it came third.
