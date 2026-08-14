# Devpost — what to change, and nothing else

`docs/DEVPOST.md` is still the source of truth for the full text. This file lists
**only what changed after it was written**, so you can edit the live submission
without re-reading the whole thing.

Nine edits. Six are one line each.

---

## 1. Tagline — lead with the number, not the negation

**Devpost field:** *Elevator pitch* (~200 chars)

**Replace with:**

```
Ask your lecture slides a question and get an answer with the slide number — offline, on a 2020 budget phone. 3x faster to first word, and none of it came from Arm's KleidiAI. We measured.
```

**Why:** this is the *Optimization* Challenge. The old tagline led with KleidiAI
doing nothing, which reads as "you measured, you didn't optimize". The 3× is the
achievement; KleidiAI is the twist that proves we measured rather than guessed.
Same facts, positive number first.

---

## 2. Add the optimization ledger — the highest-value new block

**Devpost field:** top of *How we built it*, before anything else

This did not exist in the previous submission. In a contest judged on
optimization, a single table of change → measured effect → where to verify is
the most legible thing a judge can score. **Paste it as the opening block:**

> **Time to first word: 34.5 s → ~11 s. Three times faster on hardware we did not
> change** — same phone, same model, same weights.
>
> | Change | Measured effect | Where you can check it |
> |---|---|---|
> | **Retrieve many, send few** — BM25 ranks the whole deck, only 1–2 passages are sent | the bulk of **34.5 s → ~11 s** | `first word` timer, on screen |
> | **Split prefill/decode thread counts** (8 and 6) | avoids a **58%** decode collapse | `sweep_POCO_M2_Pro.csv` |
> | **Q4_0 + GGML aarch64 repack** instead of Q4_K_M | **+15%** prefill (18.1 vs 15.7 tok/s) | `sweep_POCO_M2_Pro.csv` |
> | **KV prefix cache** for the system prompt | 14.8 s → 12.6 s (**~15%**) | `first word` timer, on screen |
> | **Greedy budget allocation** | stopped truncating the winning passage | answer correctness |
> | **Startup device calibration** | prompt budget sized to *your* phone, not ours | Settings screen |
> | **Greedy decoding** | identical answer to an identical question | ask it three times |
> | ~~KleidiAI~~ | **0%** — it does not engage on this CPU | same-binary A/B |
>
> The first-word figures come from different sessions on the same phone, so they
> do not add up into a clean waterfall — thermal state moves them a second or two
> either way. The end-to-end 34.5 s → ~11 s is the number the app prints on its
> own screen.

---

## 3. Setup Instructions — the APK now exists, lead with it

**Devpost field:** *Try it out* links / setup section

The previous draft opened with `git clone --recursive` + Gradle. A judge should
not need an NDK to see it run. **Replace the setup block with:**

> **Install (no build required):** download `cram.apk` from the
> [Releases page](https://github.com/ashfaqstu/cram-offline/releases/latest) and
> open it on any arm64 Android phone. On first launch the app shows two buttons —
> **Download the model (opens your browser)**, then **Choose the model file**.
> A sample lecture deck ships inside the app, so there is something to ask a
> question about immediately.
>
> Build from source instead: `git clone --recursive` … `./gradlew :app:assembleRelease`
> (JDK 21 + Android NDK; `scripts/setup.sh` provisions the whole toolchain).

**Why:** the release did not exist when the old text was written — the install
link 404'd. It is live now.

---

## 4. "What it does" — one sentence to add about the download button

Find the line about the app declaring no `INTERNET` permission and **append:**

> The setup screen offers a download button that hands the URL to your *browser* —
> the fetching happens there, so the app still declares no permission of any kind.

**Why:** without this, a judge who sees a "Download the model" button in the video
and then reads "no internet permission" will think one of the two is a lie.

---

## 5. Challenges — add this one, it is the strongest new material

**Devpost field:** *Challenges we ran into*

Add as a new paragraph. This is an optimization-tradeoff story with a measured
before and after, which is exactly what this contest rewards:

> **Optimizing for latency broke correctness at both ends of the pipeline.**
> We already knew about the input end: tuning the passage budget purely for speed
> drove it to ~400 characters, which cut a four-item list in half and made the
> model invent two of the four Coffman conditions. The output end had the same bug
> and we did not find it until the day of submission — the answer cap was 80
> tokens, so *"What are the four Coffman conditions?"* stopped mid-word at
> `4. **Circular Wait**: there exists a set of`. The cap is a ceiling, not a
> target — generation still stops at end-of-sequence, so short answers cost the
> same and **time to first word does not move at all**.
>
> Raising it exposed a second bug underneath. Given room to keep talking, the model
> filled it: the four conditions came back correct and were then followed by
> *"named after John Coffman, who first described them in 1972"* — a name and a
> date found nowhere in the deck, wrong twice over (it is Edward G. Coffman Jr.,
> 1971), and carrying a slide citation. A fabrication that cites a slide is worse
> than no answer, because the citation is the part the reader trusts.
>
> Tightening the cap instead was the obvious fix and the wrong one: the conditions
> run to roughly 125 tokens, so any ceiling low enough to exclude the invented tail
> also risks amputating condition four — trading a false addition for a false
> omission. Instead the closing sentence is now checked for names and years the
> retrieved passage does not contain, and dropped if it invents one. It is the
> check the flashcard path already ran before showing a card, moved onto answers.
>
> We tried asking the model not to invent, first. It did not remove the
> fabrication, **and** it rewrote an unrelated verified answer from *"The Banker's
> algorithm."* into *"This is the correct answer, as the Banker's algorithm is
> indeed…"* — the preamble the same prompt already forbids two lines earlier.
> Greedy decoding on a 1B model is steered by what you hand it, not by what you
> tell it about what you handed it.

---

## 6. Accomplishments — one line to add

Append to the existing list:

> **Two bugs found and fixed on the last day, by testing rather than assuming.**
> The launch path every new install takes crashed before the first frame, because
> an initializer wrote a Compose state property declared below it — invisible to us
> because our own phones always had the model already. It is the path every judge
> takes, and we only saw it by wiping the device and installing the APK as a
> stranger would.

**Why:** judges reward teams that test the reviewer's path. Say it plainly.

---

## 7. Correct the demo latency figures

Anywhere the old draft quotes per-question timings, **use these** — re-measured
on the final build, on the Poco M2 Pro:

| Question | Answer | First word |
|---|---|---|
| What are the four Coffman conditions? | all four, complete | **8.9 s** |
| what algorithm avoids deadlock | Banker's algorithm + how it works | **11.9 s** |
| when is assignment 3 due | 27 March, 11:59 pm | **7.3 s** |

**Note the second answer changed.** It used to be the bare *"The Banker's
algorithm."* Raising the cap means it now names the algorithm **and** explains
it, every clause traceable to slide 6. If the old text quotes the one-liner,
update it — it is better now, not worse.

The headline **~11 s** and **34.5 s → ~11 s** are unchanged and still correct.

---

## 8. What's next — drop one item

The old text ends with *"a KV cache A/B at `q8_0`"* and *"caching the passage
prefix"*. Keep both. **Add:**

> …and reproductions on i8mm hardware, where we expect the KleidiAI result to
> reverse. Disagreeing CSVs from other devices are the useful outcome, not a
> problem — the whole claim is that one hard-coded configuration cannot be right
> for every Arm phone.

---

## 9. Links block — fill these in

```
Repo:   https://github.com/ashfaqstu/cram-offline
APK:    https://github.com/ashfaqstu/cram-offline/releases/latest
Video:  <paste the public YouTube URL>
```

---

## Leave these alone

They are correct as written and re-editing them under time pressure is how
mistakes get in:

- The KleidiAI section — log line, A/B table, and the "scope, stated honestly"
  caveat about i8mm/SME hardware
- The prefill ≈ 2× decode explanation and the retrieve-many-send-few design
- The thread-cliff table (58% collapse at 8 threads)
- Q4_0 vs Q4_K_M and the `use_extra_bufts` correction
- Inspiration, What we learned, Built with
- The determinism / sampling story in Challenges

## Before you hit submit

- [ ] **Mobile AI** track selected
- [ ] Repo public, About shows **Apache-2.0** — done
- [ ] Release live with `cram.apk` — done, `sha256 ccea0709…a529`
- [ ] Video **public** on YouTube (unlisted fails eligibility), ≤ 3:00
- [ ] Video URL pasted into Devpost
- [ ] No claim of i8mm, SME2, NPU, or Performix anywhere
- [ ] "Built with Llama" present
