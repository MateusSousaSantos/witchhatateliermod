package com.crsocial.witchhatatelier.spell.meaning.sign;

import com.crsocial.witchhatatelier.spell.compiler.CastingContext;
import com.crsocial.witchhatatelier.spell.compiler.SigilType;
import com.crsocial.witchhatatelier.spell.compiler.SignBundle;
import com.crsocial.witchhatatelier.spell.compiler.SpellGraph;
import com.crsocial.witchhatatelier.spell.meaning.Magnitude;
import org.joml.Vector3f;

/**
 * Custom behaviour for the Levitation sign. Levitation shifts the spell's
 * origin point, but HOW it shifts varies per element:
 *
 * <ul>
 *   <li><b>Fire</b> — origin rises upward (heat rises). Size controls height.</li>
 *   <li><b>Air</b> — origin rises highest + strong lateral drift (wind carries).
 *       Direction from sign placement determines drift axis.</li>
 *   <li><b>Water</b> — origin drifts laterally (flowing water). Direction determines
 *       flow axis; size controls drift distance.</li>
 *   <li><b>Earth</b> — origin sinks downward (weight resists levitation). Multiple
 *       signs slowly overcome resistance.</li>
 *   <li><b>Light</b> — origin projects forward along the sign direction (light
 *       travels in straight lines). Size extends projection distance.</li>
 * </ul>
 *
 * <p>In all cases, the sign's size ({@link Magnitude#sizeNormalized()}) and
 * direction ({@link SpellGraph#symmetry()}'s net vector) control the effect.
 * The sign count amplifies it.</p>
 */
public final class LevitationSignBehavior implements SignBehavior {

    // ── Per-element tuning constants ─────────────────────────────────────────

    private static final float FIRE_MAX_RISE = 4.0f;
    private static final float AIR_MAX_RISE = 6.0f;
    private static final float AIR_DRIFT_SCALE = 2.0f;
    private static final float WATER_MAX_DRIFT = 3.0f;
    private static final float EARTH_MAX_SINK = 2.0f;
    private static final float LIGHT_MAX_PROJECT = 5.0f;

    @Override
    public SpellModification modify(SigilType sigil, SignBundle bundle,
                                    SpellGraph graph, CastingContext ctx,
                                    Magnitude magnitude) {
        return switch (sigil) {
            case FIRE  -> fire(bundle, graph, magnitude);
            case AIR   -> air(bundle, graph, magnitude);
            case WATER -> water(bundle, graph, magnitude);
            case EARTH -> earth(bundle, magnitude);
            case LIGHT -> light(bundle, graph, ctx, magnitude);
        };
    }

    // ── Fire: heat rises ─────────────────────────────────────────────────────

    private SpellModification fire(SignBundle bundle, SpellGraph graph, Magnitude magnitude) {
        float sizeInfluence = magnitude.sizeNormalized();
        float countFactor = Math.min(bundle.count(), 3) / 3.0f;
        float rise = FIRE_MAX_RISE * sizeInfluence * (0.5f + 0.5f * countFactor);

        // Fire drifts slightly toward the sign cluster direction
        var net = graph.symmetry().netDirection();
        Vector3f dirBias = new Vector3f(net.x * 0.3f, 0.2f, net.y * 0.3f);

        return SpellModification.builder()
                .originOffset(0f, rise, 0f)
                .directionBias(dirBias)
                .build();
    }

    // ── Air: wind lifts high and carries far ─────────────────────────────────

    private SpellModification air(SignBundle bundle, SpellGraph graph, Magnitude magnitude) {
        float sizeInfluence = magnitude.sizeNormalized();
        float countFactor = Math.min(bundle.count(), 3) / 3.0f;
        float rise = AIR_MAX_RISE * sizeInfluence * (0.4f + 0.6f * countFactor);

        var net = graph.symmetry().netDirection();
        Vector3f dirBias = new Vector3f(
                net.x * AIR_DRIFT_SCALE,
                0.1f,
                net.y * AIR_DRIFT_SCALE);

        return SpellModification.builder()
                .originOffset(0f, rise, 0f)
                .directionBias(dirBias)
                .durationMultiplier(0.85f) // air dissipates faster
                .build();
    }

    // ── Water: flows laterally ───────────────────────────────────────────────

    private SpellModification water(SignBundle bundle, SpellGraph graph, Magnitude magnitude) {
        float sizeInfluence = magnitude.sizeNormalized();
        float drift = WATER_MAX_DRIFT * sizeInfluence;

        var net = graph.symmetry().netDirection();
        float len = net.length();
        Vector3f offset;
        if (len > 0.01f) {
            offset = new Vector3f(net.x / len * drift, 0.2f, net.y / len * drift);
        } else {
            // Balanced signs — spread equally
            offset = new Vector3f(drift * 0.5f, 0.1f, drift * 0.5f);
        }

        return SpellModification.builder()
                .originOffset(offset)
                .build();
    }

    // ── Earth: resists lifting ───────────────────────────────────────────────

    private SpellModification earth(SignBundle bundle, Magnitude magnitude) {
        float resistance = EARTH_MAX_SINK * magnitude.sizeNormalized();
        // Each sign count slowly overcomes the resistance
        float countCounterweight = Math.min(bundle.count(), 4) * 0.2f;
        float netSink = resistance * Math.max(0.1f, 1.0f - countCounterweight);

        return SpellModification.builder()
                .originOffset(0f, -netSink, 0f)
                .powerMultiplier(1.1f) // weight adds impact
                .build();
    }

    // ── Light: projects forward ──────────────────────────────────────────────

    private SpellModification light(SignBundle bundle, SpellGraph graph,
                                    CastingContext ctx, Magnitude magnitude) {
        float sizeInfluence = magnitude.sizeNormalized();
        float distance = LIGHT_MAX_PROJECT * sizeInfluence;

        var net = graph.symmetry().netDirection();
        float len = net.length();
        Vector3f offset;
        if (len > 0.01f) {
            offset = new Vector3f(net.x / len * distance, 0f, net.y / len * distance);
        } else {
            // No directional bias — project along surface normal
            Vector3f normal = new Vector3f(ctx.surfaceNormal());
            if (normal.lengthSquared() < 1e-6f) normal.set(0f, 1f, 0f);
            offset = normal.normalize().mul(distance);
        }

        return SpellModification.builder()
                .originOffset(offset)
                .build();
    }
}

