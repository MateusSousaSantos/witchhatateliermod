# Spell-recognition tuning scripts

Offline tooling for measuring and tuning the `$P+` spell recognizer without
rebuilding the mod or redrawing sigils every time. All scripts are plain Python 3
(standard library only) and read the JSON logs the mod writes at runtime.

See `docs/plan_to_improve_recognizer.md` for the phased remediation plan these
tools support.

---

## TL;DR workflow

```bash
# 1. (in game) enable logging, label what you draw, then draw samples
#    config: recognitionLoggingEnabled = true
#    /spell label fire        ... draw a few fire sigils
#    /spell label garbage     ... draw scribbles / deliberate non-sigils
#    /spell label clear       ... stop tagging

# 2. persist this session's labeled draws into the permanent corpus
python scripts/build_corpus.py

# 3. find the best decision-stage params over the whole corpus (instant, no redraw)
python scripts/tune_corpus.py

# 4. put the recommended numbers in the config, relaunch, done.

# ad hoc: inspect ONE puzzling result, or the raw distributions
python scripts/explain_recognition.py last
python scripts/analyze_recognition_log.py
```

---

## Data files

| File | Written by | Lifetime | Purpose |
|---|---|---|---|
| `run/logs/spell_recognition.jsonl` | the mod (when `recognitionLoggingEnabled = true`) | **volatile** — overwrite/delete between sessions | one JSON object per recognized sigil, the raw feed |
| `dataset/spell_corpus.jsonl` | `build_corpus.py` | **permanent** — only grows | the deduplicated labeled training/eval set |

Each record is one **sigil** (a cast can contain several). Key fields:

- `intended` — ground-truth label from `/spell label` (`null` if you didn't label). `"garbage"` is the negative class.
- `result` — what the recognizer actually returned: `{spell, score, angle}`.
- `rawStrokes` — the un-preprocessed ink, grouped per stroke: `[[{x,y}, …], …]`.
- `processedCloud` — what the chamfer saw: `[{x,y,a,s}, …]` (a = turning angle, s = stroke id).
- `survivors` — **unfiltered** ranking (every template), each `{spell, variant, score, dist, worst, p90}`. `dist` = mean chamfer distance, `worst` = max matched-pair distance, `p90` = 90th-percentile pair.
- `decision` — what `match()` **actually did** (the decision trail): per-template prefilter verdict + grid penalty + final score, plus the winner and every gate. This is the field the offline tuner replays.
- `thresholds` — snapshot of the live config values at draw time.

> `survivors` (unfiltered, all templates) and `decision.templates` (filtered, with
> grid + final score) are complementary: the former feeds distribution plots, the
> latter feeds faithful decision replay.

---

## Scripts

### `build_corpus.py` — accumulate the permanent dataset

```bash
python scripts/build_corpus.py [log.jsonl] [corpus.jsonl]
# defaults: run/logs/spell_recognition.jsonl  ->  dataset/spell_corpus.jsonl
```

Copies every **labeled** record out of the volatile log into the permanent corpus,
**deduplicated** by `(intended + exact rawStrokes)`. Because each draw is logged
with its raw strokes, **you only ever draw a given sample once** — run this after a
labeling session and the corpus grows; a later log wipe loses nothing. Prints how
many were added, the running total, how many carry a decision trail, and the
per-label counts.

### `tune_corpus.py` — Tier-1 offline tuner (the main tool)

```bash
python scripts/tune_corpus.py                                   # fidelity check + param sweep
python scripts/tune_corpus.py --free .2 --weight .5 --minScore .9   # evaluate ONE config
python scripts/tune_corpus.py --margin .02 --gridMinSim .75         # override other knobs
```

Replays the recognizer's **decision** over the whole corpus under any
decision-stage params, in milliseconds, **without re-running the chamfer or
redrawing**. It can do this because `decision.templates` already stores each
template's `dist`/`worst`/`gridSim` + prefilter verdict — the expensive geometry
is already computed. The replay is a faithful port of `PDollarPlusRecognizer.matchInternal`'s
decision stage:

```
effDist  = mean + WEIGHT * max(0, worst - FREE)
rawScore = max(0, 1 - effDist / sqrt(3))
score    = rawScore * gridMult        # only if rawScore > gridCheckThr
winner   = highest score; reject if < minScore; consensus rescue; ambiguity margin
```

Output:
- **FIDELITY** — replays each record under *its own* logged params and checks the
  prediction matches the logged `result`. High % (≈100) means the offline replay is
  trustworthy. (A couple of mismatches are normal: params drifting between draws, or
  the grid penalty re-applying.)
- **SWEEP** — best `free`/`weight`/`minScore` by balance, plus the best setting that
  keeps ≥90% valid recall.
- With `--`flags pinned, it instead evaluates that single config and prints
  accuracy, garbage-rejection, and the confusion matrix.

**Scope (important):** Tier-1 tunes only **decision-stage** params —
`recognitionMinScore`, `worstPairFreeAllowance`, `worstPairWeight`,
`recognitionAmbiguityMargin`, the consensus knobs, and `gridMinSimilarity`.
It **cannot** tune preprocessing/prefilter params (`resampleN`, dot injection,
aspect/density prefilters), because those change the chamfer geometry itself, which
is baked into the log. Tuning those would need a Tier-2 harness that re-runs the
real Java pipeline on `rawStrokes` (not built yet; the raw strokes are preserved in
the corpus for when it is).

### `analyze_recognition_log.py` — Phase-0 distributions & diagnostics

```bash
python scripts/analyze_recognition_log.py [path.jsonl]   # default: run/logs/spell_recognition.jsonl
```

The exploratory view over a labeled log. Prints:
- accuracy + the confusion matrix (and garbage accept/reject rate),
- **valid vs non-match distributions** for each distance channel (`dist`, `p90`,
  `worst`), each with a threshold sweep — this is how we discovered that the mean
  distance can't separate garbage but the worst-pair can,
- a **Phase-1 de-compression** recommendation (`DIST_NORM` / affine remap),
- a **Phase-2 soft-demote tuner** (approximate; the faithful one is `tune_corpus.py`).

Use this to *understand* the data; use `tune_corpus.py` to *pick* the numbers.

### `explain_recognition.py` — explain one result

```bash
python scripts/explain_recognition.py            # last record
python scripts/explain_recognition.py 5          # record index 5
python scripts/explain_recognition.py last fire  # last record whose result was 'fire'
```

Pretty-prints a single record's decision trail: the gates (winner, worst-pair,
runner-up/margin, consensus, rejection stage), every template that passed the
prefilters ranked by score (flagging the winner and grid-demoted ones), and the
templates removed by each prefilter. This is the tool for answering "why on earth
did it return *that*?" — e.g. a high raw-chamfer leader that a prefilter removed,
or a clean draw a gate rejected. Handles both current and legacy log formats.

---

## How the pieces fit

```
in-game draw (logging on, /spell label)
        │  writes
        ▼
run/logs/spell_recognition.jsonl  ──explain_recognition.py──► why did ONE result happen?
        │                         ──analyze_recognition_log.py► distributions / where's the overlap?
        │  build_corpus.py (dedup, persist)
        ▼
dataset/spell_corpus.jsonl  ──tune_corpus.py──► best decision params over ALL data
        │
        ▼
set worstPairFreeAllowance / worstPairWeight / recognitionMinScore in config → relaunch
```

The current shipped values (`recognitionMinScore = 0.90`,
`worstPairFreeAllowance = 0.20`, `worstPairWeight = 0.50`) were chosen this way on a
119-sample corpus: ~85% valid recall, ~89% garbage rejected.
