package com.crsocial.witchhatatelier.spell.composition;

import com.crsocial.witchhatatelier.spell.compiler.CastingContext;
import net.minecraft.util.Mth;
import org.joml.Vector2f;
import org.joml.Vector3f;

/**
 * Resolves a {@code SpellGraph}'s canvas-space sign placement into a
 * world-space direction — the geometry-report half of {@code
 * docs/new_spell_engine.md} §4 ("sign placement/symmetry → direction").
 *
 * <p>A perfectly balanced placement ({@code radialScore == 1}, or a
 * degenerate zero net direction) resolves straight along the casting
 * surface's normal — the same "no lean" outcome the old symmetry deadzone
 * was built to make easy to draw on purpose. Otherwise the canvas-space net
 * direction is rotated into the surface's plane (via {@code
 * drawRotationDeg}, so a rotated placed-paper's drawing still leans the way
 * it looks) and blended in as a lean on top of the normal, proportional to
 * how imbalanced the placement is.</p>
 */
public final class CanvasDirection {

    /** How strongly a fully-imbalanced (radialScore = 0) placement leans off the surface normal. */
    private static final float MAX_LEAN = 0.6f;

    private CanvasDirection() {
    }

    public static Vector3f resolve(Vector2f netDirectionCanvas, float radialScore, CastingContext casting) {
        Vector3f normal = normalizedOrUp(casting.surfaceNormal());
        if (netDirectionCanvas.lengthSquared() < 1e-6f || radialScore >= 1f) {
            return normal;
        }

        Vector3f seed = Math.abs(normal.y) > 0.99f ? new Vector3f(1f, 0f, 0f) : new Vector3f(0f, 1f, 0f);
        Vector3f right = new Vector3f(seed).cross(normal).normalize();
        Vector3f up = new Vector3f(normal).cross(right).normalize();

        float rot = (float) Math.toRadians(casting.drawRotationDeg());
        float cos = Mth.cos(rot);
        float sin = Mth.sin(rot);
        float dx = netDirectionCanvas.x;
        float dy = -netDirectionCanvas.y; // canvas is y-down; world "up" along the surface is -y
        float rightAmount = dx * cos - dy * sin;
        float upAmount = dx * sin + dy * cos;

        Vector3f lean = new Vector3f(right).mul(rightAmount).add(new Vector3f(up).mul(upAmount));
        if (lean.lengthSquared() < 1e-6f) {
            return normal;
        }
        lean.normalize();

        float leanStrength = MAX_LEAN * (1f - clamp01(radialScore));
        Vector3f blended = new Vector3f(normal).add(lean.mul(leanStrength));
        return blended.lengthSquared() < 1e-6f ? normal : blended.normalize();
    }

    private static Vector3f normalizedOrUp(Vector3f normal) {
        if (normal == null || normal.lengthSquared() < 1e-6f) return new Vector3f(0f, 1f, 0f);
        return new Vector3f(normal).normalize();
    }

    private static float clamp01(float v) {
        return v < 0f ? 0f : Math.min(v, 1f);
    }
}
