# Sigils & Signs — concrete content for the spell engine

The companion doc `new_spell_engine.md` describes; this doc lists **what**. Nothing here
changes how the engine resolves a spell — every entry below is a registration
(`ElementRegistry`, `FormRegistry`, `EffectRegistry`, `OverrideRegistry`) you can add to,
remove from, or rebalance without touching `spell/composition/CompositionEngine.java`.

> Package: `spell/composition/` (`src/main/java/com/crsocial/witchhatatelier/spell/`)
> Registries: `material/ElementRegistry`, `material/ConvergenceRegistry`,
> `form/FormRegistry`, `effect/EffectRegistry`, `OverrideRegistry`
> Companion doc: `new_spell_engine.md` (the engine itself), `spell_pipeline.md` (how a
> `SpellGraph` gets built in the first place).

---

## Elements (`ElementRegistry`)

| Element | Base material | Converged material | Notes |
|---|---|---|---|
| EARTH | `minecraft:stone` (block) | same as base | |
| AIR | `minecraft:cloud` (particle) | same as base | blockless — no sensible "air block" to place |
| WATER | `minecraft:splash` (particle) | same as base | blockless — no sensible "water block" to place (that's a fluid, not a placed-block spell result) |
| FIRE | `minecraft:fire` (block) | `minecraft:magma_block` (block) | the one element with a wired convergence modifier |
| LIGHT | `minecraft:light` (block, light level 15) | same as base | |

## Convergence (`ConvergenceRegistry`)

Sparse — only **Fire** has a registered `MaterialModifier` today (`fire → magma_block`,
a flat swap to `Element.converged()`, not derived from `base`). Every other element's
convergence glyph compiles fine (`SpellGraph.convergence() == true`) but changes
nothing — a documented no-op, not a gap, until that element gets a modifier registered.

## Forms (`FormRegistry`)

| Form | Stacking | Generic default |
|---|---|---|
| COLUMN | MAGNITUDE | Extrudes the working material from the cast origin along the resolved direction. Blocky materials place a column of blocks (voxel-traversed, so a diagonal aim stays face-connected); blockless materials emit a one-shot particle trail instead. Height = `3 blocks × power × quality` (`ColumnForm.BLOCKS_PER_MAGNITUDE`). |
| DISPERSION | MAGNITUDE | Scatters the working material in a flat burst around the origin — `6 × power` positions within a `2.5 × aoe`-block radius disc. Blockless materials emit a particle burst of the same radius instead. |
| BOLT | REPETITION | One fast particle streak per drawn occurrence, fired along the resolved direction. A real projectile entity (collision, damage, a model/renderer) is reserved for a future bespoke `(BOLT, element)` override — not implemented today, the same documented-gap pattern used for `EXTINGUISH`'s missing template. |

## Effects (`EffectRegistry`)

| Effect | Stacking | canCarry | Mode | Generic default |
|---|---|---|---|---|
| LEVITATION | MAGNITUDE | yes | CONTINUOUS | Gives every entity within `3 × aoe` blocks of the origin an upward nudge (`0.6 × power`). |
| CRUSH | MAGNITUDE | no | CONTINUOUS | Un-places every block a `BlocksManifestation` just placed (works for any form's output, since the form already computed the right positions). No-op on a blockless `ParticlesManifestation`. |
| PULL | MAGNITUDE | yes | CONTINUOUS | Draws every entity within `5 × aoe` blocks toward the origin (`0.35 × power`). |
| COLLECTION | MODIFIER | no | CONTINUOUS | Its own draw-count never amplifies. Instead, `CompositionEngine`'s post-pass (`CollectionEffect.freeBonus`) scans a `4`-block cube around the origin for blocks matching the working material and adds up to `+1.5×` power/aoe, **excluded from cost** (§9's environment-drawn-amplification exemption) — the free-power worked example. |
| EXTINGUISH | MODIFIER | yes | REACTIVE | **No gesture template yet — undrawable in-game.** The reactive worked example: trigger = "is there fire within 3 blocks of the origin?", per-event action = remove it. `CompositionEngine` attaches the `Trigger` to the compiled `ExecutableSpell`; nothing evaluates it, since no reactive runtime exists yet. |

---

## Overrides (`OverrideRegistry`)

Deliberately sparse — two entries, enough to prove the opt-in mechanism (§7), not an
exhaustive per-element pass over every Form/Effect:

- **`(COLUMN, FIRE)` → `FireColumnOverride`** — reuses `ColumnForm`'s geometry verbatim
  (`super.manifest`), then scorches anything standing within 1 block of the column's
  line (4 seconds of ignite).
- **`(CRUSH, FIRE)` → `FireCrushOverride`** — reuses `CrushEffect`'s un-placement
  verbatim (`super.modify`), then a one-shot detonation along the column's line: a
  `1.75×`-wider burn radius, `9 × power`-scaled direct damage (clamped to `0.5–3.0×`),
  and an 8-second ignite. The "Crush inverts Column" framing from §6, made literal for
  Fire.

Every other `(Form, Element)`/`(Effect, Element)` pair falls straight through to the
generic default above — e.g. `EARTH + COLUMN` places a plain stone column, `WATER +
CRUSH` is a no-op (nothing blocky to un-place). Adding a new bespoke combination is a
`OverrideRegistry.registerForm`/`registerEffect` call plus a small implementation class
next to the existing `fire/` overrides — nothing in `CompositionEngine` changes.

---

## Adding a new sign

1. Register its gesture template (`data/witchhatateliermod/spell_templates/*.json`) and
   the `ElementType`/`FormType`/`EffectType` enum constant + `fromSpellName` case, per
   `spell_pipeline.md`'s "adding content" section.
2. Implement `Form`/`Effect` and register it in `FormRegistry`/`EffectRegistry` as the
   generic default — every type must have one (`docs/new_spell_engine.md` §7's "the
   default always exists").
3. Declare its `StackingMode` — does drawing it more make it stronger (`MAGNITUDE`),
   make it repeat/hit more targets (`REPETITION`), or just flag a behaviour
   (`MODIFIER`)?
4. Only if you want bespoke per-element behaviour: implement an override class and
   register it in `OverrideRegistry`. Skip this — the generic default already combines
   with every element for free.

No change to `CompositionEngine`, `docs/new_spell_engine.md`, or any other sign's
registration is ever required.
