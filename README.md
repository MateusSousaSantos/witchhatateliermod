# Witch Hat Atelier Mod

A **NeoForge 1.21.1** Minecraft mod that replaces "click an item, get a spell" with
a magic system where **you draw the spell**. You sketch gestures on a canvas; a
point-cloud recognizer reads your strokes into *sigils* and *signs*; a compiler and a
data-driven meaning engine turn that drawing into a real, server-authoritative effect
in the world. What a spell *does* is decided by **what you drew and how well**, not by
a fixed recipe.

> **Status:** in development (`mod_version 1.0.0`). The recognition, compilation,
> meaning, and casting pipeline is implemented end to end; the content roster (sigils,
> signs, and matrix cells) is still expanding.

---

## What playing it feels like

1. **Get a paper.** Spells are inscribed onto paper. There are 12 paper variants
   (small/medium/large × round/square, blank vs. inscribed).
2. **Draw.** Open the canvas and draw your spell: one **sigil** (the element — fire,
   earth, air, water, light) surrounded by optional **signs** (modifiers that shape
   how the element manifests — a column, a dispersion, a bolt, and so on).
3. **Close the ring.** Drawing a ring around your sigil is the trigger. Close it and
   the spell fires. Draw now and close later if you want to *prepare* a spell for
   later.
4. **It manifests.** A stone pillar erupts, a hovering fireball follows your crosshair,
   wind kicks up — depending on the `(sigil × sign)` combination, how large and how
   cleanly you drew, and whether you cast from the hand or from a placed paper.

The same drawing can mean different things depending on context: a paper held in hand
casts a **channeled, aim-following** spell that lasts until its fuel runs out; the same
inscription on a **placed paper** anchors to that surface instead.

---

## The core ideas

A handful of design rules hold the whole system together:

- **You draw the spell.** Recognition is a real gesture recognizer (a `$P+`
  point-cloud matcher), not a menu. Sloppy and clean drawings of the same shape are
  both recognized — but *how well* you drew it scales the result.
- **Meaning is a matrix, not a list.** A spell is `(Sigil) × (Signs) × (Context)`,
  resolved at cast time against data-driven cells. There is no hardcoded list of
  spells; add a sigil or a sign and it automatically combines with everything already
  in the game.
- **The ring is the trigger.** Closing the ring fires the spell. This makes "prepare
  now, release later" a natural, built-in state rather than a special case.
- **Quality is separate from recognition.** *What is this?* (recognition) and *how
  well was it drawn?* (quality) are independent — both feed the result, neither
  overrides the other.
- **The server is authoritative.** The client draws and previews; the server decides
  what actually happens. A single network message carries the drawing across.

---

## How a cast flows through the code

One stroke becomes one effect through a fixed pipeline:

```
client:  Draw → Recognize ($P+) → Validate (closed ring) ──[SaveGesturePayload]──► server
server:  cluster strokes → preprocess → recognize → SpellGraphBuilder
         → MeaningEngine → ExecutableSpell → SpellExecutor (one-shot)
                                           │              SpellCastManager (channeled hand cast)
                                           └────────────► PlacedPaperCastManager (sustained surface cast)
```

`SaveGestureHandler.runSpellPipeline()` is the server-side orchestrator that runs this
whole chain. Two deep-dive docs cover the two halves:

- **[`docs/recognizer.md`](docs/recognizer.md)** — everything up to recognition: the
  `$P+` point-cloud recognizer, preprocessing, the filter pipeline, the decision
  gates, and the offline tuning loop.
- **[`docs/spell_pipeline.md`](docs/spell_pipeline.md)** — everything after: the
  spell graph compiler, the `(Sigil × Sign)` meaning engine, and the instantaneous /
  channeled / sustained casting runtimes.

### Package map

| Package | Responsibility |
|---|---|
| `client/gesture/` | The canvas — stroke capture, smoothing, angle snapping. Strokes live in normalized `[0,1]` space. |
| `spell/cluster/` | Groups content strokes into candidate sigils. |
| `spell/recognition/` | The `$P+` recognizer, preprocessing, prefilters, chamfer scoring, template registry. |
| `spell/trigger/` | Ring-closure detection — separates ring strokes from content strokes. |
| `spell/compiler/` | `SpellGraphBuilder` → `SpellGraph` (core sigil + sign bundles + size + symmetry). |
| `spell/meaning/` | `MeaningEngine` resolves `(sigil, sign)` matrix cells into an `ExecutableSpell`; effect and sign-behaviour implementations. |
| `spell/cast/` | Channeled hand casts and sustained surface casts — `begin`/`tick`/`end` lifecycle, fuel budget, aim-following, medium consumption. |

---

## Data-driven content

Two kinds of content are **datapacks**, hot-reloadable with `/reload` — no rebuild
needed to iterate:

- **Gesture templates** — `data/witchhatateliermod/spell_templates/*.json`. Each names
  a spell and one or more point-sample variants of its shape.
- **Spell matrix cells** — `data/witchhatateliermod/spell_matrix/<sigil>/<sign>.json`.
  **The path is the key**: `.../earth/column.json` defines the `(EARTH, COLUMN)` spell.
  A cell declares its `behavior_kind`, base power/AoE, effect list, stacking curve, and
  fuel costs. Missing cells aren't errors — an unwired combination simply falls back to
  the *Prepared* state.

Adding a new spell is mostly authoring JSON: register a shape template, drop a matrix
cell, and (only if the combination needs new mechanics) add an `EffectKind`
implementation. Existing sigils and signs combine with anything new automatically.

---

## Build & run

Use the Gradle wrapper (`./gradlew` / `gradlew.bat`). ModDevGradle generates the run
configurations.

```bash
./gradlew build              # compile + assemble the mod jar
./gradlew runClient          # launch a dev client
./gradlew runServer          # launch a dev dedicated server
./gradlew runData            # run data generators → src/generated/resources/
./gradlew runGameTestServer  # run all registered gametests, then exit
```

There is no separate unit-test suite — tests are NeoForge **gametests** (namespace
`witchhatateliermod`), runnable via `runGameTestServer` or the in-game `/test` command.

`src/generated/resources/` is data-generator output on the main resource path; re-run
`runData` after touching the `datagen/` providers rather than hand-editing it.

---

## Debugging & tuning the recognizer

The recognizer is built to be tuned from real data:

- `F8` — debug template screen; `F9` — recognition debug screen.
- `/spell debug` — toggle verbose per-sigil recognition output in chat.
- `/spell label <word>` / `/spell label clear` — stamp a ground-truth label on every
  logged recognition (`garbage` is the negative class), for building a training corpus.
- Config flag `recognitionLoggingEnabled` writes one JSON record per recognized sigil
  to `run/logs/spell_recognition.jsonl`.

An offline Python toolchain (stdlib only) in [`scripts/`](scripts/README.md) replays
the recognizer's decision stage over a labeled corpus in milliseconds, so scoring
thresholds can be tuned without redrawing or rebuilding. See
[`scripts/README.md`](scripts/README.md).

---

## Tech stack

- **Loader:** NeoForge `21.1.228` on Minecraft `1.21.1` (Java 21).
- **Build:** ModDevGradle with Parchment mappings.
- **Runtime dependencies:** [GeckoLib](https://github.com/bernie-g/geckolib) (animated
  entity models) and PlayerAnimationLib (casting body animation).

| | |
|---|---|
| Mod id | `witchhatateliermod` |
| Base package | `com.crsocial.witchhatatelier` |
| Version | `1.0.0` |
| License | All Rights Reserved |

Tunable knobs (stroke smoothing, recognizer scoring, clustering radii, fuel, power and
cost scaling) live in `Config.java`, heavily commented with ranges and defaults.

---

## Documentation index

- [`docs/recognizer.md`](docs/recognizer.md) — the gesture recognizer, in depth.
- [`docs/spell_pipeline.md`](docs/spell_pipeline.md) — compilation, meaning, and casting.
- [`scripts/README.md`](scripts/README.md) — the offline recognizer-tuning workflow.

## License

WitchHatAtelierMod is licensed under the BSD 3-Clause License.

Copyright (c) 2026 Mateus de Sousa Santos.

See the LICENSE file for details.
