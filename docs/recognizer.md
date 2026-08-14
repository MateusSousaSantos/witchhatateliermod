# The Sigil Recognizer

How a player's pen strokes become a named sigil — every stage, every gate, and
**why** each decision is the way it is, with file/line references into the code.

> Package: `com.crsocial.witchhatatelier.spell.recognition`
> (`src/main/java/com/crsocial/witchhatatelier/spell/recognition/`)
> Status: **implemented**. This doc describes the code as it ships; the offline
> tuning workflow that produced the shipped numbers is in `scripts/README.md`.

---

## 1. What it is, in one paragraph

The recognizer answers exactly one question: **"what shape is this point cloud?"**
It is a faithful Java port of the reference **`$P+`** point-cloud recognizer
(Vatavu, *CHI '17* — `PointCloudRecognizerPlus`). A drawn sigil and every stored
template are reduced to a fixed-size cloud of `(x, y, turningAngle)` points, then
compared by a **symmetric chamfer distance**. The raw `$P+` classifier returns the
nearest class unconditionally; we wrap it in a layer of cheap structural
**pre-filters**, a spatial **post-filter**, a **worst-pair soft-demote**, and three
**decision gates** (minimum score, ambiguity margin, consensus) so that *garbage
becomes `unknown`* instead of being force-matched to the closest sigil.

The whole class doc lives at the top of `PDollarPlusRecognizer.java:11-27`.

Two non-obvious design commitments frame everything below:

- **It only classifies shape.** It does not know what a sigil *means* (that's
  `spell/meaning/`), how well it was drawn (that's quality scoring), or whether the
  ring was closed (that's `spell/trigger/`). 
- **It carries no Minecraft, network, or rendering dependency.** `Point` is
  deliberately decoupled from the client wire-format `GesturePoint`
  (`Point.java:6-11`), so the recognizer is a pure geometry library that could be
  unit-tested or run offline (which is exactly what the Python tuner does).

---

## 2. Where it sits in the cast pipeline

```
client: Draw → Recognize ($P+ preview) → Validate (closed Ring) ──SaveGesturePayload──► server
server: cluster strokes → preprocess → RECOGNIZE ($P+) → SpellGraphBuilder → CompositionEngine → …
```

The server-side orchestrator is `SaveGestureHandler.runSpellPipeline()`
(`network/SaveGestureHandler.java`). The relevant slice:

1. Strokes arrive, ring strokes are separated from content strokes
   (`SaveGestureHandler.java:226-239`).
2. `SigilClusterer.cluster(...)` groups content strokes into candidate sigils
   (`:246`).
3. For each cluster: `cluster.toPointCloud(...)` → `PointCloudPreprocessor.process(...)`
   → `recognizer.match(processed)` (`:257-271`).
4. The top-3 raw ranking is logged for debug, and — when logging is enabled — the
   full decision trail is persisted (`:279-304`).

The recognizer is **server-authoritative**. The client runs its
own preview recognition for UX, but the server's `match()` is the one that counts.

---

## 3. The data model

| Type | File | What it is |
|---|---|---|
| `Point(x, y, strokeID, turningAngle)` | `Point.java` | One sample. `turningAngle ∈ [0,1]` is the **third channel** of the distance metric; endpoints carry `0`. |
| `PointCloud(name, points)` | `PointCloud.java` | An ordered list of points; one drawn sigil or one template variant. |
| `SigilMetrics(...)` | `SigilMetrics.java` | Per-cloud cached geometry (bbox, aspect, ink density, dot count, loop count, 3×3 histogram). Computed **once**; lets every filter run O(1) at match time. |
| `Template(spellName, variantName, rawCloud, processedCloud, …, metrics)` | `Template.java` | One stored variant of a canonical spell. Keeps the **raw** cloud so re-preprocessing never needs re-authoring. |
| `RecognitionResult(spellName, confidenceScore, indicativeAngle)` | `RecognitionResult.java` | The output; `spellName == "unknown"` when every gate fails. |

**Why a third "turning angle" channel?** `(x, y)` alone can't tell a sharp corner
from a smooth curve passing through the same point. `$P+` adds the normalized
interior turning angle so corners match corners and arcs match arcs — this is the
"+" in `$P+`. See `Point.java:8-11` and the metric at
`PDollarPlusRecognizer.java:449-458`.

---

## 4. Preprocessing — `PointCloudPreprocessor`

Both candidate and template go through the **same** pipeline, so they live in the
same normalized frame before any comparison. The canonical order
(`process()`, `PointCloudPreprocessor.java:348-369`):

```
dot-inject raw → scale → translate-to-centroid → resample(N) → turning angles
                                                   └─ then compute SigilMetrics
```

### 4.1 Dot injection (input fixup) — `:351-354`, `SigilFilters.injectDotsIfNeeded`

A "tap" (Earth's dots, Cross-hair's marks) is a stroke with near-zero path length.
Fed to the resampler directly it is pathological: dividing total length by point
count gives a zero interval, and you get N copies of one coordinate — a singular
cloud with a meaningless turning angle (`SigilFilters.java:279-301`).

**Decision:** detect any stroke shorter than `dotInjectionRadius` and replace it
with an 8-point ring of that radius (`SigilFilters.java:303-352`). Now the dot is
real geometry the chamfer can bite into. The same radius doubles as the dot
*detector*, so detection and repair never disagree.

Crucially, `process()` counts dots on the **raw** cloud *before* injection
(`:351`), because after injection every dot is a full ring and the count would be
zero.

### 4.2 Scale to a reference square — `scaleToReferenceSquare`, `:165-182`

Divide by the larger bbox side so the shape fits a unit square. Makes matching
**scale-invariant**: a small fireball and a big fireball are the same sigil.

### 4.3 Translate to centroid — `translateToOrigin`, `:186-197`

Center the cloud at `(0,0)`. Makes matching **translation-invariant** — *where* on
the canvas you drew doesn't matter.

### 4.4 Resample to N points — `resample`, `:49-153`

`$P+` needs every cloud to have the same point count so chamfer terms are
comparable. We resample to `resampleN` (default **128**, `Config.RESAMPLE_N`).

Two deviations from the textbook, both deliberate:

- **Per-stroke proportional allocation** (`:83-104`): points are split across
  strokes by arc-length share, so a long stroke gets more samples than a short one.
- **A per-stroke floor** (`MIN_POINTS_PER_STROKE`, default 4, `:79-81`): without
  it, very short strokes (Cross-hair's marks, Earth's dot rings) would get 1–2
  points by proportion and vanish from the chamfer. The floor is capped at
  `n/strokeCount` so it can never starve the long strokes when there are many short
   ones. This is a correctness fix for multi-stroke sigils, paid for with a tiny
   bias toward short strokes.

### 4.5 Turning angles ($P+ third channel) — `computeTurningAngles`, `:266-303`

For each interior point within one stroke, `α = acos(cos θ)/π ∈ [0,1]` where `θ` is
the angle between the incoming and outgoing segments. Endpoints — of the cloud
**and** of each stroke — keep `0` (`:272-278`). Cross-stroke neighbors are skipped
so a pen-lift never fabricates a corner.

**Why after resampling?** The angle depends on neighbor spacing, so it must run on
the resampled cloud. It is invariant under rotation/translation/scale, so its exact
position relative to those steps is convenience, not correctness
(`PointCloudPreprocessor.java:22-25`).

### 4.6 Indicative angle & rotation — `indicativeAngle` `:211-223`, `rotateBy` `:236-247`

The principal axis is found by 2×2 covariance eigendecomposition over **all**
points (`θ = ½·atan2(2·Sxy, Sxx − Syy)`), not a centroid→first-point vector. A
covariance axis is stable under stroke-order changes and symmetric shapes, where
the naive vector collapses (`:201-210`).

A principal axis is a *line*, so it's only defined up to 180°. Rather than guess the
flip, the recognizer keeps both: the symmetric chamfer (§5) tries both match
directions, which absorbs the ambiguity. (Note: in the shipped `process()`,
`indicativeAngle` is stored as `0f` at `:368` — the candidate's angle is recomputed
and carried through `RecognitionResult` for downstream aim/orientation, while
rotation-invariance at match time is handled by the symmetric chamfer rather than a
pre-rotation. The `rotateBy`/`indicativeAngle` helpers remain available for the
180° flip retry.)

---

## 5. The core: symmetric chamfer distance

This is the reference `$P+ GreedyCloudMatch` / `CloudDistance`, ported verbatim
(`PDollarPlusRecognizer.java:323-458`).

### 5.1 One-direction distance — `cloudDistanceStats`, `:364-418`

Two phases:

- **Phase A:** for every point in `input`, find its nearest neighbor in `template`
  by the three-channel distance `√(Δx² + Δy² + Δα²)`, accumulate it, mark the
  template point matched (`:374-387`).
- **Phase B:** for every *unmatched* template point, add its nearest distance back
  into `input` (`:389-402`).

The mean over all accumulated terms is the reference `CloudDistance`. Phase B is
what stops a small dense scribble from "hiding" inside a big template: unmatched
template points still cost.

This direction also returns two extra statistics — the **worst (max)** matched-pair
distance and the **p90** matched-pair distance (`ChamferStats`, `:325-329`). These
are the signal the worst-pair demote (§7) uses. p90/worst are found in a single
pass + a `quickSelect` (`:408-447`) to avoid a full sort in the hot loop.

### 5.2 Symmetric — `chamferStats`, `:331-340`

Compute both directions (`A→B`, `B→A`) and keep the one with the **smaller mean**,
returning *that* direction's full stats. This mirrors the reference
`min(CloudDistance(A→B), CloudDistance(B→A))` and is what makes the metric robust to
the 180° principal-axis ambiguity and to asymmetric point counts.

### 5.3 Distance → score — `scoreFromDistance`, `:342-345`

`score = max(0, 1 − d/√3)`. The `√3 = REFERENCE_SIZE` (`:30-33`) is the maximum
possible per-pair distance with all three channels in `[0,1]`, so the score lands
in `[0,1]`: `1.0` = identical, `0.0` = maximally different.

---

## 6. The filter pipeline — `SigilFilters`

**The problem the chamfer creates:** it is scale-, density-, and aspect-invariant.
Wonderful for matching a sigil regardless of how big or sloppy it was drawn — but it
also means a simple "+" matches a multi-arm starburst at high confidence, because
the chamfer threw away exactly the cues that separate them. The filters
**re-introduce those discarded invariants** as cheap gates around the expensive
chamfer (`SigilFilters.java:11-37`).

Every filter is O(1) given the precomputed `SigilMetrics`, so the hot loop never
iterates points. The three-stage structure is documented at
`PDollarPlusRecognizer.java:59-73` and implemented in `matchInternal` `:171-226`.

### Stage 1 — structural pre-filters (skip the chamfer entirely)

Run *before* the O(N²) chamfer; any failure short-circuits (`:174-188`). Order
matters only for the trace label; all four are independent.

| Filter | Code | Rejects when | Why it's safe |
|---|---|---|---|
| **Aspect ratio** (direction-aware) | `aspectRatioPasses` `:61-71` | a *tall* cloud meets a *wide* template (or vice versa) | A vertical Column can't be a horizontal Crush. Direction-aware, so two opposite ARs can't cancel into a false pass — the bug an earlier `max(a/b, b/a)` had (`:52-56`). `square` is permissive on both sides; the chamfer decides borderline cases. |
| **Dot count** | `dotCountPasses` `:100-104` | `|cand − tmpl| dots > tolerance` (default 1) | Free discriminator: Earth=2, Cross-hair=4, everything else 0. Counted pre-injection (`:81-92`). |
| **Loop count** | `loopCountPasses` `:198-202` | `|cand − tmpl| loops > tolerance` (default 1) | Splits the roster cleanly: Air/Crush/Column=0, Fire/Light/Bolt=1, Collection=2. Computed by **Euler's formula** `cycles = E − V + C` on the stroke-endpoint graph (`countClosedLoops` `:129-191`), so a triangle in one stroke and a triangle in three strokes count the same loop. |
| **Ink density** | `inkDensityPasses` `:221-223` | `|cand − tmpl|/tmpl > maxRelDiff` (default 0.50) | `density = totalStrokeLength / bboxDiagonal`. A "+" is ≈1.4; a starburst is 7+ — same template? No. |

The loop counter is worth a look: it stitches stroke endpoints within
`loopClosureFraction · diagonal` into shared graph vertices via union-find
(`:144-191`), and **excludes dot strokes** so injected rings don't inflate the
vertex count (`:130-140`). This is what makes the loop count drawing-style-agnostic.

### Stage 2 — chamfer + worst-pair demote

The expensive step (§5), folded with §7. See `matchInternal` `:190-199`.

### Stage 3 — 3×3 spatial histogram (post-filter, **soft**)

Only runs when the score already cleared `gridCheckScoreThreshold` (default 0.70) —
no point sanity-checking a match that already lost (`:208`). It bins each cloud into
a 3×3 grid over its **own** bbox (`SigilMetrics.gridHistogram` `:99-135`) and
measures histogram-intersection similarity (`gridSimilarity` `:248-260`, `1 − L1/2`
over normalized mass, so different point counts are comparable).

**Decision — soft, not hard.** Earlier this was a hard reject and could silently
drop a strong chamfer match whose mass was slightly off. Now `gridScoreMultiplier`
(`:272-275`) gives full credit at/above `gridMinSimilarity` (default 0.70) and ramps
the *score* linearly toward 0 below it (`s *= gridMult`, `:208-213`). A near-miss is
**demoted, not deleted** — the chamfer's verdict can no longer be overridden outright
by the histogram, only discounted.

> **Diagnostic asymmetry to know about:** `matchVerbose` (`:310-321`) — the
> unfiltered ranking used for the top-3 debug print and the log's `survivors` field
> — runs the **raw chamfer only**, no filters. Comparing it against the filtered
> `match()` result is precisely how you see which template a filter removed. The
> filtered, grid-adjusted trail lives in `decision.templates` instead.

---

## 7. The worst-pair soft-demote ("Phase 2")

This is the single most important tuning insight in the system, and it has its own
data story (`scripts/README.md`, `analyze_recognition_log.py`).

**The finding:** across a labeled corpus, the **mean** chamfer distance *cannot*
separate valid casts from garbage — both distributions overlap. But the **worst
matched-pair** distance can: garbage tends to match a template *on average* while
stranding a few points far away. The mean hides that sprawl; the worst-pair exposes
it (`PDollarPlusRecognizer.java:43-49`).

**The mechanism** (`:190-199`):

```
effDist  = mean + WORST_PAIR_WEIGHT · max(0, worstPair − WORST_PAIR_FREE_ALLOWANCE)
rawScore = max(0, 1 − effDist/√3)
```

- Up to `worstPairFreeAllowance` (default **0.20**), one stray point costs nothing —
  a hook or an endpoint is tolerated.
- Above it, the excess is weighted (`worstPairWeight`, default **0.50**) and added
  to the mean *before* scoring.

The effect: garbage that strands points gets pushed **below** `recognitionMinScore`
and falls out, while a strong mean match survives a single outlier. The pure mean
`d` is still kept untouched for the log's distributions; only the *score* sees the
penalty (`:191-194`). Originally a hard reject ("Phase 2 gate"), it is now folded
into the winner's score, so the `minScore` floor (§8) is the only distance gate left
(`:259-265`).

---

## 8. The decision stage — three gates

After every surviving template is scored, `matchInternal` resolves a winner through
three gates (`:228-300`). All three exist to turn *"the closest class"* into
*"the closest class **if we're confident**, else `unknown`"* — the meta-layer the
reference `$P+` lacks (`:24-26`).

### Gate 1 — minimum score (hard floor) — `:241-265`

If there's no winner, or `bestScore < recognitionMinScore` (default **0.90**), return
`unknown`. Consensus can't rescue a genuinely weak match — only a near-tie. This is
the gate the worst-pair demote feeds into.

### Gate 2 — ambiguity margin — `:267-299`

`gap = bestScore − bestOfOtherSpell`, where the runner-up is the best score of any
**different** spell (`:228-239`). If `gap < recognitionAmbiguityMargin`, reject
rather than risk a confident misclassification.

**Decision — variants of the same spell never fight each other.** The runner-up is
computed per-*spell*, not per-template (`bestPerSpell`, `:166`, `:219-220`). Three
levitation variants clustering at the top is *evidence*, not ambiguity.

### Gate 2½ — consensus tie-breaker — `:267-286`

When the gap is below the margin, count how many of the top-N survivors
(`recognitionConsensusTopN`, default 5) share the winner's spell, and add
`recognitionConsensusBonus` (default 0.01) per agreeing variant to the winner's
score (`:274-285`). Several variants of one spell at the top can push a near-tie over
the line. Set the bonus to 0 to disable.

The shipped margin is intentionally tiny (0.01) — with the consensus rescue and the
worst-pair demote doing most of the work, the ambiguity gate is a light backstop, not
the primary defense.

---

## 9. Config reference

All tunables live in `Config.java` under `── Recognition ($P+) ──` (`:73-119`) and
`── Filtering pipeline ──` (`:120-224`). Defaults shown are the shipped values.

| Key | Default | Range | Stage | Role |
|---|---|---|---|---|
| `resampleN` | 128 | 16–128 | preprocess | Points per cloud. Higher = more accurate, slower. |
| `recognitionMinScore` | 0.90 | 0–1 | gate 1 | Confidence floor. Below → `unknown`. |
| `recognitionAmbiguityMargin` | 0.01 | 0–0.30 | gate 2 | Required gap over a different spell. |
| `recognitionConsensusBonus` | 0.01 | 0–0.10 | gate 2½ | Score added per agreeing variant. |
| `recognitionConsensusTopN` | 5 | 1–20 | gate 2½ | How many survivors the consensus inspects. |
| `worstPairFreeAllowance` | 0.20 | 0–0.60 | §7 | Free worst-pair distance (no penalty). |
| `worstPairWeight` | 0.50 | 0–2.0 | §7 | Weight on worst-pair excess. |
| `aspectRatioTallThreshold` | 0.85 | 0.50–1.00 | pre | AR below = tall. |
| `aspectRatioWideThreshold` | 1.18 | 1.00–2.00 | pre | AR above = wide. |
| `dotCountTolerance` | 1 | 0–5 | pre | Allowed dot-count delta. |
| `loopCountTolerance` | 1 | 0–3 | pre | Allowed loop-count delta. |
| `loopClosureFraction` | 0.12 | 0.02–0.30 | pre | Endpoint-stitch radius (× diagonal). |
| `minPointsPerStroke` | 4 | 2–16 | preprocess | Resample floor per stroke. |
| `inkDensityMaxRelDiff` | 0.50 | 0.05–1.0 | pre | Allowed ink-density deviation. |
| `gridCheckScoreThreshold` | 0.70 | 0.5–1.0 | post | Score above which grid check runs. |
| `gridMinSimilarity` | 0.70 | 0.30–0.95 | post | Full-credit grid similarity; below = ramp down. |
| `dotInjectionRadius` | 0.01 | 0.001–0.10 | preprocess | Dot detect threshold + injected ring radius. |
| `dotInjectionCirclePoints` | 8 | 3–32 | preprocess | Points per injected dot ring. |
| `recognitionLoggingEnabled` | false | — | instrument | Append a JSONL record per recognized sigil. |

`recognitionMinScore = 0.90`, `worstPairFreeAllowance = 0.20`, and
`worstPairWeight = 0.50` were chosen together on a **119-sample labeled corpus**
(2026-06): ≈85% valid recall, ≈89% garbage rejected. The provenance is recorded in
the config comments themselves (`Config.java:83-86`, `:191-210`).

---

## 10. Templates: data-driven and hot-reloadable


- **Source:** `data/witchhatateliermod/spell_templates/*.json` (currently air,
  column, earth, fire, water, levitation, dispersion, pull, light, crush, plus
  `activation_ring.json` with `is_ring: true`).
- **Loader:** `SpellTemplateLoader` (`SpellTemplateLoader.java`), a
  `SimpleJsonResourceReloadListener` registered in `events/ModEvents`. It runs on
  server start **and every `/reload`**, so authors iterate without restarting.
- **Format:** `spell_name`, optional `is_ring`, and `variants[].points[]` of
  `{x, y, stroke_id}` in normalized `[0,1]` space (`:22-36`, `parsePoints` `:99-109`).
- **Each variant is preprocessed once at load** (`:80-86`) — the chamfer compares
  against pre-normalized clouds, never redoing the pipeline per match. The **raw**
  cloud is kept too (`Template.rawCloud`), so a future preprocessing change can
  re-derive new processed clouds without re-authoring any template
  (`Template.java:5-8`).
- **Registry:** `TemplateRegistry` (singleton, cleared+rebuilt each reload). It
  separates `all()` (content) from `allRing()` (ring validators) by the `is_ring`
  flag (`TemplateRegistry.java:30-38`), so the recognizer never tries to match a
  content sigil against the activation ring.

**Adding a glyph** is therefore: drop a `spell_templates/*.json` shape, and (to wire
meaning) add the `ElementType`/`FormType`/`EffectType` enum constant + its default
implementation (see `docs/spell_pipeline.md` §9). Existing elements/forms/effects
combine with it automatically. The recognizer needs nothing else.

---

## 11. Instrumentation & the offline tuning loop

The recognizer is built to be tuned from real data, not guessed at.

### In-game

- `F9` — `RecognitionDebugScreen`; `F8` — `DebugTemplateScreen`.
- `/spell debug` — toggles the per-sigil top-3 chat output (`SaveGestureHandler.java:306-327`).
- `/spell label <word>` / `clear` — stamps a ground-truth label on every logged
  recognition; `garbage` is the negative class.
- `recognitionLoggingEnabled` — writes one JSON object per sigil to
  `run/logs/spell_recognition.jsonl` via `RecognitionLog` (`RecognitionLog.java`).

Each log record (`RecognitionLog.Entry`, `:44-60`) is a complete reconstruction of
one decision: raw strokes, processed cloud, **every** template's chamfer
distance + score (`survivors`, the unfiltered `matchVerbose` view), the filtered
decision trail (`decision`, the real `match()` path via `matchTraced`), the final
result, and a snapshot of every live threshold (`:111-124`). The two rankings are
complementary — `survivors` feeds distribution plots, `decision.templates` feeds
faithful replay (`:53-55` of `scripts/README.md`).

The traced path (`matchTraced` → `MatchTrace`/`TemplateTrace`,
`PDollarPlusRecognizer.java:80-141`) only runs when logging is on; the normal cast
takes the lighter `match()` with identical results (`SaveGestureHandler.java:262-271`).

### Offline (Python, stdlib only — `scripts/`)

```
in-game draw (labeled) → spell_recognition.jsonl
   ├─ explain_recognition.py   → why did ONE result happen? (the decision trail)
   ├─ analyze_recognition_log.py → valid-vs-garbage distributions per channel
   └─ build_corpus.py (dedup, persist) → dataset/spell_corpus.jsonl
                                            └─ tune_corpus.py → best decision params
```

`tune_corpus.py` is the main tool: because `decision.templates` already stores each
template's `dist`/`worst`/`gridSim` + prefilter verdict, it **replays the entire
decision stage over the whole corpus in milliseconds**, without re-running the
chamfer or redrawing. You draw each sample exactly once (it's preserved by raw
strokes), then sweep `recognitionMinScore` / `worstPairFreeAllowance` /
`worstPairWeight` / margin / consensus / `gridMinSimilarity` offline.

**Scope limit worth internalizing:** the offline tuner only covers **decision-stage**
params. Preprocessing/prefilter params (`resampleN`, dot injection, aspect/density
prefilters) change the chamfer *geometry* baked into the log, so they can't be tuned
from it — that would need a harness that re-runs the real Java pipeline on the
preserved `rawStrokes` (not built yet). See `scripts/README.md` §"Scope".

---

## 12. Design rationale, condensed

| Decision | Why | Where |
|---|---|---|
| Use `$P+`, not `$1`/template-stretch | Sigils are multi-stroke and stroke-order-free; a point cloud doesn't care how many pens-down or in what order. | `PDollarPlusRecognizer.java:11-16` |
| Third "turning angle" channel | `(x,y)` can't separate a corner from a curve through the same point. | `Point.java:8-11`, `:449-458` |
| Symmetric chamfer (`min` of both directions) | Absorbs the 180° principal-axis ambiguity and asymmetric point counts without a PCA-flip guess. | `:331-340` |
| Cheap pre-filters before the O(N²) chamfer | Re-introduce the scale/aspect/density invariants the chamfer discards; skip obviously-wrong templates for free. | `SigilFilters.java:11-37` |
| Grid post-filter is **soft** | A hard reject silently dropped strong matches with slightly-off mass; demotion preserves the chamfer's authority. | `:262-275`, `:201-213` |
| Worst-pair demote instead of mean threshold | The mean can't separate garbage; the worst stranded point can. Data-driven, from the corpus. | `:43-49`, `:190-199` |
| Per-spell (not per-template) ambiguity | Variants of one spell are corroboration, not competition. | `:166`, `:228-239` |
| Consensus rescue | Several variants clustering at the top is real evidence a near-tie should resolve in their favor. | `:267-286` |
| Templates as datapack, preprocessed once, raw kept | Hot-reload iteration; future pipeline changes don't invalidate authored templates. | `SpellTemplateLoader.java`, `Template.java:5-8` |
| Full JSONL instrumentation + offline replay | Tune from labeled reality in milliseconds, not by rebuilding the mod. | `RecognitionLog.java`, `scripts/README.md` |

---

## 13. Reading order for the code

1. `PointCloudPreprocessor.process` (`:348-369`) — the pipeline, top to bottom.
2. `PDollarPlusRecognizer.cloudDistanceStats` (`:364-418`) — the metric.
3. `PDollarPlusRecognizer.matchInternal` (`:143-301`) — the three stages and three
   gates, the whole decision in one method.
4. `SigilFilters` — each gate's geometry, in isolation.
5. `scripts/README.md` — how the shipped numbers were chosen, and how to re-tune.
