package com.crsocial.witchhatatelier.spell.meaning;

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
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
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

    public static Optional<ExecutableSpell> evaluate(SpellGraph graph, CastingContext ctx) {
        SigilType element = graph.core().type();
        float quality = graph.core().quality();
        float size = graph.size().normalizedBboxArea();

        List<BehaviorOp> ops = new ArrayList<>();
        float maxPower = 0f;
        float maxAoe = 0f;
        long durationTicks = 0L;

        // ── One op per sign bundle that has a matrix cell ────────────────────────
        for (SignBundle bundle : graph.signsByType()) {
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

            float opPower = entry.basePower() * magnitudeScalar * quality * Math.max(0.1f, size);
            float opAoe = entry.baseAoe() * magnitudeScalar;

            ops.add(new BehaviorOp(bundle.type(), magnitudeCount,
                    entry.behaviorKind(), kind.get().parsePayload(entry.effects())));

            if (opPower > maxPower) maxPower = opPower;
            if (opAoe > maxAoe) maxAoe = opAoe;
            if (entry.baseDurationTicks() > durationTicks) durationTicks = entry.baseDurationTicks();
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
        float durationMul = 1.0f;
        float aoeMul = 1.0f;

        for (SignBundle bundle : graph.signsByType()) {
            Optional<SignBehavior> custom = SignBehaviorRegistry.get().find(bundle.type());
            if (custom.isEmpty()) continue; // no custom impl — matrix system handles it fully

            SpellModification mod = custom.get().modify(element, bundle, graph, ctx, magnitude);
            if (mod == SpellModification.NONE) continue;
            originOffset.add(mod.originOffset());
            directionBias.add(mod.directionBias());
            powerMul *= mod.powerMultiplier();
            durationMul *= mod.durationMultiplier();
            aoeMul *= mod.aoeMultiplier();
        }

        // Apply accumulated modifications
        Magnitude finalMagnitude = new Magnitude(
                maxPower * powerMul,
                maxAoe * aoeMul,
                quality,
                size);
        long finalDuration = Math.max(1L, (long) (durationTicks * durationMul));

        Vector3f finalDirection = new Vector3f(direction).add(directionBias);
        if (finalDirection.lengthSquared() > 1e-6f) finalDirection.normalize();

        Vector3f originWorld = new Vector3f(ctx.originWorld()).add(originOffset);
        Vector3f surfaceNormal = new Vector3f(ctx.surfaceNormal());

        return Optional.of(new ExecutableSpell(
                element, List.copyOf(ops), finalMagnitude, origin,
                originWorld, surfaceNormal, finalDirection, finalDuration, null));
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
        return new Vector3f(net.x, normal.y, net.y).normalize();
    }
}
