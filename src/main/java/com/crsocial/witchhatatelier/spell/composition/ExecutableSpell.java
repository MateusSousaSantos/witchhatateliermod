package com.crsocial.witchhatatelier.spell.composition;

import com.crsocial.witchhatatelier.spell.compiler.ElementType;
import com.crsocial.witchhatatelier.spell.compiler.ExecutionMode;
import com.crsocial.witchhatatelier.spell.composition.manifest.Manifestation;
import com.crsocial.witchhatatelier.spell.composition.material.Material;
import org.joml.Vector3f;

import java.util.List;
import java.util.Optional;

/**
 * {@link CompositionEngine}'s terminal artifact — a fully resolved spell,
 * ready for a not-yet-built executor to run (see {@code
 * docs/new_spell_engine.md} §6's result description and §2's pipeline
 * diagram). Everything on this record is already decided; nothing downstream
 * re-derives element, material, magnitude, or ops.
 *
 * @param element        the compiled spell's single element
 * @param workingMaterial the material every op manifested/modified against (post-convergence)
 * @param manifestations  every {@link Manifestation} produced by step 1/2 of the resolution
 *                        algorithm, in resolution order
 * @param magnitude       the spell's shared resolved magnitude (see {@link Magnitude})
 * @param origin          resolved world-space cast origin
 * @param direction       resolved world-space direction (see {@link CanvasDirection})
 * @param mode            {@link ExecutionMode#REACTIVE} if any present effect tagged it so,
 *                        else {@link ExecutionMode#CONTINUOUS} (§8)
 * @param cost            per-tick (continuous) or per-event (reactive) cost, amplification-scaled
 *                        and environment-bonus-exempt (§9)
 * @param trigger         present only when {@code mode == REACTIVE}; what a reactive runtime
 *                        would poll (§8) — unevaluated by this engine
 */
public record ExecutableSpell(ElementType element,
                              Material workingMaterial,
                              List<Manifestation> manifestations,
                              Magnitude magnitude,
                              Vector3f origin,
                              Vector3f direction,
                              ExecutionMode mode,
                              Cost cost,
                              Optional<Trigger> trigger) {

    /** @param perTick continuous-mode drain; {@code 0} for a reactive spell. @param perEvent reactive-mode charge; {@code 0} for a continuous spell. */
    public record Cost(float perTick, float perEvent) {
        public static final Cost NONE = new Cost(0f, 0f);
    }
}
