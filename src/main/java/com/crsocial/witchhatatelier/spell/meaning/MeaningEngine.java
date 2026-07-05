package com.crsocial.witchhatatelier.spell.meaning;

import com.crsocial.witchhatatelier.Config;
import com.crsocial.witchhatatelier.WitchHatAtelierMod;
import com.crsocial.witchhatatelier.spell.compiler.CastingContext;
import com.crsocial.witchhatatelier.spell.compiler.SigilType;
import com.crsocial.witchhatatelier.spell.compiler.SignBundle;
import com.crsocial.witchhatatelier.spell.compiler.SignType;
import com.crsocial.witchhatatelier.spell.compiler.SpellGraph;
import com.crsocial.witchhatatelier.spell.meaning.effect.EffectKind;
import com.crsocial.witchhatatelier.spell.meaning.effect.EffectRegistry;
import com.crsocial.witchhatatelier.spell.meaning.sign.SignBehavior;
import com.crsocial.witchhatatelier.spell.meaning.sign.SignBehaviorRegistry;
import com.crsocial.witchhatatelier.spell.meaning.sign.SpellModification;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Evaluates a {@link SpellGraph} against the loaded spell matrix and produces
 * an {@link ExecutableSpell}.
 *
 * <p>Phase 2 scope (see {@code docs/magic_system/implementation_roadmap.md}):
 * walk each sign bundle, look up its {@code (sigil, sign)} cell, build one
 * {@link BehaviorOp} per matched cell, and aggregate magnitude. A graph with
 * no matched cells returns empty and the inscription falls back to
 * Prepared.</p>
 *
 * <p>Multi-sign carrier/rider resolution, Force-sign re-shaping, and Meta-sign
 * layering land in Phase 5 — they are no-ops here because the matrix only has
 * one cell. The skeleton already iterates every bundle so adding cells later
 * is purely a data change.</p>
 */
public final class MeaningEngine {

    private MeaningEngine() {}

    public static Optional<ExecutableSpell> evaluate(SpellGraph graph, CastingContext ctx,
                                                     @Nullable ServerLevel level) {
        SigilType element = graph.core().type();
        float quality = graph.core().quality();
        float size = graph.size().normalizedBboxArea();

        // Where to sample the world for context_modifiers: the source block for a surface
        // cast, else the hand-cast origin in front of the caster. Null level (e.g. a
        // non-world caller) simply leaves every context multiplier at 1.0.
        Vector3f o = ctx.originWorld();
        BlockPos ctxPos = ctx.sourceBlock() != null
                ? ctx.sourceBlock()
                : BlockPos.containing(o.x, o.y, o.z);

        List<BehaviorOp> ops = new ArrayList<>();
        float maxPower = 0f;
        float dominantBasePower = 0f; // basePower of the op that set maxPower — the cost reference
        float maxAoe = 0f;
        float totalCostPerTick = 0f;
        float totalCostPerUse = 0f;

        // ── Collect Force-tier signs as modifiers, not ops ───────────────────────
        // A Force sign (CRUSH, …) produces no op of its own — it retargets the
        // carrier ops (Column, Dispersion). We gather the Force bundles here and
        // attach the same modifier map to every carrier op below, so the effect
        // kind can branch (e.g. a pillar carving instead of placing under CRUSH).
        // With no carrier, a Force sign alone yields no ops → Prepared (see below).
        Map<SignType, Integer> forceModifiers = new EnumMap<>(SignType.class);
        for (SignBundle bundle : graph.signsByType()) {
            if (bundle.type().tier() == SignType.Tier.FORCE) {
                forceModifiers.merge(bundle.type(), bundle.count(), Integer::sum);
            }
        }

        // ── One op per sign bundle that has a matrix cell ────────────────────────
        for (SignBundle bundle : graph.signsByType()) {
            if (bundle.type().tier() == SignType.Tier.FORCE) continue; // modifier, handled above
            Optional<MatrixEntry> cell = MatrixRegistry.get().find(element, bundle.type());
            if (cell.isEmpty()) {
                WitchHatAtelierMod.LOGGER.info(
                        "[MeaningEngine] No matrix entry for ({}, {}); skipping bundle.",
                        element, bundle.type());
                continue;
            }
            MatrixEntry entry = cell.get();

            Optional<EffectKind> kind = EffectRegistry.get().find(entry.behaviorKind());
            if (kind.isEmpty()) {
                WitchHatAtelierMod.LOGGER.warn(
                        "[MeaningEngine] Matrix cell ({}, {}) references unknown behavior_kind '{}'; skipping.",
                        element, bundle.type(), entry.behaviorKind());
                continue;
            }

            int magnitudeCount = bundle.count();
            float magnitudeScalar = bundle.type().stackingMode() == SignType.StackingMode.MAGNITUDE
                    ? entry.stackingCurve().apply(magnitudeCount) : 1.0f;

            // Context axis: fold in any environmental multipliers whose condition holds now.
            float[] ctxMul = contextMultipliers(entry, level, ctxPos, element, bundle.type());
            float opPower = entry.basePower() * magnitudeScalar * quality
                    * SizeScaling.powerMultiplier(size) * ctxMul[0];
            float opAoe = entry.baseAoe() * magnitudeScalar * ctxMul[1];

            ops.add(new BehaviorOp(bundle.type(), magnitudeCount,
                    entry.behaviorKind(), kind.get().parsePayload(entry.effects()),
                    entry.costPerTick(), entry.costPerUse(), forceModifiers));

            if (opPower > maxPower) {
                maxPower = opPower;
                dominantBasePower = entry.basePower();
            }
            if (opAoe > maxAoe) maxAoe = opAoe;
            totalCostPerTick += entry.costPerTick();
            totalCostPerUse += entry.costPerUse();
        }

        // ── Sigil default behaviour ──────────────────────────────────────────────
        // No sign produced an op (none drawn, or none with a matrix cell). Fall back
        // to the sigil's <sigil>/default.json cell — a sign-less BehaviorOp with no
        // stacking. This is what fires for a bare sigil drawn inside a ring.
        if (ops.isEmpty()) {
            Optional<MatrixEntry> def = MatrixRegistry.get().findDefault(element);
            if (def.isPresent()) {
                MatrixEntry entry = def.get();
                Optional<EffectKind> kind = EffectRegistry.get().find(entry.behaviorKind());
                if (kind.isPresent()) {
                    float[] ctxMul = contextMultipliers(entry, level, ctxPos, element, null);
                    float opPower = entry.basePower() * quality * SizeScaling.powerMultiplier(size) * ctxMul[0];
                    ops.add(new BehaviorOp(null, 1, entry.behaviorKind(),
                            kind.get().parsePayload(entry.effects()),
                            entry.costPerTick(), entry.costPerUse(), forceModifiers));
                    maxPower = opPower;
                    dominantBasePower = entry.basePower();
                    maxAoe = entry.baseAoe() * ctxMul[1];
                    totalCostPerTick += entry.costPerTick();
                    totalCostPerUse += entry.costPerUse();
                    WitchHatAtelierMod.LOGGER.info(
                            "[MeaningEngine] No sign cell for sigil={}; using default behaviour '{}'.",
                            element, entry.behaviorKind());
                } else {
                    WitchHatAtelierMod.LOGGER.warn(
                            "[MeaningEngine] Default cell for {} references unknown behavior_kind '{}'; skipping.",
                            element, entry.behaviorKind());
                }
            }
        }

        if (ops.isEmpty()) {
            WitchHatAtelierMod.LOGGER.info(
                    "[MeaningEngine] No matrix cells matched for sigil={} signs={} — falling back to Prepared.",
                    element, graph.signsByType().stream().map(b -> b.type().name()).toList());
            return Optional.empty();
        }

        Magnitude magnitude = new Magnitude(maxPower, maxAoe, quality, size);
        Origin origin = originFor(ctx.medium());
        Vector3f direction = resolveDirection(graph, ctx);

        // ── Apply per-sign custom behaviour modifications (only for signs that have one) ──
        Vector3f originOffset = new Vector3f();
        Vector3f directionBias = new Vector3f();
        float powerMul = 1.0f;
        float aoeMul = 1.0f;

        // Sign-placement behaviours (Levitation origin shift, Column direction skew)
        // are surface-cast features. Hand casts (PAPER_ITEM) re-aim every tick to the
        // caster's crosshair, so a fixed canvas-derived offset/skew fights the aim —
        // skip them entirely for hand casts until that's designed properly.
        boolean applySignBehaviors = ctx.medium() != CastingContext.MediumKind.PAPER_ITEM;

        for (SignBundle bundle : graph.signsByType()) {
            if (!applySignBehaviors) break;
            Optional<SignBehavior> custom = SignBehaviorRegistry.get().find(bundle.type());
            if (custom.isEmpty()) continue; // no custom impl — matrix system handles it fully

            SpellModification mod = custom.get().modify(element, bundle, graph, ctx, magnitude);
            if (mod == SpellModification.NONE) continue;
            originOffset.add(mod.originOffset());
            directionBias.add(mod.directionBias());
            powerMul *= mod.powerMultiplier();
            aoeMul *= mod.aoeMultiplier();
        }

        // Repeated copies of the same element amplify power: ×(1 + (stack-1)*perExtra).
        float sigilStackMul = 1.0f + (graph.sigilStack() - 1)
                * Config.SIGIL_STACK_POWER_PER_EXTRA.get().floatValue();

        // Apply accumulated modifications
        float finalPower = maxPower * powerMul * sigilStackMul;
        Magnitude finalMagnitude = new Magnitude(
                finalPower,
                maxAoe * aoeMul,
                quality,
                size);

        // ── Cost scales with power ───────────────────────────────────────────────
        // The matrix cost is the BASE: a spell drawn at its reference power pays it
        // unchanged. Amplifiers (quality, size, sign stacking, sign behaviours,
        // repeated sigils) raise power above that baseline, and cost rides along by
        // the same factor — scaled by COST_POWER_SCALING (0 = flat, 1 = 1:1 with
        // power, >1 = a steeper toll on heavy casts).
        float powerFactor = dominantBasePower > 1e-6f ? finalPower / dominantBasePower : 1f;
        float costScaling = Config.COST_POWER_SCALING.get().floatValue();
        float costMul = Math.max(0f, 1f + (powerFactor - 1f) * costScaling);
        totalCostPerTick *= costMul;
        totalCostPerUse *= costMul;

        Vector3f finalDirection = new Vector3f(direction).add(directionBias);
        if (finalDirection.lengthSquared() > 1e-6f) finalDirection.normalize();

        Vector3f originWorld = new Vector3f(ctx.originWorld()).add(originOffset);
        Vector3f surfaceNormal = new Vector3f(ctx.surfaceNormal());

        return Optional.of(new ExecutableSpell(
                element, List.copyOf(ops), finalMagnitude, origin,
                originWorld, surfaceNormal, finalDirection,
                totalCostPerTick, totalCostPerUse, null, ctx.sourceBlock()));
    }

    /**
     * Combined {@code {power, aoe}} multipliers from an entry's {@code context_modifiers}
     * whose condition holds at {@code pos}. Returns {@code {1, 1}} when the level is null
     * (non-world caller) or the cell has no modifiers, so callers can multiply
     * unconditionally. Matching modifiers stack multiplicatively.
     */
    private static float[] contextMultipliers(MatrixEntry entry, @Nullable ServerLevel level,
                                              BlockPos pos, SigilType element, @Nullable SignType sign) {
        float p = 1f, a = 1f;
        if (level != null) {
            for (ContextModifier cm : entry.contextModifiers()) {
                if (cm.condition().test(level, pos)) {
                    p *= cm.powerMultiplier();
                    a *= cm.aoeMultiplier();
                }
            }
        }
        if (p != 1f || a != 1f) {
            WitchHatAtelierMod.LOGGER.info(
                    "[MeaningEngine] Context modifiers for ({}, {}) at {} → power×{}, aoe×{}.",
                    element, sign, pos,
                    String.format(java.util.Locale.ROOT, "%.2f", p),
                    String.format(java.util.Locale.ROOT, "%.2f", a));
        }
        return new float[]{ p, a };
    }

    private static Origin originFor(CastingContext.MediumKind medium) {
        return switch (medium) {
            case PAPER_ITEM      -> Origin.HAND;
            case PLACED_PAPER    -> Origin.PAPER_SURFACE;
            case INSCRIBED_BLOCK -> Origin.INSCRIBED_BLOCK;
        };
    }

    /**
     * Net direction in world space. Phase 2 uses the canvas-space symmetry
     * vector projected against {@code surfaceNormal}; Phase 3 will replace this
     * with a proper basis derived from the placed-paper facing.
     */
    private static Vector3f resolveDirection(SpellGraph graph, CastingContext ctx) {
        var net = graph.symmetry().netDirection();
        if (net.lengthSquared() < 1e-6f) return new Vector3f();
        Vector3f normal = new Vector3f(ctx.surfaceNormal());
        if (normal.lengthSquared() < 1e-6f) normal.set(0f, 1f, 0f);
        // Rotate the canvas direction by the paper's in-plane rotation so it follows the
        // rendered drawing (matches ColumnSignBehavior); keep the surface-normal vertical term.
        var world = CanvasDirection.toWorldXZ(net.x, net.y, ctx.drawRotationDeg());
        return new Vector3f(world.x, normal.y, world.y).normalize();
    }
}
