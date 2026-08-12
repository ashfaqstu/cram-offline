# Demo / validation script

Use this to check the pipeline end to end. **Run it in English first** — Whisper is
strongest there, so an English pass proves the machinery works and isolates any
remaining problem to the language.

## How the turn-taking works

You are playing **the local person** (bus clerk). The app is the **agent**, acting
for an English-speaking traveller. So:

1. **You speak** (as the clerk) → the app transcribes it
2. **The app answers** with its next question and fills in any facts it learned
3. Repeat until every slot is filled

The clerk speaks first, exactly like walking up to a counter.

## Setup

- Language: tap **English**
- Objective: **Bus ticket** — slots are `departure times`, `air conditioning`, `ticket price`
- Hold the button for the **whole sentence**, release when finished
- Speak at a normal pace, ~30 cm from the phone

---

## Turn 1 — greeting

> **Say:** *"Hello, welcome to the ticket counter. How can I help you today?"*

**Expect**
- `👤` line shows roughly that sentence
- `🤖` asks something about bus departure times
- Slots: still all `○` (you haven't told it anything yet)

**This turn passes if the transcript is close to what you said.** That is the ASR check.

---

## Turn 2 — give the departure times

> **Say:** *"We have buses to Cox's Bazar at eight in the morning, twelve noon, and ten at night."*

**Expect**
- `departure times` ticks to **✓** with something like `8 AM, 12 PM, 10 PM`
- `🤖` now asks about air conditioning or price — **not** about departure times again

**This is the real test.** It proves three things at once: the model extracted a fact,
the state machine stored it, and it moved on to what is still missing rather than
repeating itself.

---

## Turn 3 — answer both remaining questions

> **Say:** *"Only the ten o'clock bus has air conditioning, and a ticket costs fifteen hundred taka."*

**Expect**
- `air conditioning` ✓ (`only 10 PM` or similar)
- `ticket price` ✓ (`1500 taka`)
- All three slots filled → **English summary card** appears
- `🤖` says something closing

---

## What "working" looks like

| Check | Pass |
|---|---|
| Transcript resembles what you said | ASR is fine |
| Question is in the target language | prompt + model fine |
| A slot ticks from `○` to `✓` | extraction + FSM fine |
| It asks about something *different* next turn | the agent is genuinely tracking state |
| It never re-asks a filled slot | merge logic fine |
| Summary appears when all slots fill | full loop fine |
| `FIRST AUDIO` shows a number | the latency metric is being captured |

## If something fails

Every turn writes `turn_NNN.wav` + `turn_NNN.json` to the app's storage and is pulled
automatically. The WAV is the exact audio Whisper received, so a failure can be replayed
against a different model or thread count without you reproducing anything.

## Once English passes

Repeat **turn 2 only**, once with **TURBO on** and once with **TURBO off**, saying the
same sentence both times. That pair is the headline before/after latency number, and it
is the one measurement that cannot be produced without a person speaking.

Then switch to **हिन्दी** and run the same three turns to see whether the demo can be in
Hindi. Equivalent lines:

1. *"नमस्ते, टिकट काउंटर में आपका स्वागत है। मैं आपकी क्या मदद कर सकता हूँ?"*
2. *"हमारे पास सुबह आठ बजे, दोपहर बारह बजे और रात दस बजे बसें हैं।"*
3. *"सिर्फ़ दस बजे वाली बस में एसी है, और टिकट पंद्रह सौ रुपये का है।"*
