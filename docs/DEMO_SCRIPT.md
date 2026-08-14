# Demo script — follow this exactly

Every screen, every number and every button label below was captured on the Poco
M2 Pro on the build you are about to record. Nothing here is predicted.

Video is ≤ 3:00, public on YouTube, recorded on the real phone, no speed-ups.

---

# Part 1 — Prep. Ten minutes, before you press record.

### Let the phone cool. This is the one that will bite you.

First word was **9.0 s** on a cool phone and **19.1 s** on a hot one, in the same
build, on the same question. Screen recording plus a few generations is enough to
double it, and the number is on screen.

- [ ] Plug in, close everything, leave it **5 minutes** before the take
- [ ] **Do Not Disturb ON**
- [ ] **Airplane mode ON** — visible in the status bar, it is the claim
- [ ] Record **1080p portrait**, status bar in frame

### Set the app up

- [ ] **Settings → if a red `modified` chip is showing, tap "Reset to measured".**
- [ ] **Regenerate every deck's cards.** Cards are saved to disk, so any deck you
      made cards on before tonight still holds the old broken ones — I saw
      "What do roots do?" still sitting on your phone. Open the deck, tap
      **Make cards**, let it finish.
- [ ] Force-stop the app so the video opens cold.

### Two cutaway images, already made for you

- `bench/results/overlay_kleidiai_log.png`
- `bench/results/overlay_kleidiai_ab.png`

Both are 1080×1920, the exact size of your recording. Drop them straight on the
timeline. Do not rebuild them.

### Rehearse once, then delete the take

Run the whole thing once end to end. It also warms the deck so the take is clean.

---

# Part 2 — The shot list

| Time | On screen | Beat |
|---|---|---|
| 0:00–0:12 | Phone in hand, airplane mode | The hardware |
| 0:12–0:34 | Ask → cited answer | Evidence at 6 ms, answer at ~9 s |
| 0:34–0:50 | Answer still up | **The number** — 34.5 s → 9 s |
| 0:50–1:26 | Settings, then the two overlays | **The twist** — KleidiAI does nothing |
| 1:26–2:00 | Settings thread cards | Where the speed came from |
| 2:00–2:42 | Study → Practise | The loop |
| 2:42–3:00 | Settings privacy card | Close |

---

## 0:00–0:12 — Open on the phone

**Do:** hold the phone, airplane mode visible. Open Cram cold → tap
**Try a sample deck** → tap the **Ask** tab → tap the suggested question
**"What are the four Coffman conditions?"**

**Say:**

> "This is a Poco M2 Pro from 2020. Two fast cores, six slow ones, no NPU, no
> i8mm — and it's in airplane mode. These are my lecture slides, and I have a
> question."

## 0:12–0:34 — The answer

**What appears, in this order:** a `FOUND IN YOUR SLIDES  6 ms` strip, then the
**slide 4** card with the original text, then `ANSWER`, then the answer streaming
in at about nine seconds.

**The answer is one sentence.** Verified, character for character:

> The four Coffman conditions are mutual exclusion, hold and wait, no preemption,
> and circular wait.

Under it: `evidence 6 ms` and `first word 9.0s`.

**Say** — as the evidence strip appears:

> "The matching slide comes back in six milliseconds. That's not the model —
> that's BM25 ranking every passage in the deck. The model is only there to
> phrase it."

**Say** — once the answer completes:

> "All four conditions, and it tells me they came from slide four. I can check its
> work — it can't make something up without showing me where it didn't come from."

> ⚠️ **Do not cut the wait.** The `first word` timer is on screen; any edit is
> visible. Nine honest seconds on a 2020 phone is the point.

## 0:34–0:50 — The number

**Do:** point at the `first word 9.0s` chip.

**Say:**

> "Nine seconds. The first time this worked end to end it was **thirty-four and a
> half**. Same phone, same model, same weights — three times faster on hardware I
> didn't change.
>
> Here's where that came from. It is not where Arm tells you to look."

Pause. Cut to Settings.

## 0:50–1:26 — The twist

**Do:** tap **Settings**. The top card reads
`MEASURED ON FIRST RUN` — `PREFILL 18 t/s` · `CORES 2 + 6` · `I8MM no`.
Hold on it, then scroll to the bottom card, **ABOUT THIS PHONE**, which reads:

> "2 fast cores + 6 efficient cores at 2323 MHz. Arm's KleidiAI is inert on this
> CPU: its int4 kernels require i8mm or SME, and this processor has neither. We
> measured no difference with it switched on or off, so the speed here comes from
> how the app is built, not from the library."

**Say:**

> "The app measured this phone on first run. Eighteen tokens a second, two fast
> cores, six slow ones — and no i8mm.
>
> Arm markets KleidiAI as CPU acceleration for on-device AI. On this chip it does
> nothing at all, and the app says so about your own phone."

**Do:** cut to **`overlay_kleidiai_log.png`**, full screen, ~7 seconds.

**Say:**

> "Its int4 kernels need i8mm or SME. This CPU has neither, so GGML falls back —
> silently, for a hundred and sixty-three tensors."

**Do:** cut to **`overlay_kleidiai_ab.png`**, full screen, ~7 seconds.

**Say:**

> "And we didn't just read the log. We built the same binary twice, with the
> library on and off. Seven percent apart, and decode is *faster* with it switched
> off. That's noise, not acceleration — and that cliff runs straight through the
> mid-range install base."

## 1:26–2:00 — Where the speed came from

**Do:** back to Settings. Scroll so both thread cards are in frame:
**Threads for writing the answer — `6 of 8 cores`** and
**Threads for reading the slides — `8 of 8 cores`**.

**Say:**

> "So we measured what does work. Using all eight cores makes writing fifty-eight
> percent *slower* than six — every layer ends in a barrier, so the two fast cores
> wait on the six slow ones. Reading the slides scales to eight; writing peaks at
> six. The app runs both, and takes those numbers from the phone's own core split."

**Do:** scroll up to **HOW MUCH OF THE SLIDE IT READS** — `Brief ~14s`,
`Balanced ~19s`, `Thorough ~27s`.

**Say:**

> "Prefill here is only about twice decode — not the fifty times a GPU gives you.
> Context is expensive and retrieval is nearly free, so the app ranks the whole
> deck in milliseconds and sends almost none of it. That's what took first word
> from thirty-four seconds to nine.
>
> And none of it is hard-coded. It times a real prefill on *your* phone at startup,
> and shows you what every choice costs in seconds."

## 2:00–2:42 — The loop

**Do:** **Ask** tab → scroll to the answer → tap **"Make flashcards from slide 4"**.

Study opens with scope already set: **Slides**, `From 4`, `To 4`, `of 11`, and the
button turns into **`Writing`** with a spinner.

**Say:**

> "The question I just asked becomes the thing I revise."

**Do:** **cut here**, with this caption burned on screen:

> `100 seconds later — not sped up, cut for length`

Land on the finished list: **`5 CARDS FROM SLIDE 4`**. Verified, in this order:

1. What are the four Coffman conditions?
2. What is the purpose of mutual exclusion?
3. What is the purpose of hold and wait?
4. What is the purpose of no preemption?
5. What is the purpose of circular wait?

**Say:**

> "A hundred seconds of the phone's time, five cards, all from slide four — and
> they're saved with the deck, not thrown away when I close it."

**Do:** tap **Practise**. You get `1 of 5`, a progress bar, and **Show answer**.
Tap **Show answer** — the back appears with a yellow **`from slide 4`** chip, and
two buttons: **Review again** and **Got it**.

Tap **Review again** on this first card, then **Show answer → Got it** on the
remaining four.

> Do not linger on card one's back — it gives one condition, not all four. Tap
> through it. Card two onward read cleanly.

You land back on the list showing **`4 known · 1 to drill`** and a blue
**"Drill the 1 I missed"**.

**Say:**

> "Every card knows which slide it came from. Get one wrong, and it drills only
> that one — because the real question at one in the morning isn't 'what is a
> deadlock', it's 'what have I still not got'."

## 2:42–3:00 — Close

**Do:** Settings, bottom, the **PRIVACY** card:

> "This app has no internet permission and no storage permission. It cannot send
> your documents anywhere, even by mistake."

**Say:**

> "No internet permission. No storage permission. Not a promise in a privacy
> policy — the app is structurally incapable of sending your notes anywhere.
>
> Everything you just saw ran on a six-year-old phone, in airplane mode, on the
> CPU."

---

# Part 3 — After the shoot

## Do not say

- ❌ "KleidiAI is broken" — it is inert *on this hardware*, correct on i8mm/SME
- ❌ "accelerated by i8mm / SME2 / the NPU" — this chip has none of them
- ❌ any latency figure not on screen at that moment
- ❌ "the first app to do this" — Jackqr placed third doing something adjacent

## Why this order wins

The first Arm AI Developer Challenge had 142 entries and six winners. **Jackqr —
an offline study app with flashcards — took third.** Being a study app is worth
about third place, so the idea is not the pitch. This is the ***Optimization***
Challenge, and a third-place entry last year sold *using* KleidiAI as a feature.
Leading with 34.5 s → 9 s and landing KleidiAI at 0:50 is the whole argument, and
a judge who stops at one minute has already seen it.

## Title, thumbnail, description

- **Title:** `Cram — 3× faster on a 2020 phone. None of it came from Arm's KleidiAI.`
- **Thumbnail:** phone in hand, airplane mode visible, **"34.5s → 9s"** large and
  **"KleidiAI: 0%"** under it.

```
Offline RAG over lecture slides on a Snapdragon 720G — Armv8.2-A, no i8mm, no SME, no NPU.
First word 34.5s → ~9s, and a same-binary A/B showing Arm's KleidiAI contributes nothing on this CPU.

Code, benchmarks and the reproduction guide: https://github.com/ashfaqstu/cram-offline
APK: https://github.com/ashfaqstu/cram-offline/releases/latest
Built with Llama.
```

## Upload checklist

- [ ] **Public**, not unlisted — unlisted fails eligibility
- [ ] Under 3:00
- [ ] `evidence 6 ms` and `first word` legible at 1080p
- [ ] Airplane mode visible during the ask
- [ ] URL pasted into Devpost
