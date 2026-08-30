package com.crsocial.witchhatatelier.spell.composition;

import com.crsocial.witchhatatelier.spell.compiler.EffectBundle;
import com.crsocial.witchhatatelier.spell.compiler.EffectType;
import com.crsocial.witchhatatelier.spell.compiler.ElementType;
import com.crsocial.witchhatatelier.spell.compiler.ExecutionMode;
import com.crsocial.witchhatatelier.spell.compiler.FormBundle;
import com.crsocial.witchhatatelier.spell.compiler.SpellGraph;
import com.crsocial.witchhatatelier.spell.composition.effect.CollectionEffect;
import com.crsocial.witchhatatelier.spell.composition.effect.Effect;
import com.crsocial.witchhatatelier.spell.composition.effect.EffectRegistry;
import com.crsocial.witchhatatelier.spell.composition.form.Form;
import com.crsocial.witchhatatelier.spell.composition.form.FormRegistry;
import com.crsocial.witchhatatelier.spell.composition.manifest.BlocksManifestation;
import com.crsocial.witchhatatelier.spell.composition.manifest.Manifestation;
import com.crsocial.witchhatatelier.spell.composition.manifest.ParticlesManifestation;
import com.crsocial.witchhatatelier.spell.composition.material.Element;
import com.crsocial.witchhatatelier.spell.composition.material.ElementRegistry;
import com.crsocial.witchhatatelier.spell.composition.material.ConvergenceRegistry;
import com.crsocial.witchhatatelier.spell.composition.material.Material;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Content-agnostic resolver that turns a {@link SpellGraph} into an {@link
 * ExecutableSpell} — implements the resolution algorithm in {@code
 * docs/new_spell_engine.md} §6 exactly. Knows nothing about what any specific
 * element/form/effect does; it only matches, counts, and composes via the
 * {@code Form}/{@code Effect} roles the registries hand back. See §12's
 * invariants — nothing here is a per-combination special case.
 */
public final class CompositionEngine {

    /** Reference cost at {@link Magnitude#REFERENCE} amplification. */
    private static final float BASE_COST = 1.0f;

    private CompositionEngine() {
    }

    public static Optional<ExecutableSpell> compose(SpellGraph graph, CastContext ctx) {
        ElementType elementType = graph.core().type();

        // ── Step 0: working material ─────────────────────────────────────────
        Element element = ElementRegistry.get(elementType);
        Material working = element.base();
        if (graph.convergence()) {
            Material base = working;
            working = ConvergenceRegistry.find(elementType)
                    .map(modifier -> modifier.apply(element, base))
                    .orElse(working);
        }

        Magnitude baseline = baselineMagnitude(graph);
        CastContext baseCtx = ctx.withMagnitude(baseline);

        List<FormBundle> formBundles = graph.formsByType();
        List<EffectBundle> effectBundles = graph.effectsByType();
        List<Manifestation> manifestations = new ArrayList<>();
        Set<EffectType> carriedTypes = new HashSet<>();

        // ── Step 1: base manifestation(s) ────────────────────────────────────
        if (!formBundles.isEmpty()) {
            for (FormBundle bundle : formBundles) {
                Form form = OverrideRegistry.findForm(bundle.type(), elementType)
                        .orElseGet(() -> FormRegistry.get(bundle.type()));
                CastContext opCtx = baseCtx.withMagnitude(applyStacking(baseline, form.stacking(), bundle.count()));
                manifestations.add(form.manifest(working, opCtx));
            }
        } else {
            List<EffectBundle> carriers = effectBundles.stream()
                    .filter(b -> resolveEffect(b.type(), elementType).canCarry())
                    .toList();
            if (!carriers.isEmpty()) {
                for (EffectBundle bundle : carriers) {
                    Effect effect = resolveEffect(bundle.type(), elementType);
                    CastContext opCtx = baseCtx.withMagnitude(applyStacking(baseline, effect.stacking(), bundle.count()));
                    manifestations.add(effect.carry(working, opCtx));
                    carriedTypes.add(bundle.type());
                }
            } else {
                bareElementDefault(working, baseCtx).ifPresent(manifestations::add);
            }
        }

        // ── Step 2: every non-carrying effect modifies every manifestation ──
        for (EffectBundle bundle : effectBundles) {
            if (carriedTypes.contains(bundle.type())) continue; // already carried in step 1
            Effect effect = resolveEffect(bundle.type(), elementType);
            CastContext opCtx = baseCtx.withMagnitude(applyStacking(baseline, effect.stacking(), bundle.count()));
            for (int i = 0; i < manifestations.size(); i++) {
                manifestations.set(i, effect.modify(manifestations.get(i), working, opCtx));
            }
        }

        // ── Step 3: Prepared safety net ──────────────────────────────────────
        if (manifestations.isEmpty()) {
            return Optional.empty();
        }

        // ── Step 4: post-passes ──────────────────────────────────────────────
        ExecutionMode mode = effectBundles.stream()
                .map(b -> resolveEffect(b.type(), elementType).modeTag())
                .anyMatch(m -> m == ExecutionMode.REACTIVE)
                ? ExecutionMode.REACTIVE : ExecutionMode.CONTINUOUS;

        // Cost is measured against the baseline BEFORE Collection's free bonus —
        // environment-drawn amplification is explicitly cost-exempt (§9).
        ExecutableSpell.Cost cost = computeCost(baseline, mode);

        Magnitude finalMagnitude = baseline;
        boolean hasCollection = effectBundles.stream().anyMatch(b -> b.type() == EffectType.COLLECTION);
        if (hasCollection) {
            Vec3 origin = new Vec3(ctx.origin().x, ctx.origin().y, ctx.origin().z);
            float bonus = CollectionEffect.freeBonus(ctx.level(), origin, working);
            finalMagnitude = finalMagnitude.scaledBy(1f + bonus);
        }

        Optional<Trigger> trigger = mode == ExecutionMode.REACTIVE
                ? effectBundles.stream()
                        .map(b -> resolveEffect(b.type(), elementType).trigger())
                        .filter(Optional::isPresent)
                        .map(Optional::get)
                        .findFirst()
                : Optional.empty();

        return Optional.of(new ExecutableSpell(elementType, working, List.copyOf(manifestations),
                finalMagnitude, ctx.origin(), ctx.direction(), mode, cost, trigger));
    }

    private static Effect resolveEffect(EffectType type, ElementType element) {
        return OverrideRegistry.findEffect(type, element).orElseGet(() -> EffectRegistry.get(type));
    }

    /**
     * The spell's shared baseline magnitude, computed once from the graph's
     * geometry reports: size → power/aoe, neatness (element quality) → duration
     * only, same-element repeats ({@code sigilStack}) → power (§9).
     */
    private static Magnitude baselineMagnitude(SpellGraph graph) {
        float sizeScalar = 0.5f + graph.size().normalizedBboxArea(); // reference ~1.0 at half-filled ring
        float stackingMultiplier = StackingCurve.multiplierFor(graph.sigilStack());
        float power = sizeScalar * stackingMultiplier;
        float aoe = sizeScalar * stackingMultiplier;
        float duration = 0.5f + graph.core().quality(); // neatness -> duration only, never size
        return new Magnitude(power, aoe, duration, graph.core().quality(), 1);
    }

    /** Folds one sign's own {@link StackingMode}-driven count into a per-op copy of the baseline (§5). */
    private static Magnitude applyStacking(Magnitude baseline, StackingMode mode, int count) {
        return switch (mode) {
            case MAGNITUDE -> baseline.scaledBy(StackingCurve.multiplierFor(count));
            case REPETITION -> baseline.withRepetitions(count);
            case MODIFIER -> baseline; // count never amplifies; a behavioural flag only
        };
    }

    /**
     * Branch 1c of the resolution algorithm: no form, no carrier-capable
     * effect → place one block / emit one particle burst of the working
     * material at the cast origin. The reason Step 3 (Prepared) is rare: a
     * lone element always lands here.
     */
    private static Optional<Manifestation> bareElementDefault(Material working, CastContext ctx) {
        Optional<Block> block = working.asBlock();
        if (block.isPresent()) {
            BlockPos pos = BlockPos.containing(ctx.origin().x, ctx.origin().y, ctx.origin().z);
            if (!ctx.level().isInWorldBounds(pos)) return Optional.empty();
            BlockState existing = ctx.level().getBlockState(pos);
            if (!(existing.isAir() || existing.canBeReplaced())) return Optional.empty();
            BlockState target = block.get().defaultBlockState();
            ctx.level().setBlock(pos, target, Block.UPDATE_ALL);
            return Optional.of(new BlocksManifestation(List.of(pos), target));
        }
        ParticleOptions particle = working.asParticle().orElse(null);
        if (particle == null) return Optional.empty();
        ctx.level().sendParticles(particle, ctx.origin().x, ctx.origin().y, ctx.origin().z, 12, 0.3, 0.3, 0.3, 0.02);
        return Optional.of(new ParticlesManifestation(ctx.origin(), ctx.direction(), particle, 0f));
    }

    /**
     * §9: cost scales with amplification from size/stacking, measured against
     * {@link Magnitude#REFERENCE} — superlinear, so doubling power more than
     * doubles cost. {@code per_tick} for a continuous spell, {@code per_event}
     * for a reactive one.
     */
    private static ExecutableSpell.Cost computeCost(Magnitude magnitude, ExecutionMode mode) {
        float amplification = (magnitude.power() / Magnitude.REFERENCE.power())
                * (magnitude.aoe() / Magnitude.REFERENCE.aoe());
        float scaled = BASE_COST * amplification * amplification;
        return mode == ExecutionMode.REACTIVE
                ? new ExecutableSpell.Cost(0f, scaled)
                : new ExecutableSpell.Cost(scaled, 0f);
    }
}
