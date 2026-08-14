# The Spell System — from recognized glyphs to a structured graph

What happens **after** the recognizer hands back a list of named glyphs: how those
names become a structured `SpellGraph` (element, forms, effects, convergence, size,
symmetry). Every stage names the code that implements it and explains **why** it's
shaped the way it is.

> Package: `spell/compiler/` (`src/main/java/com/crsocial/witchhatatelier/spell/`)
> Orchestrator: `network/SaveGestureHandler.runSpellPipeline()`
> Companion doc: `docs/recognizer.md` covers everything *up to* recognition.

**Status: step 0.** The pipeline stops here on purpose. A previous iteration
(`spell/composition/` + `spell/cast/` — a **compositional** engine that resolved the
graph into mechanical numbers and ran channeled/instantaneous casts against the world)
grew into more machinery than the project needed and was removed wholesale to restart
that half of the design from scratch. Nothing downstream of `SpellGraph` exists right
now: closing a ring reports what was recognized (action-bar + `/spell debug` chat +
the `InscriptionSummary` stamped on the paper) and nothing else — no world effect, no
channel, no fuel/duration. What replaces `CompositionEngine` is future work, not yet
designed.

---

## 1. The shape of the whole thing

The recognizer's output is a `List<RecognitionResult>` — one entry per content
cluster, each a spell name like `"fire"`, `"column"`, or `"unknown"`. From there:

```
recognitions ─► SpellGraphBuilder ─► SpellGraph   (structure: what was drawn)
                                          │
                                          ▼
                              InscriptionSummary    (feedback: action-bar / tooltip / debug chat)
```

`SpellGraph` is the **terminal** artifact of the pipeline today — nothing consumes it
to produce a world effect. The orchestration lives in `SaveGestureHandler.runPipeline`
— read that method first if you only read one thing; the section below expands the
graph-building stage.

---

## 2. `SpellGraphBuilder`: names → structure

`SpellGraphBuilder.build(...)` walks the parallel `clusters` / `recognitions` lists and
sorts each recognized name into one of four buckets via enum lookups, in order:

- **Elements** — `ElementType.fromSpellName`: the five elements `EARTH, AIR, WATER,
  FIRE, LIGHT`.
- **Forms** — `FormType.fromSpellName`: `COLUMN, DISPERSION, BOLT` — "how it
  manifests." Each carries a `FormRole` (`CARRIER`/`RIDER`), metadata reserved for a
  not-yet-built "combining forms" feature; until that lands every form is just a
  structural occurrence on the graph.
- **Effects** — `EffectType.fromSpellName`: `LEVITATION, CRUSH, PULL, COLLECTION,
  EXTINGUISH` — "how behaviour is modified." `ExecutionMode`/`canCarry()` remain on the
  type as classification metadata, unused until something downstream reads them again.
- **Convergence** — a literal `"convergence"` name sets a bare `boolean` flag on the
  graph, not a Form/Effect node.

Anything `unknown` is skipped; anything recognized but none of the above is logged and
ignored.

### The one structural rule

**Exactly one *element* per ring.** The decisions:

- **No element → reject.** Returns `CompileResult.rejected("No element recognized…")`.
- **Repeats of the same element are allowed.** Drawing `fire + fire` is not an error —
  `sigilStack` counts the copies; the best-drawn copy (highest recognizer quality)
  becomes the representative `core`.
- **Two *different* elements → reject.** Mixed elements need nested rings (not built
  yet).

Why enforce so little structurally? Forms and effects combine freely — multiple of the
same type stack, different types coexist — and whatever consumes that combination
resolves what it *means* later, not here. The builder's one job is guaranteeing one
invariant (one element) so downstream code never re-checks structural validity.

### What the graph carries

`SpellGraph` bundles: `root` (`RingNode` — enclosing ring's stroke IDs + mean radius),
`core` (`ElementNode` — the single element + centroid + quality), `sigilStack` (how many
identical elements were drawn, ≥1), `convergence` (the boolean flag), `forms` (one
`FormNode` per occurrence), `effects` (one `EffectNode` per occurrence), `symmetry`
(`SymmetryReport`, see below), `size` (`SizeReport` — content bbox ÷ ring bbox,
normalized to `[0,1]`), and `inner` (reserved for ring-nesting, always empty today).

Derived views: `formsByType()` / `effectsByType()` group occurrences into
`FormBundle`/`EffectBundle`s; `describeForm()` renders a human label like `"Column +
Levitation"`; `toDebugString()` is the multi-line server dump (`/spell debug`).

### Symmetry — glyph placement as force vectors

`SymmetryAnalyzer.analyze` treats every drawn form/effect glyph as a **force vector**:
its displacement from the element centre, weighted by recognizer quality (both axes
feed one merged list via `GlyphPlacement`, so a Form and an Effect drawn together are
weighed the same way). The vector sum is the net pull; dividing its length by the
scalar sum of individual lengths gives an *imbalance* in `[0,1]` — `0` = opposing
glyphs perfectly cancel, `1` = all aligned.

**The decision worth noting:** a configurable **deadzone**
(`Config.symmetryCancelDeadzone`) snaps near-balanced placements all the way to zero
net direction. Without it, a hand-drawn "balanced" spell would always leak a small
leftover direction because no human draws a perfect mirror. The deadzone makes
*deliberately directionless* drawings reliably drawable.

Both `radialScore` (neatness) and `netDirection` are computed and carried on the graph,
unused by anything downstream today — reserved for whatever duration/direction model
replaces the removed composition engine.

---

## 3. Casting context — how the medium colors the draw

Before compiling, `SaveGestureHandler.buildCastingContext` builds a `CastingContext`
describing *where* the inscription lives:

- `MediumKind` — `PAPER_ITEM` (held), `PLACED_PAPER` (surface), or `INSCRIBED_BLOCK`
  (reserved).
- `originWorld` / `surfaceNormal` — world anchor + the casting surface's outward normal.
- `drawRotationDeg` — the placed paper's in-plane drawing rotation.
- `sourceBlock` — the placed-paper block, or `null` for a held-paper draw.

Nothing in the compiler stage uses `originWorld`/`surfaceNormal`/`sourceBlock` yet —
they're threaded through for whatever reads world position next.

---

## 4. Feedback — `InscriptionSummary`

`spell/feedback/InscriptionSummary` derives a display-only summary from the
`CompileResult`: `state` (`READY`/`FIZZLE`/`ILLEGIBLE`), the core element, form/effect
occurrence counts, whether convergence was drawn, and the core element's recognizer
quality. This is what the action-bar line, the item tooltip, and the placed-paper
canvas header all read — the player can always tell what a paper holds without any
world-effect system existing behind it. `QualityGrade` turns the raw quality float into
a letter grade (S–F) for display.

---

## 5. Adding content (compiler vocabulary)

**A new element**: register its gesture template and add the `ElementType` enum
constant + `fromSpellName` case.

**A new form or effect**: register its gesture template and add the enum constant +
`fromSpellName` case.

Neither currently "does" anything beyond being structurally recognized and shown in
feedback — there is no material/behaviour resolution layer to also register with.

---

## 6. Explicitly deferred

- **Everything downstream of `SpellGraph`.** World-effect resolution (was
  `CompositionEngine`) and cast execution (was `spell/cast/`) were removed and are
  being redesigned from scratch.
- **Combining forms.** A carrier/rider split (`column + bolt` = a column that fires
  bolts) — `FormType.FormRole` is reserved for it but no mechanism resolves it yet.
- **Completion by joining two halves.** A possible second ring-closure-alternative
  trigger. No mechanic uses it.

---

## 7. Reading order for the code

1. `SaveGestureHandler.runPipeline` — the orchestration: recognize → compile →
   report, in one method.
2. `SpellGraphBuilder.build` — names → structure, the four-way cascade, and the one
   rule.
3. `SymmetryAnalyzer.analyze` — glyph placement as force vectors.
4. `InscriptionSummary` — how the graph becomes player-facing feedback.
