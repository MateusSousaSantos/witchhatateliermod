# Contributing to Witch Hat Atelier Mod

Thanks for wanting to work on this mod. This file covers how we expect contributions
to be made — the workflow, the standards, and a few hard rules. Read it before opening
a pull request.

---

## The two hard rules

These are not negotiable. A PR that breaks either will be closed regardless of code
quality.

### 1. No AI-generated art or images

**All assets and art in this mod are made by a dedicated artist.** That includes
textures, models, GUI art, icons, item/block sprites, particles' source art,
promotional images — anything visual.

- **AI-generated images are forbidden**, full stop. Do not submit them, do not use them
  as placeholders, do not commit them "just to test."
- If a feature you're building needs new art, you can try to make it or talk to someone who can or use a clearly-temporary, obviously-flat
  placeholder (e.g. a solid-color or missing-texture block) in your branch and note it
  in the PR so it never gets mistaken for final art.

### 2. AI-assisted code is fine — vibecoding is not

Using an AI assistant to write code is **allowed and welcome**. What's required is that
*you* understand, own, and have deliberately shaped every line you submit.

**This means:**

- You read the code the AI produced, understood *why* it works, and would be able to
  explain and defend it in review.
- It fits the existing architecture and patterns of this repo (see below) — not
  whatever generic shape the model defaulted to.
- You verified it: it compiles, it runs, and you actually tried the behavior.
- You edited it to match our conventions, naming, and the surrounding code's style.

**This does not mean** pasting a prompt's output, watching it compile, and opening a PR.
That's "vibecoding," and we don't accept it. If you can't explain what a piece of your
diff does and why it's there, it isn't ready.

The bar is the same one we'd hold for hand-written code. The tool you used to get there
is your business; the result being deliberate and understood is ours.

---

## Before you build something big

**Talk to the maintainers first** if you want to:

- make a **large architectural / system change**,
- add a **new feature** or subsystem,
- change a **boundary** (a network payload, a save format, a public registry shape),
- or alter one of the **design rules** the system is built on.

Open an issue or reach out *before* writing the code. A big PR that arrives unannounced
and cuts against the design direction is painful for everyone — it's far better to
agree on the approach first. Small, self-contained fixes and improvements don't need
this; just open the PR.

---

## Follow the existing patterns

This codebase has deliberate, documented structure. New code should look like it
belongs. Read these first:

- **[`README.md`](README.md)** — what the mod is and how a cast flows end to end.
- **[`docs/recognizer.md`](docs/recognizer.md)** — the gesture recognizer.
- **[`docs/spell_pipeline.md`](docs/spell_pipeline.md)** — compilation, meaning, and casting.

A few patterns that matter in practice:

- **Meaning is a matrix, not a list.** A spell is `(Sigil) × (Signs) × (Context)`
  resolved against data-driven cells. Don't hardcode a spell; add a matrix cell. Adding
  a sigil/sign should auto-combine with everything already registered.
- **Mechanics and visuals are decoupled.** The server reads the `ExecutableSpell`; the
  client reads the `SpellGraph`/visual manifest. Don't make one read the other.
- **The server is authoritative.** The single client→server crossing is
  `SaveGesturePayload`. Adding a boundary-crossing step means registering a new payload
  at the one intended edit site — not a new ad-hoc packet scattered elsewhere.
- **Prefer data over code.** New content (sigils, signs, spells) is usually JSON in
  `data/witchhatateliermod/spell_templates/` and `spell_matrix/`, hot-reloadable with
  `/reload`. Reach for a new `EffectKind` only when the behavior genuinely can't be
  expressed as data.
- **Match the local style.** Coordinates are normalized `[0,1]` canvas space until the
  meaning engine's world-space outputs. Logging goes through
  `WitchHatAtelierMod.LOGGER` with bracketed stage tags (`[SpellPipeline]`,
  `[MeaningEngine]`, …). New code should read like the code around it — same comment
  density, naming, and idioms.
- **Tunables go in `Config.java`**, commented with their range and default, not as magic
  numbers buried in logic.

If you're changing recognizer behavior, note that some tuning is done **offline** from
logged data — see [`scripts/README.md`](scripts/README.md). Don't eyeball threshold
changes; tune them against the corpus.

---

## Workflow

1. **Branch** off `main` (don't commit directly to `main`).
2. **Build and run** before you push:
   ```bash
   ./gradlew build              # compile + assemble
   ./gradlew runClient          # try it in a dev client
   ./gradlew runGameTestServer  # run the gametests
   ```
   If you touched `datagen/` providers, re-run `./gradlew runData` (don't hand-edit
   `src/generated/resources/`).
3. **Verify the behavior in-game**, not just that it compiles. A spell change means
   actually casting the spell.
4. **Keep the diff focused.** One logical change per PR. Don't mix a refactor into a
   feature, and don't reformat unrelated files.
5. **Write a clear PR description**: what changed, why, and how you tested it. If you
   used an AI assistant, that's fine — just make sure the description reflects *your*
   understanding of the change.

### Tests

There's no separate unit-test suite — tests are NeoForge **gametests** (namespace
`witchhatateliermod`), run via `./gradlew runGameTestServer` or the in-game `/test`
command. If you add or change mechanical behavior, add or update a gametest where it's
practical.

---

## Commits & PRs

- Write commit messages that explain *why*, not just *what*.
- Rebase/clean up obvious "wip" / "fix typo" noise before requesting review.
- Be responsive in review — this is a small project and review is a conversation, not a
  gate.

---

## Questions

If you're unsure whether something fits the direction of the mod, **ask before you
build**. A two-line message to a maintainer saves a two-hundred-line PR that has to be
rewritten. We'd rather help you land the right thing than reject the wrong one.


## Contact

You can send me a message on Discord at `critica_social` or open an issue in this repo if you have questions about contributing. Thanks for your interest!