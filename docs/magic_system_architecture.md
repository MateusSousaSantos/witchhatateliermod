# Magic System Architecture Specification

## 1. System Overview
This document outlines the architecture for a modular, visually striking, sigil-based magic system built for NeoForge 1.21.1. The system relies on player-drawn gestures interpreted via the $P Point-Cloud algorithm, compiling distinct elements (Sigils, Signs, and Rings) into contextual, meaning-based spells.

---

## 2. Core Modules

### 2.1. Inscription & Input Layer (The Canvas)
Handles the capturing of player interactions and translating them into raw stroke data.
* **Input Capture:** Records 2D coordinates of player strokes, capturing temporal data and stroke grouping.
* **$P Point-Cloud Recognizer:** Compares drawn strokes against a predefined dictionary of shapes (Sigils, Signs, Rings).
* **Quality Analyzer:** * *Size Metric:* Calculates the bounding box of the entire inscription. Maps directly to **Spell Power/Area of Effect**.
    * *Neatness Metric:* Calculates the deviation of the drawn stroke from the ideal template. Maps directly to **Spell Duration/Stability**.

### 2.2. Spell Compiler (The Graph Builder)
Translates recognized shapes into a structural representation of a spell.
* **Ring Topology Detection:** Scans for enclosing rings. A closed ring signals the compiler to attempt compilation.
* **Nesting Logic:** Supports multi-layered spells by detecting concentric rings. Outer rings wrap inner rings, combining their ASTs (Abstract Spell Trees).
* **Symmetry Analyzer:** Detects bilateral or radial symmetry in the placement of Signs around the central Sigil. Symmetrical placements yield stable spells; asymmetrical placements yield valid but chaotic/unstable variants.
* **AST Generation:** Outputs a `SpellGraph` object containing:
    * `RootNode`: The enclosing Ring.
    * `CoreNode`: The central Sigil (Element).
    * `ModifierNodes`: Array of surrounding Signs (Form/Context).

### 2.3. Contextual Meaning Engine (The Brain)
Evaluates the `SpellGraph` to determine the actual mechanics of the spell. Instead of hardcoded spell IDs, it uses a meaning-based matrix.
* **Element Resolution:** The central Sigil (Earth, Air, Water, Fire, Light) determines the *Target Domain* (what the spell manipulates).
* **Contextual Modifiers:** Signs define the *Behavior*.
    * *Example:* `Breaking Sign` + `Earth Sigil` = Destroys blocks.
    * *Example:* `Breaking Sign` + `Water Sigil` = Repels/cleaves water entities.
* **Output:** Generates an `ExecutableSpell` object containing the mechanical payload (damage, block updates, entity manipulation).

### 2.4. Casting Medium State Machine (The Anchor)
Manages *where* and *how* the spell is stored or cast.
* **Medium Contexts:**
    * `InscribedBlock(For future updates not yet implemented)`: The spell is physically carved into the world. Triggers on proximity or redstone.
    * `PlacedPaper`: A portable, placeable spell. Can be consumed on use.
    * `PaperItem`: Cast dynamically in the air. Triggers immediately upon ring closure.
* **Preparation State:** Allows spells with an open (unclosed) ring to be stored in NBT data. Closing the ring transitions the state to `Active`.

### 2.5. Decoupled Visual & Rendering Engine (The Beauty)
Ensures visual splendor without hard-coupling to the mechanical logic. 
* **Visual Manifest:** When the `SpellGraph` is built, it emits a `VisualManifest` containing metadata (Element tags, Size, Symmetry index, Chaos factor).
* **Client-Side Rendering Registry:** Listens for the `VisualManifest` and constructs rendering layers:
    * *Layer 1 (The Base):* The glowing outline of the drawn strokes, colored by element (e.g., fiery orange for Fire).
    * *Layer 2 (The Geometry):* Procedurally generated 3D custom models (e.g., rotating runic bands, floating geometric shards) that scale with the spell's Size metric.
    * *Layer 3 (The VFX):* Particle emitters driven by the Signs (e.g., `Dispersion` causes outward particle bursts; `Column` creates concentrated beam VFX).

---

## 3. Data Flow Pipeline (Execution Cycle)

1.  **Draw:** Player draws strokes (Block, Paper, or Air).
2.  **Recognize:** $P algorithm categorizes strokes into shapes.
3.  **Validate:** System detects a closed Ring.
4.  **Compile:** Shapes inside the Ring are parsed into a `SpellGraph`.
5.  **Evaluate:** Meaning Engine translates `SpellGraph` into an `ExecutableSpell`.
6.  **Manifest (Client):** `VisualManifest` is broadcasted; Client renders customized layered models and particle effects.
7.  **Execute (Server):** `ExecutableSpell` applies mechanical effects to the world based on the Casting Medium.

---

## 4. Extensibility & Modularity
Because the system is graph-based and contextual, adding new content is frictionless:
* **Adding a new Element:** Register a new Sigil shape in the $P recognizer and add its domain to the Meaning Engine. All existing Signs automatically work with it.
* **Adding a new Form:** Register a new Sign shape and define its verb/action in the Meaning Engine matrix.
* **Adding a new Visual:** Map a new custom model to a specific tag combination in the Client Registry without touching the server-side compiler logic.
