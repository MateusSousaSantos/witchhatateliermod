# Spell Recognition — Remediation Plan

A phased plan to fix the three symptoms you're seeing — **false accepts**, **false rejects (`unknown`)**, and **confusions between similar sigils** — in the existing `$P+` pipeline. Written against your actual classes and config knobs.

---

## Root cause

You reported all three symptoms at once, and **not** clustering. That pattern is the tell: the strokes reaching the recognizer are correct, but your **scores are compressed into a narrow high band (~0.80–0.95), so valid and invalid matches aren't separated enough for any threshold to split them.** Every threshold move just trades one symptom for another (raise `RECOGNITION_MIN_SCORE` → fewer garbage accepts but more `unknown`; lower it → the reverse). The seesaw *is* the proof that the fix is separation, not tuning.

Where the compression comes from, in your code:

- `PDollarPlusRecognizer.score`: `max(0, 1 − dist/√3)`. The `√3` normalizer assumes a matched pair could be √3 apart, but after `scaleToReferenceSquare` + `translateToOrigin` the real near-worst case is much smaller, so `√3` crushes the dynamic range — decent and mediocre matches both map into 0.8–0.95.
- The chamfer **averages** per-pair distances (`cloudDistance`), which **hides outliers** — garbage that overlaps a template in most places but sprawls in a few still averages to a passing score. → false accepts.
- Stage 5 returns `unknown` whenever two different spells land within `RECOGNITION_AMBIGUITY_MARGIN` (0.01). For look-alikes (the four vertical-shaft glyphs) that's almost always — so the gate **gives up exactly when it should look closer**. → confusions surface as `unknown`, and valid casts fizzle.

Three symptoms, one compressed-and-averaged score plus a give-up gate. The plan attacks each.

> **Decision recorded: not going to ML now.** A global model would average over the shared shaft just like `$P+` does (no better on look-alikes), and there's no labeled corpus to train it. ML stays a future, cluster-confined upgrade behind the Phase 3 resolver — *after* logging has run a while.

---

## Symptom → phase map

| Symptom | Primary fix | Supporting fixes |
|---|---|---|
| Accepts garbage as a spell | **Phase 2** (worst-pair distance gate) | Phase 1 (recalibration), Phase 4 (floor) |
| Rejects valid drawings as `unknown` | **Phase 1** (recalibration) | Phase 3 (resolve vs. fizzle), Phase 4 |
| Confuses similar sigils | **Phase 3** (per-cluster resolver) | Phase 1, Phase 4 (margin) |

---

## Phase 0 — Instrumentation (prerequisite, do first)

You cannot calibrate or threshold what you can't see, and this is also the future-ML data source.

- **Log every recognition** in `SaveGestureHandler.runSpellPipeline`: raw gesture points (per stroke), the full `survivors` list with raw chamfer distance **and** score per template, `bestPerSpell`, the final `RecognitionResult`, and the `blockOrigin`/context. You already build `survivors` and have `matchVerbose` — route them to a persistent log, not just the F-key viewer.
- **Build a labeled set:** a small in-game "draw spell X" collection mode, several people, dozens per sigil, including the four vertical-shaft glyphs and deliberate garbage.

**Output of this phase:** two score/distance distributions — **valid matches** vs **non-matches/garbage** — per sigil. Every later phase is tuned against these. If you do nothing else this week, do this.

---

## Phase 1 — De-compress the score scale

Goal: spread scores so valid matches sit high (~0.9) and non-matches sit low (~0.2), making the gates meaningful again.

- In `PDollarPlusRecognizer.score`, replace the theoretical `√3` with an **empirical normalizer** `DIST_NORM`, derived from the Phase-0 non-match distance distribution (roughly the typical non-match mean distance), **or** apply a monotonic affine/piecewise remap that pins median-valid → ~0.9 and median-non-match → ~0.2.
- **Safety property:** any monotonic remap leaves *which template wins* unchanged — it only rescales the numbers the gates read. So it cannot break cases that currently rank correctly; it can only make thresholds separable.
- **Consequence:** every score-space constant now lives on a new scale — `RECOGNITION_MIN_SCORE`, `RECOGNITION_AMBIGUITY_MARGIN`, `RECOGNITION_CONSENSUS_BONUS`, `GRID_CHECK_SCORE_THRESHOLD`, `GRID_MIN_SIMILARITY`. **Do not ship Phase 1 with the old thresholds** — they get re-derived in Phase 4.

**Validate:** re-plot the two distributions on the new scale; the valid/garbage bands should visibly pull apart.

---

## Phase 2 — Worst-pair distance gate (kill false accepts)

The mean hides the outliers that let garbage through. Real sigils match closely *everywhere*; garbage almost always strands a few points far from any template point.

- In `cloudDistance`, alongside the accumulated sum, also track the **max** (or 90th-percentile) matched-pair three-channel distance.
- In `match`, after the chamfer, add a gate (new `SigilFilters` check or inline post-score): if `worstPairDist > MAX_PAIR_DIST` → reject/demote, even when the mean score passes.
- Set `MAX_PAIR_DIST` from the Phase-0 distributions: above what valid matches ever produce, below where garbage lives.

**Validate:** false-accept rate on the logged garbage set drops; valid-match acceptance unchanged.

---

## Phase 3 — Per-cluster resolver at the Stage-5 gate (kill confusions)

This is the one piece of the earlier plan you never built. Today the ambiguity gate fizzles to `unknown` on look-alikes; instead, look closer at the region where they differ.

**Interface + registry** (drop-in seam, also the future ML slot):
```
interface ClusterResolver { members: spellName[]; resolve(processed, top2) -> RecognitionResult }
ResolverRegistry = { VERTICAL_SHAFT: FeatureTreeResolver /* later: MLResolver */ }
```

**Hook in Stage 5:** before returning `unknown` on the ambiguity-margin branch, if the top-2 *spell names* are both members of a registered cluster, call that cluster's `resolve(...)` and return its result instead of `unknown`.

**`FeatureTreeResolver` for the four vertical-shaft glyphs** — uses data you already compute (turning angle `α` is already on every `Point`, so curvature is nearly free). Checked **in this order** (up-arrow first, or it's misread as bar):
```
1. top horizontal spread (top ~25% of the cloud) WIDE      → up-arrow  (arrowhead)
2. bottom terminal: center height ≈ edges (flat)           → bar
3. bottom peak curvature (max α in bottom region) SHARP     → chevron
4. else (smooth bottom dip)                                 → cup
```
(Work in the `Processed` cloud — rotation is preserved there, so "top"/"bottom" are well-defined; mind the y-down convention.)

**Validate:** confusion matrix over the four glyphs collapses, especially the 1↔3 (bar/cup) and 3↔4 (cup/chevron) cells.

---

## Phase 4 — Re-tune the gates on the new distribution

Now the thresholds mean something. Tune on the Phase-0 labeled set, on the Phase-1 scale, with Phases 2–3 active:

- `RECOGNITION_MIN_SCORE` — set at the valid/garbage crossover.
- `RECOGNITION_AMBIGUITY_MARGIN` — from observed within-spell vs between-spell gaps (it will be larger than 0.01 once scores are de-compressed).
- `MAX_PAIR_DIST` (Phase 2), `RECOGNITION_CONSENSUS_BONUS`, grid thresholds — re-derive on the new scale.

**Validate:** sweep each knob; confirm you can now find a single operating point where all three symptom rates are acceptable simultaneously — which the compressed scale made impossible.

---

## Phase 5 — ML (future, cluster-confined, optional)

Only after Phase 0 has banked enough labeled data, and only if the `FeatureTreeResolver` plateaus below your bar:

- Train a **tiny model confined to the cluster's members** (4-class on the vertical-shaft glyphs), so it focuses on the discriminating ends instead of the shared shaft.
- Implement `MLResolver` behind the **same `ClusterResolver` interface**; register it for `VERTICAL_SHAFT`. No other code changes.
- Compare ML vs. feature-tree on the same confusion matrix; keep whichever wins.

---

## Sequencing & checklist

```
[ Phase 0 ] Logging + labeled set + redraw labels        ◄── do first, unblocks all
     │           → two distributions: valid vs non-match
     ▼
[ Phase 1 ] Empirical normalizer / monotonic remap        (false rejects ↓, separation ↑)
     ▼
[ Phase 2 ] Worst-pair distance gate in cloudDistance     (false accepts ↓)
     ▼
[ Phase 3 ] ClusterResolver at Stage-5 ambiguity gate     (confusions ↓)
     ▼
[ Phase 4 ] Re-derive MIN_SCORE / MARGIN / MAX_PAIR_DIST  (find one good operating point)
     ▼
[ Phase 5 ] (future) cluster-confined ML behind resolver  (only if tree plateaus)
```

---

## Definition of done

Measured on a held-out, **user-independent** labeled split (train templates from people A–E, test on F):

- Garbage / non-sigil input accepted as a spell: near-zero.
- Valid drawings returned as `unknown`: down to your tolerance.
- Four-glyph confusion matrix: per-symbol accuracy clears your **95%** bar.
- A **single** set of thresholds achieves all of the above at once (no seesaw).

---

## Risks & caveats

- **Distributions may still overlap after Phase 1.** If valid and garbage scores genuinely overlap, the chamfer can't separate them and you lean harder on Phase 2's percentile gate and the structural filters — and that overlap is the real signal that glyph redesign or cluster-confined ML is warranted. You won't know until Phase 0 shows you the two curves. (This is exactly why logging is first, not optional.)
- **Phase 1 invalidates old thresholds** — shipping it without Phase 4 will regress. Treat 1→4 as one release.
- **Resolver order matters** — up-arrow (top-spread) check must precede the bar check.
- **`MAX_PAIR_DIST` too tight** introduces new false rejects; tune it strictly from the valid-match distribution's upper tail.
- **Don't touch clustering** — you confirmed it's not the problem; changing `SigilClusterer` radii now only adds variables.

---

# Execution history (reconstructed 2026-07-01)

> The original execution log was lost (this file was never committed; recovered from git
> stash `3a47994`, which predates the entries below). Condensed reconstruction from
> session notes — treat dates as accurate, numbers as reported at the time.

## Phases 0–2 + Phase 4 first pass (2026-06-01)

- **Phase 0 built:** `RecognitionLog` JSONL (gated by `recognitionLoggingEnabled`),
  `/spell label <word>` ground-truth stamping, decision-trail logging (`matchTraced`),
  worst-pair/p90 instrumentation in the chamfer.
- **Phase 2 evolved:** hard `MAX_PAIR_DIST` gate → **soft demote**
  (`worstPairFreeAllowance`/`worstPairWeight` folded into effective distance) after a hard
  cut false-rejected a clean draw with a single outlier point.
- **Phase 1 shipped** as the two-pin ramp (`recognitionDistAtFullScore` /
  `recognitionDistAtZeroScore`) replacing `1 − d/√3`.
- **Offline tuning loop built** (`scripts/`): permanent corpus (`dataset/spell_corpus.jsonl`),
  `tune_corpus.py` decision-stage replay with fidelity check. First tune on a 119-sample
  corpus: 85% valid recall / 89% garbage rejected.

## Recall-first push R0–R5 (2026-06-11 → 06-12)

**Objective change:** valid-sigil recall ≥ 90%; letting more garbage through is accepted.

- **R0–R4 (06-11):** coverage promotion 75→123 template variants (curated `sopas` draws)
  + pruning 4 fire-stealing light variants + gate retune → recall **81% → 95.6%**
  (241/252), garbage rejection 17%. Final gates shipped in `Config.java`:
  `minScore 0.12 / distAtFull 0.03 / distAtZero 0.12 / margin 0.03 / worstPairWeight 0.0`
  (soft-demote disabled on the de-compressed scale — plain floor sufficed).
- **R5 (06-12):** third drawer (Paozin, 301 draws) → corpus 576. Coverage gaps: air 71%
  (stolen by water), pull 67% (stolen by dispersion). Promoted air 11→19, pull 10→21
  variants → replay recall **91% → 97%** (500/516), air 100%, pull 98%; donors held.
- **Learned mechanics** (keep applying): dense coverage saturates the ramp top — lower
  `distAtFullScore` when classes pin at 1.0; promotion can create new thieves — always
  re-audit and ablate (`tune_corpus.py --drop`) before pruning; small thieves are often
  net-positive holders; per-hand proportions are the dominant error source.
- **Left broken/unmeasured:** `/spell crossval` crashed on first run — the held-out
  generalization number (this plan's Definition of Done) was never measured. Headless
  replay harness (`run/world/datapacks/replay_trigger/`) later lost with the world folder.

## Roster change (production commit `48334b4`)

**`dispersion` is cut** — `dispersion.json` (20+ variants) deliberately deleted. All R-phase
numbers above were measured *with* dispersion present; its former confusion partners
(`pull`, `water`) need re-baselining. `bolt` has corpus labels but never shipped a template.

---

# Campaign: Misrecognition push (M-phases, started 2026-07-01)

**Pain point:** valid draws accepted as the *wrong* class (recall is fine; confusions are not).
Full plan agreed with user; success criteria: misrecognition ≤ 3% overall, no covered class
below 90% in-sample recall, leave-one-drawer-out recall measured (target ≥ 85% after the
4th-drawer round), garbage rejection not below the ~17% floor.

- **M0 — restore the measurement loop:** recover this doc (done); rebuild headless harness
  (repo copy in `scripts/replay_trigger/`); cut-class bucket in `CorpusReplay`/`CorpusCrossVal`
  (labels absent from `TemplateRegistry` tallied separately, reported as
  "should-reject: rejected/matched-as-something"); fix the crossval crash from a real stack trace.
- **M1 — baseline:** headless replay + first-ever crossval on the current (post-dispersion-cut)
  templates; record per-class recall, confusion matrix, win-stealers, per-drawer accuracy here.
- **M2 — confusion-pair loop:** per top cell: contact sheets → coverage-vs-thief diagnosis →
  `--drop` ablation before pruning / conservative promotion (suspect: `light`, 7 variants) →
  re-measure every change → gate retune last (`margin`/consensus/`gridMinSim`).
- **M3 — 4th drawer:** collect, merge, leave-one-drawer-out as the honest metric, promote gaps.
- **M4 — structural (only if M2/M3 plateau):** winner-rank sanity gate (winner must sit in the
  raw-chamfer top-K — targets the diagnosed rank-10 `fire` promotion by prefilters+consensus);
  table-driven pairwise resolver for persistent confusable pairs (this plan's Phase 3, scoped down).

## M-phase log

- **2026-07-01 — M0 started.** Doc recovered from stash `3a47994` and committed; execution
  history reconstructed above.
- **2026-07-01 — M0 done.** Headless harness rebuilt (canonical copy `scripts/replay_trigger/`).
  **Crossval "crash" solved: it was the ServerHangWatchdog** — crossval runs minutes of chamfer
  on the server thread in one tick and the 60s watchdog force-killed the server; the reported
  `CorpusCrossVal.java:129` was just the watchdog's thread-dump sample. Fix: `max-tick-time=0`
  in `run/server.properties` (dev harness only). Cut-class bucket added to `CorpusReplay`,
  `CorpusCrossVal` (also excluded from train promotion), `ModCommands`, `audit_replay.py` —
  covered set derived from the live registry/replay log, self-maintaining.
- **2026-07-01 — M1 baseline** (576-record corpus, 130 template variants, post-dispersion-cut,
  gates as shipped in Config.java):
  - **In-sample replay: 453/465 = 97.4%** real recall; misrecognition (wrong class accepted)
    **10/465 = 2.2%** — already under the 3% target. Per-class: all five elements 98–100%
    (zero element↔element confusions; `light` 100% despite only 7 variants), column 97%,
    pull 98%, levitation 95%, **crush 92%** (weakest). False rejects: 2 (ambiguityGate).
    Garbage 12/60 = 20% rejected (above the 17% floor).
  - **Every wrong-class error is inside the sign cluster** crush↔levitation↔pull↔column
    (plus one levitation→air). Steals are diffuse (no variant stole more than 2) →
    intrinsic look-alike geometry, not a thief template — the old plan's Phase-3
    cluster-resolver scenario, now with data.
  - **First-ever leave-one-drawer-out crossval: base 453/465 (97.4%) → +train-coverage
    457/465 (98.3%).** Caveat: "base" templates were partly authored from these same
    drawers' draws, so this is not a clean user-independent number — the honest read
    arrives with the 4th drawer (M3). Small Δ says extra coverage adds little for
    already-known drawers.
  - **Cut-class leakage is the big hole: dispersion draws 48/51 match a live class**
    (23→column, 21→pull, 2→air, 1→crush, 1→fire). Anything dispersion-shaped drawn in
    live play casts column/pull.
  - M2 focus, in order: (a) crush recall + crush↔levitation confusion, (b) decide whether
    the dispersion leak needs a mitigation (it is invisible to recall metrics), (c) only
    then gate retune.
- **2026-07-01 — M2 iteration 1 (verified live via headless replay):**
  - Ablations (`tune_corpus.py --drop` on the fresh replay log): `pull:variant_5` net-zero
    (keep), `levitation:variant_6` net-positive holder (keep), **`levitation:variant_11`
    net-negative thief** — steals 2–3 crush, holds 1 levitation. **Pruned** (17→16 variants).
  - **Margin retune 0.03 → 0.05** (Config.java default): offline sweep showed the sign-cluster
    confusions and a big slice of garbage live in the 0.03–0.05 gap band while real winners
    clear it. Knee sharp: 0.06 loses one draw, 0.08 → 96% recall.
  - **Result: recall 453→454/465 (97.6%), wrong-class accepts 10→6 (1.3%), garbage
    rejection 20%→40%**, crossval base 453→454. Per-class: crush 92→95%, levitation 95→93%
    (one draw traded to the prune + one to unknown — acceptable), everything else ≥97%.
    Dispersion leak 48→44/51 (still the biggest uncontrolled hole; mitigation deferred —
    needs either a shape-space guard (M4 resolver) or acceptance that a cut-class draw casts
    its nearest neighbor).
  - **Remaining in-sample errors: 6 wrong-class + 5 ambiguity-gate fizzles**, almost all
    Dev-drawer sign-cluster boundary cases. In-sample tuning is at diminishing returns —
    **next lever is M3 (4th drawer + honest generalization number), which needs new data.**
  - Tooling: `tune_corpus.py` now excludes cut-class labels (covered set derived from the
    log's survivors) so ablation/sweep numbers aren't dragged by a constant 51-record penalty.
- **2026-07-04 — M4 (structural): open-set rejection templates** — the first structural
  lever, targeting the two holes in-sample tuning cannot touch: the ~60% garbage acceptance
  and the dispersion leak (44/51 cut draws still cast a live spell). Turns the closed-set
  classifier (always "which template is nearest?") open-set by letting a template's *win mean
  reject*.
  - **Mechanism:** `Template.isRejection` + `"is_rejection": true` in a `spell_templates` JSON
    (parsed in `SpellTemplateLoader`). Negative templates run the same prefilters + chamfer as
    any template but never enter the survivor pool, per-spell table, or winner race; their best
    score is tracked apart (`bestRejectionScore`). New **rejection gate** in
    `PDollarPlusRecognizer.matchInternal` (after consensus, before the ambiguity gate): reject
    when `winnerEffectiveScore − bestRejectionScore < recognitionRejectionMargin`. New stage
    label `rejectionTemplate`; `bestRejection*` fields added to `MatchTrace` and the log's
    decision block; `rejectionMargin` added to the thresholds snapshot.
  - **`recognitionRejectionMargin` default 0.0** = fire only when a negative template *strictly*
    out-scores every real spell → **inert until negative templates are authored**, and a pure
    monotone add-on (never changes which real spell wins). Raise it to also reject
    look-alikes-of-garbage (trades cut/garbage rejection for a little recall).
  - **Seeded negative template:** `dispersion.json` recovered from `48334b4^` (12 variants),
    re-added with `is_rejection: true`. Because dispersion won its own draws at full roster,
    the gate should reclaim most of the 44/51 leak at margin 0.0. `covered`-set builders in
    `CorpusReplay`/`CorpusCrossVal` and `matchVerbose` now exclude negatives, so `dispersion`
    stays a should-reject class (not recall) and isn't promoted in crossval.
  - **Not yet measured live** — needs a headless replay run (`/spell replay-corpus`) to quantify
    the dispersion-leak drop and any valid-recall cost, then a `recognitionRejectionMargin`
    sweep against the garbage + cut buckets. Extend the negative set with labeled `garbage`
    draws (via `promote_to_templates.py` with an `is_rejection` flag) once the seed is validated.
