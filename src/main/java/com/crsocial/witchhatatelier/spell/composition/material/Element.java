package com.crsocial.witchhatatelier.spell.composition.material;

import com.crsocial.witchhatatelier.spell.compiler.ElementType;
import net.minecraft.sounds.SoundEvent;
import org.jetbrains.annotations.Nullable;

/**
 * One element's material palette — the source of every default manifestation
 * in the composition engine's fallback chain (see the resolution algorithm in
 * {@code docs/new_spell_engine.md} §6). {@code converged} is the densified/
 * upgraded material a registered {@link MaterialModifier} (see {@link
 * ConvergenceRegistry}) resolves {@code base} to; when an element has no
 * registered modifier, convergence leaves the working material unchanged and
 * {@code converged} is simply never read for that element — a valid,
 * documented outcome, not a gap.
 *
 * @param type          the element this palette belongs to
 * @param base          the element's ordinary working material
 * @param converged     the densified/upgraded material convergence resolves to
 * @param colorARGB     cosmetic tint used by particle/UI rendering, or {@code 0} for none
 * @param ambientSound  optional sound cue played by manifestations, or {@code null}
 * @param lightLevel    light emitted by block manifestations of this element, 0-15
 */
public record Element(ElementType type,
                      Material base,
                      Material converged,
                      int colorARGB,
                      @Nullable SoundEvent ambientSound,
                      int lightLevel) {
}
