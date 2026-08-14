# Making Cram one product, not five tabs

Written with hours left before the deadline. The recommendation that matters most:
**do not add a feature now.** Everything below is tightening, renaming, or deleting.
A new feature added tonight will be the thing that breaks on camera tomorrow.

---

## The one sentence

> **It is the night before the exam. You have the slides and no time.**

Every screen should be answerable to that sentence. That is the test for whether
something belongs.

## The core loop

```
        a question you actually have
                  │
                  ▼
   ASK ──────► cited answer ──────► "Make flashcards from slide N"
    ▲                                          │
    │                                          ▼
    └────── "Ask about this" ◄──────────── STUDY / PRACTISE
```

The loop already closes in both directions. That is the product. Ask is the front
door, Study is the payoff, and the cross-links are what make them one thing instead
of two tabs that happen to share a PDF.

## What each tab is for, and why it stays

| Tab | Job in the one sentence | Verdict |
|---|---|---|
| **Ask** | "I don't understand this, and the lecture was three weeks ago" | **The core.** Everything else is in service of it |
| **Study** | "I understand it now, make me remember it by morning" | **The payoff.** Without it Cram is a search box |
| **Source** | "Is it actually telling me the truth?" | **The proof.** This is what separates it from a chatbot |
| **Slides** | Getting in | Necessary, keep minimal |
| **Settings** | The optimization story, on the judge's own phone | **Judge-facing.** Not for students, and that is fine |

Nothing here is out of place. The structure is already coherent — the risk was never
an extra feature, it was that the pieces read as unrelated. The cross-links fixed
that.

## What to tighten, in priority order

**1. Make the loop visible in the video, not just present in the code.** The single
most convincing 30 seconds is: ask a question → get a cited answer → tap "Make
flashcards from slide N" → reveal a card → tap "Ask about this". That is one
continuous gesture through the whole product without touching the tab bar. Shoot it
in one take.

**2. Lead every claim with the citation.** "Found in your slides — 1 ms" appearing
*before* the answer is the whole thesis: retrieval is the product, the model only
phrases it. It is already built. Make sure it is legible on camera.

**3. Practice is the weakest link.** It is the least polished screen and the least
essential to the sentence above. Do not delete it now — deleting is a change, and
changes break — but keep it out of the video if it does not look as good as the
rest. A feature the judge never sees cannot hurt you; a feature that looks unfinished
can.

**4. Resist the notebook.** Multi-PDF notebooks, chat history, mind maps and
multi-format ingest all pull toward "offline NotebookLM", which is a category with an
obvious cloud incumbent and a prior Arm-challenge entry that did not place. Cram wins
by being smaller and faster on hardware nobody else targets, not by being a worse
NotebookLM.

## What the submission actually argues

Say this in the video, and say it in this order:

1. **This phone is from 2020 and has no NPU, no i8mm, no SME.** Establish the
   constraint before showing anything work.
2. **Arm's own KleidiAI does nothing here** — log line, then the zero-delta A/B.
   This is the part nobody else has.
3. **So we measured what does work** — the thread cliff, and prefill being only 2×
   decode, which is why retrieval sends few passages rather than many.
4. **And it sizes itself to your phone**, not to ours.
5. **It cannot leak your notes** — no internet permission, structurally.

The app is the evidence for the argument. It is not the argument.

## The one thing that would have lost it

Answers used to be sampled with a random seed, so the same question could be right
once and wrong the next time. On camera that is a coin flip, and a judge who sees a
wrong answer will not re-ask. Greedy decoding, stemming, and a tighter runner-up
threshold turned "what algorithm avoids deadlock" from *"the ostrich algorithm"* and
*"Detection and recovery"* into **"The Banker's algorithm."** three times in a row,
character for character.

**Verify determinism again on the demo phone, on the day, after any rebuild.** It is
the cheapest possible insurance and it takes ninety seconds.

## Verified demo questions

Measured on the Poco M2 Pro against the bundled deck, after the determinism fix.

| Question | Answer | Slide | First word |
|---|---|---|---|
| What are the four Coffman conditions? | all four, in order | 4 | ~11 s |
| what algorithm avoids deadlock | The Banker's algorithm. | 6 | 16.9 s |
| when is assignment 3 due | The assignment 3 is due on 27 March. | 2 | 9.2 s |

Re-run these three before recording. If any one of them changes wording between two
consecutive asks, something has regressed to sampled decoding — check
`llama_sampler_init_greedy` is still in `native/otjni.cpp`.

**On building a deck that "gets everything right":** the bundled deck is a normal
lecture — course admin, a definition, a numbered list, four named strategies, an
algorithm with a complexity bound. It was not written to flatter the app; the three
questions above failed on it repeatedly, which is how the bugs were found. Keep it.
A deck engineered to be answerable would be both obvious to a judge and useless as a
test.
