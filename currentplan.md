# Current plan — what we are doing right now

**One sentence: get ONE clean three-turn conversation that can be filmed.**

Everything else is already done or can be written later. The app builds, installs,
listens, thinks and speaks. What it does not yet have is a single recorded run good
enough to put in a video. That is the whole target.

**Deadline: 2026-08-15, 05:00 Dhaka.**

---

## 1. The target, precisely

A conversation where all three of these happen on camera:

1. You speak → the transcript on screen matches what you said
2. A slot flips from `○` to `✓` with a value you actually spoke
3. The next question is about **something different** — proof it is tracking state

If those three happen in one unbroken take, we can ship. Everything after that is
documentation and editing.

---

## 2. How to use the app

Install and launch:

```bash
cd D:/omnitalk
bash scripts/deploy.sh            # build + install + launch
bash scripts/deploy.sh --models   # add this the first time on a new phone
```

Then on the phone:

| Element | What it does |
|---|---|
| **TURBO / NAIVE** switch, top right | TURBO = all optimizations on. NAIVE = the unoptimized baseline. Flipping it reloads the model (~8 s). |
| **SPEAK** row | Target language. **English** for testing, हिन्दी for the demo. |
| **OBJECTIVE** card | The goal and its slots. `○` = still missing, `✓` = filled. |
| **Transcript** | `👤` = what it heard you say · `🤖` = what the agent asked, with an English gloss underneath |
| **FIRST AUDIO** | Seconds from you releasing the button to the first spoken word. The headline metric. |
| **HOLD TO SPEAK** | Press and **hold** for the whole sentence, release when done. |

**You are playing the local person** (a bus clerk). The app is the agent, acting for
an English-speaking traveller. So you speak first, like walking up to a counter.

Rules that matter:
- **Hold the button the entire time you are talking.** Releasing early truncates the audio.
- Speak ~30 cm from the phone, normal pace.
- Wait for the reply before pressing again — a turn takes about 10–15 seconds.

---

## 3. The ideal test case

Set language to **English**, objective **Bus ticket**.

### Turn 1 — greeting
> **Say:** *"Hello, welcome to the ticket counter. How can I help you today?"*

**Expected**
- `👤` shows roughly that sentence
- `🤖` asks about the bus — departure time, or price
- Slots: **all still `○`** ← this is a real test. You stated no facts, so nothing
  should fill. If a slot fills here, the model invented it and the guard failed.

### Turn 2 — give one fact
> **Say:** *"We have buses to Cox's Bazar at eight in the morning, twelve noon, and ten at night."*

**Expected**
- `departure times` → **✓** with something like `8 am` or `8:00, 12:00, 22:00`
- `🤖` asks about **air conditioning or price** — *not* departure again

This is the turn that proves the agent works. Three things at once: it extracted a
fact, the state machine stored it, and it moved on to what is still missing.

### Turn 3 — give the rest
> **Say:** *"Only the ten o'clock bus has air conditioning, and a ticket costs fifteen hundred taka."*

**Expected**
- `air conditioning` → ✓ · `ticket price` → ✓ (`1500`)
- All three filled → the **English summary card** appears

---

## 4. How to tell it is working

| Signal | Means |
|---|---|
| Transcript resembles your words | ASR is fine |
| Nothing fills on the greeting turn | the anti-hallucination guard is working |
| A slot fills with a value you actually said | extraction + state machine working |
| The next question is about a different slot | genuinely tracking state, not looping |
| It never re-asks something already `✓` | merge logic fine |
| Summary appears when all three fill | full loop working |
| `FIRST AUDIO` shows a number | the latency metric is being captured |

**If all seven happen, the app is working.** Record that take.

---

## 5. Test it without speaking

Regression testing after any change — no voice needed:

```bash
bash scripts/deploy.sh --test
```

Runs a three-turn conversation from pre-recorded audio and writes results to
`bench/results/turnlogs/`. Open `turn_002.json` and look for:

```json
"transcript": "Only the 10 o'clock bus has air conditioning and a ticket costs 1500 Taka.",
"slots": { "departure": "10 am", "ac": "yes", "price": "$1500.00" }
```

To watch live while you use the app by hand:

```bash
bash scripts/watch.sh
```

Every turn is pulled off the phone within a few seconds and printed with a verdict —
including `mic silent`, `ASR produced nothing`, `output is not JSON`.

---

## 6. Known-broken — do not chase these

| Thing | Status |
|---|---|
| **Bengali** | Does not work. Measured: whisper-tiny → `"Keep it to soul."`, base → `"ki kottisu"`. This is the models, not our code. Use English or Hindi. |
| **Bengali TTS voice** | Not installed on the device; the header shows `NO VOICE`. Text still appears on screen. |
| **The agent sometimes echoes you** as its own question instead of asking something new. Prompt was changed to fix this; needs re-verification. |
| **Latency ~10–11 s** per turn | Honest and expected. Whole-utterance ASR costs ~4.5 s of it. |
| **Turn 1 rarely fills slots** | Correct behaviour, not a bug. |

---

## 7. Order of work

1. **Push repo public + save a Devpost draft.** Ten minutes. The only irreversible
   deadline — do it before anything else.
2. **Get one clean English run** using §3, with `watch.sh` open.
3. **Try Hindi.** If the transcript is good, demo in Hindi. If not, demo in English —
   an English-only demo is completely fine, the agent behaviour is the point.
4. **Record TURBO vs NAIVE** — same sentence, flip the switch between. That pair is
   the headline before/after number and it is still missing.
5. **Film it.** Airplane mode on camera, one unbroken take for the conversation.
6. Docs, then Devpost text.

---

## 8. If it is not working

Check in this order:

1. `bash scripts/watch.sh`, do one turn, read the verdict line
2. `audio_peak` near `0.000` → the microphone captured nothing; hold the button longer
3. Transcript is nonsense → wrong language selected, or too far from the phone
4. Output is not JSON → grammar failed to load; `adb logcat | grep grammar`
5. App crashes → `adb logcat -b crash -d | grep -i "abort message"` gives the reason

Everything we have already hit, with exact error text and fixes, is in
`docs/TROUBLESHOOTING.md`.
