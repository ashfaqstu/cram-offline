# Demo script — shot by shot

A 3:00 video, public on YouTube, recorded on the real phone, no speed-ups.
Screen-record the phone (MIUI: Control Centre → Screen Recorder, or
`adb shell screenrecord`).

Everything below is timed against **measured** behaviour on the Poco M2 Pro. If
you follow it in order and read the lines as written, it lands at 3:00.

---

# Part 1 — Prep. Do all of this before you press record.

Ten minutes. Skipping any of it costs you a re-shoot.

### The phone

- [ ] **Plug it in and let it cool.** Thermal throttling moves first word from
      9 s to 15 s, and the timer is on screen.
- [ ] **Do Not Disturb ON.** A notification banner mid-take costs the whole recording.
- [ ] **Force-stop Messenger, Gmail, Chrome.** A background app measurably slows
      prefill. One notified during our testing at 02:27 — don't let it be your take.
- [ ] **Airplane mode ON**, and confirm the icon is visible in the status bar.
      It is the claim; it must be legible.
- [ ] Record **1080p portrait**. Do not crop out the status bar.

### The app

- [ ] Open **Settings**. If you see a red **`modified`** chip, tap
      **Reset to measured**. The subtitle under "Settings" must read from the
      measurement, not "Tuned by hand" — a hand-tuned app contradicts the whole
      self-calibration point you are about to make.
- [ ] **Pre-generate the flashcards.** Ask the four-conditions question, tap
      **"Make flashcards from slide 4"**, and let it finish. **This takes about
      90 seconds** — that is why it is not shot live. The cards are saved with the
      deck, so they will still be there when you record.
- [ ] In **Practise**, mark a couple wrong so **"Drill the N I missed"** is
      showing before you record.
- [ ] Force-stop the app so the video opens cold.

### The three-times determinism check — 90 seconds, do not skip

Ask **"what algorithm avoids deadlock"** three times, touching nothing between.
All three must read character-for-character identical. If any differs, decoding
has regressed to sampling — check `llama_sampler_init_greedy` is still in
`native/otjni.cpp`. This is the one bug that would lose it on camera.

### Measured timings — so you know what you are waiting for

| Question | First word | Answer runs for |
|---|---|---|
| What are the four Coffman conditions? | **9.0 s** | ~13 s after that |
| what algorithm avoids deadlock | **11.9 s** | ~7 s after that |
| when is assignment 3 due | **7.3 s** | ~2 s after that |

---

# Part 2 — What actually won last time

From [Arm's announcement](https://newsroom.arm.com/blog/arm-ai-dev-challenge) of
the first Arm AI Developer Challenge — 142 submissions, six winners, judged on
technological implementation, UX, potential impact and "wow".

| Place | Project | What it was |
|---|---|---|
| 1st | **Chuck'it** | On-device bookmarking — save a thing, find it later, privately |
| 3rd | **Jackqr** | **Offline study tool: PDFs → text, simplification, flashcards** |
| 3rd | Epictetus | Android chatbot **using KleidiAI** as a selling point |

Three things that shape every second below.

1. **Our category already placed third.** Being an offline study app is worth
   about third. *The idea is not the pitch.* Do not spend the opening explaining
   that studying is hard.
2. **The winner was graspable in one sentence and one gesture.** Not more
   features — one loop, shown working.
3. **The theme changed in our favour.** That was the *Developer* Challenge; this
   is the ***Optimization*** Challenge. Jackqr's feature list scores the same
   today. Our measurement does not — and a third-place project last year sold
   KleidiAI as a feature that we have measured doing nothing.

**Lead with the number. The finding is the twist.** Open on 34.5 s → 11 s, and let
KleidiAI arrive thirty seconds later as the reason to believe it. Opening on the
negation invites "you measured, you didn't optimize."

---

# Part 3 — The shot list

| Time | On screen | Beat |
|---|---|---|
| 0:00–0:14 | Phone in hand, airplane mode | The hardware, and the question |
| 0:14–0:36 | Answer generating, then complete | Evidence first, four conditions, cited |
| 0:36–0:52 | Answer still visible | **The number** — 34.5 s → 11 s |
| 0:52–1:28 | Settings top, then overlays | **The twist** — KleidiAI does nothing |
| 1:28–2:05 | Settings thread cards | Where the speed came from |
| 2:05–2:42 | Study → Practise | The loop: answer → cards → drill |
| 2:42–3:00 | Settings privacy card | Close |

---

## 0:00–0:14 — Open on the phone, not on slides

**Do:** hold the phone up, airplane mode visible. Open Cram cold. Tap
**Try a sample deck**, then the **Ask** tab, then the suggested question
**"What are the four Coffman conditions?"**

**Say:**

> "This is a Poco M2 Pro from 2020. Two fast cores, six slow ones, no NPU, no
> i8mm — and it's in airplane mode.
>
> It's exam week, these are my lecture slides, and I have a question."

---

## 0:14–0:36 — The answer, and let the wait show

**Do:** nothing. Do not cut. The evidence card appears almost immediately; the
answer streams in after about nine seconds.

**Say** — as the evidence card appears (about 1 second in):

> "The matching slide comes back in eight milliseconds. That's not the model —
> that's BM25 ranking every passage in the deck. The model is only there to
> phrase it."

**Say** — while the answer is writing:

> "Nine seconds to the first word, on a six-year-old phone, with no signal."

**Say** — once all four conditions are on screen:

> "All four, complete, and it tells me they came from slide four. I can check its
> work — it can't make something up without showing me where it didn't come from."

> ⚠️ **Do not cut this wait.** An honest nine seconds is more credible than a jump
> cut, and the on-screen `first word` timer makes any edit obvious anyway.

---

## 0:36–0:52 — The number

**Do:** point at the `first word 9.0s` chip, still on screen.

**Say:**

> "Nine seconds. The first time this worked end to end it was **thirty-four and a
> half**. Same phone, same model, same weights — three times faster on hardware I
> didn't change.
>
> Here's where that came from. It is not where Arm tells you to look."

That last line is the hinge of the video. Say it, pause, then cut to Settings.

---

## 0:52–1:28 — The twist: KleidiAI is inert here

**Do:** tap **Settings**. Hold on the top card — `PREFILL 18 t/s`, `CORES 2 + 6`,
`I8MM no`. Then scroll to the bottom, to **ABOUT THIS PHONE**.

**Say** — on the top card:

> "The app measured this phone on first run. Eighteen tokens a second, two fast
> cores and six slow ones, and no i8mm."

**Say** — on the ABOUT THIS PHONE card:

> "Arm markets KleidiAI as CPU acceleration for on-device AI. On this chip it does
> nothing at all — and the app says so, about your own phone."

**Do:** cut to a full-screen overlay of the log line:

```
kleidiai: no compatible q4 kernels found for CPU features mask 1
kleidiai: SME disabled
```

**Say:**

> "Its int4 kernels need i8mm or SME. Armv8.2-A has neither, so GGML silently
> falls back. And we didn't just read the log — we built the same binary twice,
> with the library on and off."

**Do:** cut to a full-screen overlay of the A/B table.

| Q4_0 weights | prefill tok/s | decode tok/s |
|---|---:|---:|
| KleidiAI **ON** | 18.75 | 8.82 |
| KleidiAI **OFF** | 17.48 | 9.17 |

**Say:**

> "Seven percent apart, and decode is *faster* with it switched off. That's noise,
> not acceleration. And that cliff runs straight through the mid-range install
> base."

---

## 1:28–2:05 — Where the speed actually came from

**Do:** back to Settings. Scroll so both thread cards are visible:
**Threads for writing the answer — 6 of 8 cores** and
**Threads for reading the slides — 8 of 8 cores**.

**Say:**

> "So we measured what does work. Using all eight cores makes writing fifty-eight
> percent *slower* than six — every layer ends in a barrier, so the two fast cores
> sit waiting on the six slow ones. Reading the slides scales to eight; writing
> the answer peaks at six. So the app runs both, and it takes those numbers from
> the phone's own core split."

**Do:** scroll up to **HOW MUCH OF THE SLIDE IT READS** — Brief ~14s / Balanced
~19s / Thorough ~27s.

**Say:**

> "And prefill here is only about twice decode — not the fifty times you get on a
> GPU. That inverts the usual trade-off: context is expensive and retrieval is
> nearly free. So the app ranks the whole deck in milliseconds and sends almost
> none of it. That is what took first word from thirty-four seconds to nine.
>
> None of it is hard-coded. It times a real prefill on *your* phone at startup and
> sizes itself to it — and it shows you what each choice costs in seconds."

---

## 2:05–2:42 — The loop, in one motion

**Do:** back to **Ask**, scroll to the answer, tap
**"Make flashcards from slide 4"** directly under it. Let the Study tab open and
begin generating — then **cut**, with this caption burned on screen:

> `90 seconds later — not sped up, cut for length`

Land on the finished cards.

**Say:**

> "The question I just asked becomes the thing I revise. Ninety seconds of the
> phone's time, and they're saved with the deck, not thrown away when I close it."

**Do:** reveal one card — show the **from slide 4** chip. Then tap **Practise**,
mark one wrong, finish, and land on **"Drill the N I missed"**.

**Say:**

> "Every card knows which slide it came from. Get one wrong and it drills only
> those. And it tells me what I still haven't opened — because the real question
> at one in the morning isn't 'what is a deadlock', it's 'what have I not looked
> at yet'."

---

## 2:42–3:00 — Close

**Do:** Settings, bottom, the **PRIVACY** card.

**Say:**

> "No internet permission. No storage permission. Not a promise in a privacy
> policy — the app is structurally incapable of sending your notes anywhere.
>
> Everything you just saw ran on a six-year-old phone, in airplane mode, on the
> CPU."

---

# Part 4 — After the shoot

## Do not say

- ❌ "KleidiAI is broken" — it is inert *on this hardware*, and correct on i8mm/SME
- ❌ "accelerated by i8mm / SME2 / the NPU" — this chip has none of them
- ❌ any latency figure not visible on screen at that moment
- ❌ "the first app to do this" — Jackqr placed third doing something adjacent, and
  a judge who knows that stops trusting the rest of the video

## Title, thumbnail, description

The judge sees these before a single frame. Number first, finding second.

- **Title:** `Cram — 3× faster on a 2020 phone. None of it came from Arm's KleidiAI.`
- **Thumbnail:** phone in hand, airplane mode visible, two lines of text —
  **"34.5s → 9s"** large, **"KleidiAI: 0%"** underneath, the cited answer behind it.
- **Description:** first two lines matter, the rest is below the fold.

```
Offline RAG over lecture slides on a Snapdragon 720G — Armv8.2-A, no i8mm, no SME, no NPU.
First word 34.5s → ~9s, and a same-binary A/B showing Arm's KleidiAI contributes nothing on this CPU.

Code, benchmarks and the reproduction guide: https://github.com/ashfaqstu/cram-offline
APK: https://github.com/ashfaqstu/cram-offline/releases/latest
Built with Llama.
```

Not "Cram: an offline study app". That title is Jackqr's, and it came third.

## Upload checklist

- [ ] **Public**, not unlisted — an unlisted link fails eligibility
- [ ] Under 3:00
- [ ] `evidence 8 ms` and `first word 9.0s` legible at 1080p
- [ ] Airplane mode visible during the ask
- [ ] URL pasted into Devpost
