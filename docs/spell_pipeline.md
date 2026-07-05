# The Spell System — from recognized sigils to a world effect

What happens **after** the recognizer hands back a list of named sigils: how those
names become a structured spell, how a data-driven `(Sigil × Sign)` matrix turns
that structure into mechanical numbers, and how those numbers run against the world
— instantaneously or as a sustained channel. Every stage links to the code with
`file:line` references and explains **why** it's shaped the way it is.

> Packages: `spell/compiler/`, `spell/meaning/` (+ `meaning/effect/`,
> `meaning/sign/`), `spell/cast/`
> (`src/main/java/com/crsocial/witchhatatelier/spell/`)
> Orchestrator: `network/SaveGestureHandler.runSpellPipeline()`
> Companion doc: `docs/recognizer.md` covers everything *up to* recognition.

---

## 1. The shape of the whole thing

The recognizer's output is a `List<RecognitionResult>` — one entry per content
cluster, each a spell name like `"fire"`, `"column"`, or `"unknown"`. From there:

```
recognitions ─► SpellGraphBuilder ─► SpellGraph        (structure: what was drawn)
                                          │
                          MeaningEngine ──┤  resolves (Sigil × Sign) against the matrix
                                          ▼
                                   ExecutableSpell       (mechanics: numbers + ops)
                                          │
            ┌─────────────────────────────┼─────────────────────────────┐
            ▼                             ▼                             ▼
   SpellExecutor.run            SpellCastManager           PlacedPaperCastManager
   (instantaneous surface)      (channeled hand cast)      (sustained surface cast)
            │                             │                             │
            └──────────────► EffectKind.execute / begin·tick·end ◄──────┘
                                          │
                                     world mutation
```

Two structural commitments shape this half of the system:

- **Meaning is a matrix, not a list.** A spell's mechanics are
  `(Sigil) × (Signs) × (Context)` resolved *at evaluation time* against
  data-driven cells — never a hardcoded `SpellID`. Add a sigil or sign and it
  auto-combines with everything already registered. This is why
  `MeaningEngine.evaluate` iterates *every* sign bundle even though most cells
  don't exist yet (`MeaningEngine.java:21-34`).
- **Mechanics are decoupled from visuals.** The graph emits the server-side
  `ExecutableSpell`; the client reads the `SpellGraph` for visuals separately. The
  `ExecutableSpell` doc note makes this explicit (`ExecutableSpell.java:10-14`).
  Everything below is the **server / mechanical** half.

The dispatch fork lives in `SaveGestureHandler.java:396-417` — read that block first
if you only read one thing; the three sections below expand each arm.

---

## 2. Stage 1 — `SpellGraphBuilder`: names → structure

`SpellGraphBuilder.build(...)` (`SpellGraphBuilder.java:38-95`) walks the parallel
`clusters` / `recognitions` lists and sorts each recognized name into one of two
buckets via the enum lookups:

- **Sigils** — `SigilType.fromSpellName` (`SigilType.java:22-32`): the five elements
  `EARTH, AIR, WATER, FIRE, LIGHT`.
- **Signs** — `SignType.fromSpellName` (`SignType.java:63-76`): the eight signs,
  each tagged with a `Tier` (MANIFESTATION / FORCE / META), a `StackingMode`
  (MAGNITUDE / REPETITION), and a `ManifestationRole` (CARRIER / RIDER / NONE)
  (`SignType.java:12-20`).

Anything `unknown` is skipped (`SpellGraphBuilder.java:53`); anything recognized but
neither sigil nor sign is logged and ignored (`:68-69`).

### The one structural rule

**Exactly one *element* per ring** (`SpellGraphBuilder.java:72-87`). The decisions:

- **No sigil → reject.** Returns `CompileResult.rejected("No sigil recognized…")`
  (`:75-77`); the inscription falls back to *Prepared*.
- **Repeats of the same element are allowed and amplify.** Drawing `fire + fire` is
  not an error — `sigilStack` counts the copies (`:87`) and the meaning engine turns
  that into a power multiplier (§4.4). The best-drawn copy (highest recognizer
  quality) becomes the representative `core` (`:85`).
- **Two *different* elements → reject.** Mixed elements need nested rings, a
  deliberate "use the composition mechanism" message (`:80-84`).

Why enforce so little? Because *signs combine freely* — multiple of the same type
stack, different types coexist, and the carrier/rider/force distinctions are resolved
later by the meaning engine, not gated here (`SpellGraphBuilder.java:18-24`). The
builder's job is to guarantee one invariant (one element) so downstream code never
re-checks structural validity (`SpellGraph.java:11-15`).

### What the graph carries

`SpellGraph` (`SpellGraph.java:25-31`) bundles:

| Field | Built by | Meaning |
|---|---|---|
| `root` (RingNode) | `buildRing` `:112-136` | The enclosing ring's stroke IDs + mean radius. |
| `core` (SigilNode) | best-quality sigil `:79-86` | The single element + its centroid + quality. |
| `sigilStack` | `sigils.size()` `:87` | How many identical elements were drawn (≥1). |
| `modifiers` (List<SignNode>) | per sign occurrence `:64` | One node per drawn sign, with position + indicative angle + quality. |
| `symmetry` (SymmetryReport) | `SymmetryAnalyzer.analyze` `:90` | How the signs are placed around the sigil (see §2.1). |
| `size` (SizeReport) | `buildSize` `:138-155` | Content bbox area ÷ ring bbox area, normalized to `[0,1]`. |
| `inner` | `Optional.empty()` `:93` | Nested ring — reserved for ring-nesting (not built yet). |

`SpellGraph` exposes derived views the meaning engine and UI use without re-deriving:
`signsByType()` groups occurrences into `SignBundle`s for stacking math
(`SpellGraph.java:34-44`); `carriers()`/`riders()` split manifestation signs by role
(`:47-62`); `describeForm()` renders the human label like `"Dispersion with Bolt
impacts"` (`:69-79`); `toDebugString()` is the multi-line server dump
(`:97-132`).

### 2.1 Symmetry — sign placement as force vectors

`SymmetryAnalyzer.analyze` (`SymmetryAnalyzer.java:27-65`) treats each sign as a
**force vector**: its displacement from the sigil centre, weighted by recognizer
quality (`:39-45`). The vector sum is the net pull; dividing its length by the scalar
sum of individual lengths gives an *imbalance* in `[0,1]` — `0` = opposing signs
perfectly cancel, `1` = all aligned (`:47-50`).

**The decision worth noting:** a configurable **deadzone**
(`SYMMETRY_CANCEL_DEADZONE`) snaps near-balanced placements all the way to zero net
direction (`:52-59`). Without it, a hand-drawn "balanced" spell would always leak a
small leftover direction because no human draws a perfect mirror. The deadzone makes
*deliberately directionless* spells reliably drawable. The net direction this
produces is what the meaning engine later projects into world space as the spell's
aim (§4.3).

---

## 3. Casting context — how the medium colors the cast

Before evaluation, `SaveGestureHandler.buildCastingContext` (`:475-…`) builds a
`CastingContext` (`CastingContext.java:13-19`) describing *where* the inscription
lives:

- `MediumKind` — `PAPER_ITEM` (held → hand cast), `PLACED_PAPER` (surface), or
  `INSCRIBED_BLOCK` (reserved) (`CastingContext.java:22`).
- `originWorld` / `surfaceNormal` — world anchor + the casting surface's outward
  normal.
- `drawRotationDeg` — the placed paper's in-plane drawing rotation, so spell
  direction/skew follow the *rendered* drawing rather than a fixed world axis
  (`CastingContext.java:30-38`, read from the block entity's rotation segment at
  `SaveGestureHandler.java:494-495`).
- `sourceBlock` — the placed-paper block a summon anchors its "fuel" to, or `null`
  for hand casts.

Reserved `ink`/`wand` slots are `null` today — threaded through so future systems
land without an API change (`CastingContext.java:24-28`). **The medium never changes
*what* a spell is, only where it emerges and how it's driven** (`:21`).

---

## 4. Stage 2 — `MeaningEngine`: structure → mechanics

`MeaningEngine.evaluate(graph, ctx)` (`MeaningEngine.java:40-161`) is where the
matrix resolves. It returns `Optional<ExecutableSpell>` — **empty** means "no matrix
cell matched", which the orchestrator surfaces as the `INERT` inscription state:
an action-bar line at save time, plus the state line on the paper's tooltip and the
reopened canvas (the old per-cast chat breakdown is now `/spell debug`-only). The
pipeline also runs on **ring-less** saves — recognize + compile + meaning, no
execution — purely to stamp this `InscriptionSummary` (`spell/feedback/`) onto the
paper item / placed-paper block entity.

Before clustering, the pipeline excludes a detected **ring-in-progress** chain from
the content set (`TriggerEvaluator.findRingInProgress`: winds ≥
`ringInProgressMinWindingDegrees`, ring-sized, encloses other strokes — no closure
gates): an unfinished ring's hull would otherwise macro-merge every sigil into one
unrecognizable cluster. **Recorded trade-off:** a large circle-like *sign* that fully
encloses other strokes can be misclassified as ring-in-progress on ring-less saves;
this only affects the preview summary — at cast time the client's strict closed-ring
trigger decides the real ring, and content is split by its verdict.

### 4.1 One op per sign bundle that has a cell

For each `SignBundle` in `graph.signsByType()` (`MeaningEngine.java:53`):

1. **Look up the cell** `MatrixRegistry.find(element, bundle.type())` (`:54`). No
   cell → skip the bundle (`:55-60`). *This is the matrix in action: the `(FIRE,
   LEVITATION)` pairing is data, not code.*
2. **Resolve the effect implementation** `EffectRegistry.find(entry.behaviorKind())`
   (`:63`). An unknown `behavior_kind` → warn + skip (`:64-69`).
3. **Apply the stacking curve** (`:71-73`): for `MAGNITUDE` signs the bundle count is
   folded into a scalar via the cell's `StackingCurve`; `REPETITION` signs pass
   `1.0` here and instead loop at execution time.
4. **Compute this op's power/aoe** (`:75-76`):
   `opPower = basePower × magnitudeScalar × quality × SizeScaling.powerMultiplier(size)`.
5. **Emit a `BehaviorOp`** carrying the kind, the parsed payload, and the per-tick /
   per-use costs (`:78-80`, `BehaviorOp.java:25-26`).

The engine tracks the **dominant** op (the one that set `maxPower`) and remembers its
`basePower` as the cost reference (`:82-85`) — important for cost scaling in §4.5.

If no bundle produced an op, return empty → Prepared (`:91-96`).

### 4.2 The stacking curves

`StackingCurve` (`StackingCurve.java:10-34`): `LINEAR` (×count), `LOGARITHMIC`
(`1 + log₂ count`), or `CAPPED` (min(3, count)). `apply(1) == 1.0` always, and an
absent JSON field defaults to `LINEAR` (`:27-33`). This is how a cell author chooses
whether stacking a sign three times triples the effect or yields diminishing returns —
purely a data choice.

### 4.3 Direction — canvas vector to world

`resolveDirection` (`MeaningEngine.java:176-185`) takes the symmetry net direction
(canvas space), rotates it by the paper's `drawRotationDeg` via
`CanvasDirection.toWorldXZ`, and keeps the surface-normal's vertical term — so a
directional spell points the way the drawing leans on the actual surface. A
zero-length net (balanced/deadzoned) yields a zero vector = radial/undirected.

### 4.4 Amplifiers stack multiplicatively

After the per-bundle loop, several independent amplifiers fold into the final
magnitude:

- **Custom sign behaviours** (§5) contribute an origin offset, a direction bias, and
  power/aoe multipliers — but **only for non-hand casts** (`MeaningEngine.java:108-125`).
  Hand casts re-aim every tick, so a fixed canvas-derived offset would fight the live
  aim; they're skipped until designed properly (`:108-112`).
- **Sigil stack** — repeated elements multiply power by
  `1 + (stack−1) × SIGIL_STACK_POWER_PER_EXTRA` (`:127-129`, `Config.java:228`).
- **Size** — `SizeScaling.powerMultiplier(size)` already entered each op's power
  (`:75`); see §4.6.

Final magnitude is assembled at `:131-137`; final direction (net + bias, normalized)
at `:151-152`; final world origin (ctx origin + offset) at `:154`.

### 4.5 Cost scales with power

The matrix `cost.per_tick`/`per_use` are the **base** — what a spell drawn at its
reference power pays. Amplifiers that raise power above baseline raise cost by the
same factor, tunable by `COST_POWER_SCALING` (`MeaningEngine.java:139-149`,
`Config.java:290`): `0` = flat cost, `1` = 1:1 with power, `>1` = a steeper toll on
heavy casts. `powerFactor = finalPower / dominantBasePower` (`:145`) is why the engine
bothered to remember the dominant op's base power back in §4.1.

### 4.6 Size curve

`SizeScaling.powerMultiplier` (`SizeScaling.java:31-42`) maps normalized drawn size
to a multiplier anchored so a **reference** size → ×1.0, **full** size → up to
`SIZE_POWER_MAX`, and a vanishing size → a `0.1` floor. The decision recorded in the
class doc (`:6-18`): previously size could only ever *reduce* a spell (a full-size
draw was ×1.0 with no way to draw "big for more power"); the anchored, monotonic
curve fixes that without moving existing spells. `steerMultiplier` (`:49-52`) raises
the same curve to `DIRECTION_SIZE_EXPONENT` so size weighs more on steering than on
raw power — used by `ColumnSignBehavior`.

### 4.7 The output

`ExecutableSpell` (`ExecutableSpell.java:29-39`) is the serializable mechanical
payload: element, the list of `BehaviorOp`s, the `Magnitude` (power/aoe/quality/size),
an `Origin` enum, world origin/normal/direction vectors, aggregate costs, an optional
`inner` spell, and the `sourceBlock`. Its `withOrigin(...)` returns a re-aimed copy
each tick of a channel without mutating shared vectors (`:41-49`) — the mechanism the
hand-cast manager relies on.

---

## 5. Custom sign behaviours — escape hatch from pure data

Most signs are *fully* expressed by their matrix cell (base power/aoe + stacking
curve). A few need mechanics the matrix can't express — shifting the origin,
biasing direction. Those implement `SignBehavior` (`SignBehavior.java:24-42`) and
register in `SignBehaviorRegistry` (`SignBehaviorRegistry.java:33-38`): currently
`LEVITATION` and `COLUMN`. Any sign *not* listed there is matrix-only — the engine
calls `find()` and skips silently when empty (`MeaningEngine.java:116-117`).

A behaviour receives the sigil element, the bundle, the full graph, the context, and
the magnitude, and returns a `SpellModification` (origin offset + direction bias +
power/aoe multipliers) or `SpellModification.NONE`. The per-element variation is the
point: the interface doc cites Levitation raising the origin for Fire (heat rises)
but sinking it for Earth (weight resists) (`SignBehavior.java:16-19`). These compose
additively/multiplicatively with everything else in §4.4.

---

## 6. Stage 3 — dispatch to the runtime

`SaveGestureHandler.java:396-417` picks one of three paths from the medium and the
spell's cost profile:

| Condition | Path | Behaviour |
|---|---|---|
| Hand cast (`blockOrigin == null`) | `SpellCastManager.start(sp, spell, inscribed)` `:402` | Channeled, aim-following; paper consumed when the channel ends. |
| Surface cast **with** `totalCostPerTick > 0` | `PlacedPaperCastManager.start(...)` `:408-409` | Sustained channel anchored to the block; block marked spent when fuel drains. |
| Surface cast with no per-tick cost | `SpellExecutor.run(...)` then `consumeMedium` `:412-415` | Fire once, spend immediately. |

The per-tick-cost test is the dividing line between "one-shot" and "sustained"
surface casts (`PlacedPaperCastManager.java:30-34`). The medium is **never** consumed
twice — the channeled managers own consumption (so the one-shot path is the only one
that calls `consumeMedium`).

### 6.1 The Prepared → Activated → Used lifecycle

`consumeMedium` (`SaveGestureHandler.java:438-461`) marks the inscription **spent**
rather than deleting it: a placed paper stays in the world but rejects further casts
(`:443-447`); a held inscribed paper stays in the inventory but can't be re-cast
(`:455-459`). "Spent, not gone" is what makes the *Prepared* state (draw now, fire
later) coherent.

### 6.2 `EffectKind` — the behaviour contract

Every `behavior_kind` string resolves to an `EffectKind` singleton registered at
class-load in `EffectRegistry.bootstrap()` (`EffectRegistry.java:34-40`): currently
`flame_pillar`, `particle`, `pyreball`, `stone_pillar`, `wind_pillar`. The interface
(`EffectKind.java:20-73`) has two halves:

- `execute(level, caster, spell)` — the one-shot world mutation.
- A **channel lifecycle** `begin` / `tick` / `end` (`:44-72`). The default `begin`
  just calls `execute` then holds, so a block effect (a pillar) fires once and stays;
  an entity effect overrides `tick` to follow the caster's live aim.

`SpellExecutor.forEachDistinctKind` (`SpellExecutor.java:51-66`) is the shared
dispatch helper: it deduplicates ops by kind (so stacked ops sharing a behaviour fire
their kind **once**, and the kind itself iterates `spell.ops()` internally). Both the
one-shot executor and both channel managers route through it, so they all dedupe
identically.

### 6.3 Worked example — `pyreball` (Fire + Levitation)

The matrix cell `spell_matrix/fire/levitation.json` declares
`behavior_kind: "pyreball"`, a `unique_entity` effect naming
`witchhatateliermod:pyreball` with `fallback_block: minecraft:fire`, and
`cost.per_tick: 1.0`. Because it has a per-tick cost, a hand cast runs as a channel:

- `PyreballEffect.begin` (`PyreballEffect.java:73-86`) computes a lifetime from
  `fuel.capacity / costPerTick`, spawns one tracked orb, and stashes it in the
  per-cast `EffectScratch`. If the entity can't spawn, it falls back to the one-shot
  `execute` path.
- `tick` (`:88-94`) re-positions the orb to `spell.originWorld()` — which the manager
  has already re-aimed to the live crosshair — so the orb follows the player's look.
- `end` (`:96-101`) discards the orb.
- The orb's max scale is derived from `spell.magnitude().power()` (`:123-124`), so all
  the amplifier math from §4 reaches the visible result.

The one-shot `execute` (`:50-67`) is what a *surface* pyreball or an entity-spawn
failure uses; it anchors the orb to `spell.sourceBlock()` so it dissipates when its
fuel block is broken (`:170`).

---

## 7. The channeled-cast managers

Two managers share the `begin`/`tick`/`end` lifecycle and the `SpellFuel` budget but
differ in what they key on and how they re-aim.

### 7.1 `SpellCastManager` — hand casts (`SpellCastManager.java`)

Keyed by **player UUID**. Started from recognition success on a held paper
(`:61-102`):

- Stamps the inscribed paper with a random `castId` (`:80-81`) so the cast can find
  "its" paper even after slot moves (`findByCastId`), and remembers whether it began
  held vs. in-inventory (`heldAtStart`, `:82`).
- Fires every effect's `begin` with the spell re-aimed to live aim, then registers the
  cast and broadcasts casting state to clients (`:87-98`).

Driven once per `ServerTickEvent.Post` via `tickAll` (`:114-163`, wired in
`SpellCastEvents.java:19-23`):

- **Teardown conditions:** player gone/removed/dying (`:122-126`); the cancellation
  rule depends on `heldAtStart` — a held cast cancels when the paper leaves *both*
  hands, an inventory cast only when the paper disappears entirely (`:128-140`).
- **Drive:** rebuild the spell at the live crosshair (`liveSpell`, `:210-215`) and run
  every kind's `tick` (`:142-153`).
- **Fuel:** drain `totalCostPerTick`; `SpellFuel.consume` returning false ends the
  cast as `FUEL_EXHAUSTED` (`:155-159`).

`endCast` (`:167-188`) runs every `end`, then `consumePaper` marks the paper spent and
clears its `castId` (`:190-198`). Logout cancels via the game-bus subscriber
(`SpellCastEvents.java:25-30`).

### 7.2 `PlacedPaperCastManager` — sustained surface casts (`PlacedPaperCastManager.java`)

The `PLACED_PAPER` analogue. Keyed by **dimension + block position** (`SurfaceKey`,
`:48`) instead of by player, and the origin/normal are **fixed to the block face** —
no live aim, the spell is used as-is each tick (`:166-167`). Only spells with a
per-tick cost ever reach here (`:30-34`). Teardown happens when fuel drains, the block
is broken/unloaded, or it's already spent (`:100-129`); `endCast` optionally marks the
source block spent (`:142-159`).

### 7.3 `SpellFuel` and `CastContext`

`SpellFuel` (`SpellFuel.java`) is a simple capacity counter starting at
`DEFAULT_SPELL_FUEL` (`Config.java:283`); `consume` returns false once empty, which is
the universal "channel over" signal, and `progress()` exposes the consumed fraction.
Each lifecycle hook receives a `CastContext` (`CastContext.java:20-30`) — the spell
recomputed *for this tick*, plus an `EffectScratch` (per-cast mutable bag, e.g. the
tracked orb) and the fuel handle. `level`/`caster` may be null during `end` after a
logout, which effects must tolerate (`:17-18`).

---

## 8. Data-driven content — matrix cells

The `(Sigil × Sign)` matrix is a datapack, loaded by `MatrixLoader`
(`MatrixLoader.java`), a `SimpleJsonResourceReloadListener` registered alongside the
template loader in `ModEvents.onAddReloadListeners` (`ModEvents.java:73-75`). Both
reload on server start **and every `/reload`**, so authors iterate without restarting.

**The path is the key.** A file at `spell_matrix/earth/column.json` registers the
`(EARTH, COLUMN)` cell; the `sigil`/`sign` fields inside the JSON are only validated
against the path, which wins on disagreement (`MatrixLoader.java:69-97`). A cell
names:

| Field | Parsed at | Role |
|---|---|---|
| `behavior_kind` | `:99` | Which `EffectKind` runs (e.g. `"stone_pillar"`). |
| `base.power` / `base.aoe` | `:101-103` | Baseline magnitude before scaling (default 1.0). |
| `effects[]` | `:105-109` | Raw payloads the effect kind parses (`EffectKind.parsePayload`). |
| `stacking_curve` | `:111-112` | LINEAR / LOGARITHMIC / CAPPED (default LINEAR). |
| `cost.per_tick` / `per_use` | `:114-116` | Fuel costs (default 0.0). |
| `context_modifiers[]` | `MatrixLoader` | Optional environmental `power`/`aoe` multipliers — the **Context** axis as data. Each entry is `{ "when": <condition>, "power": …, "aoe": … }`; multipliers default to 1.0 and matching entries stack multiplicatively. Conditions live in `ContextCondition` (`raining`, `thundering`, `day`, `night`, `underground`, `exposed_to_sky`, `hot`, `cold`); unknown names warn and are dropped. Evaluated in `MeaningEngine` against the source block (surface cast) or hand-cast origin; because the boost raises `power`, it also raises fuel cost through the §4.5 power→cost scaling. |

**Missing cells are not errors** (`MatrixLoader.java:30-32`): an unregistered
`(sigil, sign)` pair simply makes the engine return empty and the inscription falls
back to Prepared. That's what makes the matrix *sparse-by-design* — you wire only the
combinations you've authored, and everything else is harmlessly inert.

Two shipped examples:

```jsonc
// spell_matrix/earth/column.json  →  (EARTH, COLUMN)
{ "behavior_kind": "stone_pillar",
  "base": { "power": 1.0, "aoe": 1.0 },
  "effects": [ { "type": "spawn_blocks", "block": "minecraft:stone",
                 "shape": "vertical_column", "blocks_per_magnitude": 3 } ],
  "stacking_curve": "linear",
  "cost": { "per_tick": 25.0, "per_use": 300.0 } }

// spell_matrix/fire/levitation.json  →  (FIRE, LEVITATION)
{ "behavior_kind": "pyreball",
  "base": { "power": 1.0, "aoe": 1.0 },
  "effects": [ { "type": "unique_entity", "entity": "witchhatateliermod:pyreball",
                 "fallback_block": "minecraft:fire",
                 "special_effects": ["ignite", "levitate"] } ],
  "stacking_curve": "linear",
  "cost": { "per_tick": 1.0, "per_use": 0.0 } }
```

### Adding a spell

End to end, a new `(Sigil × Sign)` spell is:

1. (If the sigil/sign is new) register its **gesture template** and add the
   `SigilType`/`SignType` enum constant + its `fromSpellName` case.
2. Drop a `spell_matrix/<sigil>/<sign>.json` cell naming an existing or new
   `behavior_kind`.
3. (If the `behavior_kind` is new) implement an `EffectKind` and register it in
   `EffectRegistry.bootstrap()` (`EffectRegistry.java:34-40`).
4. (Optional) implement a `SignBehavior` for mechanics the matrix can't express and
   register it in `SignBehaviorRegistry.bootstrap()` (`SignBehaviorRegistry.java:33-38`).

Existing signs/sigils combine with the new one automatically — that's the payoff of
the matrix design.

---

## 9. Config reference (mechanics half)

All in `Config.java`; defaults are the shipped values.

| Key | Default | Role | Where it enters |
|---|---|---|---|
| `sigilStackPowerPerExtra` | 0.5 | Power bonus per duplicate element. | `MeaningEngine.java:127-129` |
| `sizePowerReference` | — | Drawn size that maps to ×1.0. | `SizeScaling.java:32` |
| `sizePowerMax` | — | Multiplier at full drawn size. | `SizeScaling.java:33` |
| `sizePowerExponent` | — | Curve shape each side of the anchor. | `SizeScaling.java:34` |
| `directionSizeExponent` | — | Extra weight size carries on steering. | `SizeScaling.java:51` |
| `costPowerScaling` | — | How strongly fuel cost tracks power. | `MeaningEngine.java:146` |
| `symmetryCancelDeadzone` | — | When opposing signs cancel to zero direction. | `SymmetryAnalyzer.java:56` |
| `defaultSpellFuel` | — | Starting fuel for a channel. | `SpellCastManager.java:84`, `PlacedPaperCastManager.java:72` |

(Per-cell `base.power/aoe`, `stacking_curve`, and `cost.*` live in the matrix JSON,
not the config — they're per-spell, not global.)

---

## 10. Reading order for the code

1. `SaveGestureHandler.java:338-434` — the orchestration: compile → evaluate →
   dispatch, in one method.
2. `SpellGraphBuilder.build` (`:38-95`) — names → structure, and the one rule.
3. `MeaningEngine.evaluate` (`:40-161`) — the matrix resolution and all the
   amplifier math, top to bottom.
4. `MatrixLoader` + an example cell — how a `(sigil, sign)` becomes data.
5. `EffectKind` (`:20-73`) + `PyreballEffect` — the behaviour contract and one full
   channeled implementation.
6. `SpellCastManager` — the channel lifecycle, fuel, and consumption end to end.
