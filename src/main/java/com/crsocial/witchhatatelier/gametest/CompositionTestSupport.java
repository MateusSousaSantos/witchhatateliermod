package com.crsocial.witchhatatelier.gametest;

import com.crsocial.witchhatatelier.spell.compiler.CastingContext;
import com.crsocial.witchhatatelier.spell.compiler.ElementNode;
import com.crsocial.witchhatatelier.spell.compiler.ElementType;
import com.crsocial.witchhatatelier.spell.compiler.EffectNode;
import com.crsocial.witchhatatelier.spell.compiler.FormNode;
import com.crsocial.witchhatatelier.spell.compiler.RingNode;
import com.crsocial.witchhatatelier.spell.compiler.SizeReport;
import com.crsocial.witchhatatelier.spell.compiler.SpellGraph;
import com.crsocial.witchhatatelier.spell.compiler.SymmetryReport;
import com.crsocial.witchhatatelier.spell.composition.CanvasDirection;
import com.crsocial.witchhatatelier.spell.composition.CastContext;
import com.crsocial.witchhatatelier.spell.composition.Magnitude;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import org.joml.Vector2f;
import org.joml.Vector3f;

import java.util.List;
import java.util.Optional;

/**
 * Hand-builds {@link SpellGraph}/{@link CastContext} instances for the
 * Composition* gametests, bypassing the recognizer entirely — same pattern
 * {@code InscriptionGameTests} uses for {@code InscriptionSummary}.
 */
final class CompositionTestSupport {

    private CompositionTestSupport() {
    }

    /** A {@link CastContext} whose world origin is {@code relativeOrigin}, resolved via {@code helper}'s test structure. */
    static CastContext ctx(GameTestHelper helper, BlockPos relativeOrigin, Magnitude magnitude) {
        ServerLevel level = helper.getLevel();
        BlockPos abs = helper.absolutePos(relativeOrigin);
        Vector3f origin = new Vector3f(abs.getX() + 0.5f, abs.getY(), abs.getZ() + 0.5f);
        Vector3f normal = new Vector3f(0f, 1f, 0f);
        CastingContext casting = CastingContext.of(CastingContext.MediumKind.PAPER_ITEM, origin, normal, null, 0f);
        Vector3f direction = CanvasDirection.resolve(new Vector2f(0f, 0f), 1f, casting);
        return new CastContext(level, null, casting, magnitude, origin, direction);
    }

    static Magnitude magnitude(float power, float aoe, float duration, float quality, int repetitions) {
        return new Magnitude(power, aoe, duration, quality, repetitions);
    }

    /** A balanced, half-filled, well-drawn {@link SpellGraph} of one element plus the given forms/effects. */
    static SpellGraph graph(ElementType elementType, int sigilStack, boolean convergence,
                            List<FormNode> forms, List<EffectNode> effects) {
        RingNode ring = new RingNode(List.of(), 360f, 10f);
        ElementNode core = new ElementNode(elementType, new Vector2f(0f, 0f), 0.9f);
        SymmetryReport symmetry = new SymmetryReport(1f, 0f, new Vector2f(0f, 0f), true);
        SizeReport size = new SizeReport(100f, 0.5f);
        return new SpellGraph(ring, core, sigilStack, convergence, forms, effects, Optional.empty(), symmetry, size);
    }
}
