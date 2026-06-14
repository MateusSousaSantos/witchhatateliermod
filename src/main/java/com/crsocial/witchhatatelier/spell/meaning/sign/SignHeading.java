package com.crsocial.witchhatatelier.spell.meaning.sign;

import com.crsocial.witchhatatelier.spell.compiler.CastingContext;
import com.crsocial.witchhatatelier.spell.compiler.SignBundle;
import com.crsocial.witchhatatelier.spell.compiler.SignNode;
import com.crsocial.witchhatatelier.spell.meaning.CanvasDirection;
import org.joml.Vector2f;

/**
 * Shared read of a direction-bearing sign's recovered <b>glyph heading</b> — the
 * intrinsic "which way the arrow points" direction of the drawn shape, as opposed to
 * {@link SignPlacement}'s "where it was drawn relative to the sigil". Both reads are
 * available to a {@link SignBehavior}; the convention is to prefer the glyph heading
 * (the player explicitly aimed the arrow) and fall back to placement when no heading
 * was recovered.
 *
 * <p>The heading is the confidence-weighted mean of the bundle's occurrence headings
 * (occurrences with no heading are skipped), re-normalized to a unit canvas-space
 * vector. {@link #worldDirectionXZ} maps it to world XZ through the same
 * paper-rotation-aware {@link CanvasDirection} mapping the rest of the engine uses.</p>
 *
 * @param canvasDir  unit heading in canvas {@code [0,1]} space; zero vector when undefined
 * @param confidence averaged recovery confidence over the contributing occurrences
 */
public record SignHeading(Vector2f canvasDir, float confidence) {

    private static final float EPSILON = 1e-6f;

    /** No usable heading on this bundle — callers fall back to placement. */
    public static final SignHeading NONE = new SignHeading(new Vector2f(0f, 0f), 0f);

    /** Reads the confidence-weighted mean glyph heading from a sign bundle. */
    public static SignHeading from(SignBundle bundle) {
        Vector2f sum = new Vector2f();
        float confSum = 0f;
        int n = 0;
        for (SignNode occ : bundle.occurrences()) {
            if (!occ.hasHeading()) continue;
            float c = occ.headingConfidence();
            sum.add(occ.heading().x * c, occ.heading().y * c);
            confSum += c;
            n++;
        }
        if (n == 0 || sum.lengthSquared() < EPSILON) return NONE;
        sum.normalize();
        return new SignHeading(sum, confSum / n);
    }

    /** True when a usable glyph heading exists. */
    public boolean hasDirection() {
        return canvasDir.lengthSquared() > EPSILON;
    }

    /**
     * Unit heading rotated by the paper's in-plane rotation, mapped to world XZ
     * ({@code worldX, worldZ}); the zero vector when {@link #hasDirection()} is false.
     */
    public Vector2f worldDirectionXZ(CastingContext ctx) {
        if (!hasDirection()) return new Vector2f();
        return CanvasDirection.toWorldXZ(canvasDir.x, canvasDir.y, ctx.drawRotationDeg());
    }
}
