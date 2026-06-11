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

### `promote_to_templates.py` — corpus → new template variants (coverage fix)

```bash
python scripts/promote_to_templates.py fire              # add all clean fire draws
python scripts/promote_to_templates.py crush --count 6   # cap how many
python scripts/promote_to_templates.py fire --dry-run    # preview only
# web-drawer coverage: read the REPLAY log (corpus rows for web draws have no result),
# filter to one drawer, hand-pick cells after a render_draws.py review:
python scripts/promote_to_templates.py fire --corpus run/logs/corpus_replay.jsonl `
    --drawer sopas --select 5,7,9,11,13,14
```

The other lever besides decision-param tuning: when a sigil fails because of thin
**template coverage** (too few variants, or authored variants that don't match how
people actually draw it), promote real labeled draws from the corpus into new
`spell_templates/<label>.json` variants. Works because `SpellTemplateLoader` runs every
variant through the same preprocessing as a live candidate, so a corpus record's
`rawStrokes` drops straight in as a variant's `points`. Conservative by default (only
promotes draws the recognizer already classified correctly, so a borderline draw never
becomes a template); `--include-misrecognized` adds the failures too.

> **Validate with the Tier-2 harness (`/spell replay-corpus`), not `tune_corpus.py`.**
> `tune_corpus.py` replays the logged chamfer geometry, which does *not* contain new
> templates, so it can't measure their effect. After promoting, `/reload` (or relaunch)
> and run `/spell replay-corpus` (see below) to re-score the corpus against the current
> templates, then analyze `run/logs/corpus_replay.jsonl`.

### `/spell replay-corpus` — Tier-2 harness (in-game command, faithful re-score)

```
/spell replay-corpus                 # replays dataset/spell_corpus.jsonl
/spell replay-corpus <abs-path>      # replays an explicit corpus/log file
```

Runs each corpus record's preserved `rawStrokes` back through the **live** Java
preprocessing + chamfer against the **currently-loaded** templates, then writes the
results in the exact recognition-log schema to `run/logs/corpus_replay.jsonl`. This is
the faithful answer to "did my template edit help?" that `tune_corpus.py` cannot give
(it is blind to templates added after a draw was logged). Because the harness *is* the
live pipeline, it also reflects preprocessing/prefilter changes — the params Tier-1
can't touch.

Workflow: edit a `spell_templates/*.json` variant → `/reload` → `/spell replay-corpus`
→ feed `run/logs/corpus_replay.jsonl` to the analysis tools below (both already accept a
path argument):

```bash
python scripts/analyze_recognition_log.py run/logs/corpus_replay.jsonl   # distributions + confusion on CURRENT templates
python scripts/tune_corpus.py            run/logs/corpus_replay.jsonl   # re-tune gates on CURRENT geometry
```

Implemented by `CorpusReplay.java` (harness core) + `RecognitionLog.writeRecords` (the
shared, path-parameterized writer) + the `replay-corpus` branch in `ModCommands.java`.

### Headless harness runner (no client, no typing in a console)

```powershell
.\gradlew runServer --console=plain    # ~70s: builds, boots, replays, crossvals, stops itself
```

`run/world/datapacks/replay_trigger/` contains a `#minecraft:load` datapack function
that runs `spell replay-corpus` + `spell crossval` and then `stop` every time the dev
server boots (`run/server.properties` sets `function-permission-level=4` so the
function may issue `stop`). So one Gradle command produces fresh
`run/logs/corpus_replay.jsonl` + `corpus_crossval.jsonl` against the current code,
config defaults, and templates — the whole recall-first loop without launching a
client. Delete the datapack folder to get a normal interactive dev server back.

### `render_draws.py` — PNG contact sheets for visual curation

```bash
python scripts/render_draws.py fire --corpus run/logs/corpus_replay.jsonl --drawer sopas
python scripts/render_draws.py fire --templates     # the shipped variants instead
# writes run/render/<label>_{draws,templates}.png + a stdout legend per cell
```

Promotion requires eyeballing draws first (one malformed draw widens a sigil's accept
region permanently). This renders any label's draws — or its current template
variants — as a grid, strokes colored in drawing order, no deps (stdlib PNG writer).
The legend marks each cell OK/WRONG/UNK with the drawer and the recognizer's answer;
cell numbers are exactly the indices `promote_to_templates.py --select` takes, so the
workflow is: render → pick cells → `--select 5,7,9`.

### `audit_replay.py` — per-drawer accuracy + win-stealing templates

```bash
python scripts/audit_replay.py [replay.jsonl] [corpus.jsonl]
# defaults: run/logs/corpus_replay.jsonl, dataset/spell_corpus.jsonl
```

The first thing to read after a replay. Prints per-drawer real accuracy with each
drawer's confusion cells (whose drawing style the templates miss — the coverage
signal), the table of template variants that win draws labeled as something else
(ablation candidates for `tune_corpus.py --drop`), and the rejection stage behind
every `unknown`. Joins replay lines back to corpus lines by order to recover the
drawer id (CorpusReplay doesn't carry `player` through yet) and fails loudly on a
stale/misaligned replay log.

### `/spell crossval` — Tier-2 cross-validation (generalization, not in-sample)

```
/spell crossval                 # splits dataset/spell_corpus.jsonl
/spell crossval <abs-path>      # splits an explicit corpus file
```

`replay-corpus` scores the corpus against the templates it helped author — an *in-sample*
number. `crossval` measures **generalization**: it splits the corpus and never lets a test
draw's own group seed the templates it's matched against.

- **≥2 drawers** → **leave-one-drawer-out** (the user-independent split the Definition of
  Done wants): each drawer is held out, the others' draws are promoted to templates.
- **1 drawer** → stratified **5-fold** by label. *Not* user-independent (same hand draws
  train and test) — a smoke test of the machinery until a second drawer's data exists.

Each fold reports held-out accuracy two ways: **base** (authored templates only) vs
**base + train coverage** (authored + the fold's train draws promoted to in-memory
templates). The **Δ** is the marginal value of that coverage — "does collecting more
drawers actually help?" — answered without the circularity of testing on promoted draws.
Augmented predictions are written to `run/logs/corpus_crossval.jsonl` (same schema), so
`analyze_recognition_log.py run/logs/corpus_crossval.jsonl` breaks them down per class.

> Promotion is in-memory (mirrors `SpellTemplateLoader`), so nothing is written to
> `spell_templates/` and no `/reload` is needed. Implemented by `CorpusCrossVal.java` +
> the `crossval` branch in `ModCommands.java`.

### `tune_corpus.py` — Tier-1 offline tuner (the main tool)

```bash
python scripts/tune_corpus.py                                   # fidelity check + param sweep
python scripts/tune_corpus.py --free .2 --weight .5 --minScore .9   # evaluate ONE config
python scripts/tune_corpus.py --margin .02 --gridMinSim .75         # override other knobs
python scripts/tune_corpus.py --drop pull:variant_7 --minScore .3   # ablate one template variant
python scripts/tune_corpus.py --drop pull                           # ablate a whole spell
```

`--drop spell:variant` (or `--drop spell`, repeatable) removes a template from the
matcher and recomputes the decision. This is faithful — dropping a template never
changes any other template's logged geometry — so it answers "is this variant hurting?"
offline, without a re-draw or a server run. (Finding: pruning the `pull` "over-attractor"
variants did nothing; the residual errors are coverage gaps, not bad templates.)

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
aspect/density prefilters), nor measure template additions, because those change the
chamfer geometry itself, which is baked into the log. For those, use the **Tier-2
harness** (`/spell replay-corpus`, above): it re-runs the real Java pipeline on each
record's `rawStrokes` against the live templates and writes a fresh log that this same
tuner then reads (`tune_corpus.py run/logs/corpus_replay.jsonl`).

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
        │                   ──/spell replay-corpus (Tier-2)──► re-run live pipeline on
        │                          │                            CURRENT templates
        │                          ▼
        │                   run/logs/corpus_replay.jsonl ──analyze/tune_corpus.py──► faithful
        │                                                   accuracy after template/preproc edits
        ▼
set worstPairFreeAllowance / worstPairWeight / recognitionMinScore in config → relaunch
```

The current shipped values were chosen this way on a 119-sample corpus. After the
**Phase-1 score de-compression** (`recognitionDistAtFullScore = 0.054`,
`recognitionDistAtZeroScore = 0.085` — pins that map a typical valid draw to ~0.9 and a
typical non-match to ~0.2), the gates were re-tuned: `recognitionMinScore = 0.33`,
`worstPairWeight = 0.0` → ~83% valid recall, ~94% garbage rejected. Note `worstPairWeight`
is now **0**: on the de-compressed scale a plain `minScore` on the mean distance already
rejects garbage that the old compressed scale needed the worst-pair demote to catch, and
with the tight Phase-1 pins any `weight > 0` over-penalizes valid draws. The residual
false-rejects (look-alikes going to `unknown`) are the geometry overlap Phase 1 can't fix —
that's the Phase-3 per-cluster resolver's job.
