package com.crsocial.witchhatatelier.spell.meaning.sign;

import com.crsocial.witchhatatelier.spell.compiler.CastingContext;
import com.crsocial.witchhatatelier.spell.compiler.SignBundle;
import com.crsocial.witchhatatelier.spell.compiler.SignNode;
import com.crsocial.witchhatatelier.spell.compiler.SpellGraph;
import com.crsocial.witchhatatelier.spell.meaning.CanvasDirection;
import org.joml.Vector2f;

/**
 * Universal placement read for direction-sensitive signs.
 *
 * <p>Both Column (direction skew) and Levitation (origin shift) — and any future sign that steers
 * by where it was drawn — share the same three reads: the mean of the sign's occurrence positions
 * relative to the sigil centre (canvas-space displacement), the averaged recognizer confidence,
 * and the rotation of that displacement into world space so it follows the placed paper's in-plane
 * rotation. This record captures those shared primitives in one place; each sign keeps its own
 * <i>magnitude</i> curve.</p>
 *
 * @param displacement mean occurrence position − sigil centre, in canvas {@code [0,1]} space
 * @param distance     {@code |displacement|} (canvas units)
 * @param signQuality  averaged recognizer confidence over the sign's occurrences
 */
public record SignPlacement(Vector2f displacement, float distance, float signQuality) {

    /** Below this displacement length the placement direction is treated as undefined. */
    private static final float EPSILON = 1e-4f;

    /**
     * Reads the shared placement primitives from a sign bundle: mean occurrence position minus the
     * sigil centre, and the averaged recognizer quality.
     */
    public static SignPlacement from(SignBundle bundle, SpellGraph graph) {
        Vector2f meanPos = new Vector2f();
        float quality = 0f;
        for (SignNode occ : bundle.occurrences()) {
            meanPos.add(occ.position());
            quality += occ.quality();
        }
        int n = bundle.occurrences().size();
        if (n > 0) {
            meanPos.div(n);
            quality /= n;
        }
        Vector2f centre = graph.core().centre();
        Vector2f d = new Vector2f(meanPos.x - centre.x, meanPos.y - centre.y);
        return new SignPlacement(d, d.length(), quality);
    }

    /** True when the sign was drawn off the sigil centre (a usable direction exists). */
    public boolean hasDirection() {
        return distance > EPSILON;
    }

    /**
     * Unit placement direction rotated by the paper's in-plane rotation, mapped to world XZ
     * ({@code worldX, worldZ}); the zero vector when {@link #hasDirection()} is false.
     */
    public Vector2f worldDirectionXZ(CastingContext ctx) {
        if (!hasDirection()) return new Vector2f();
        float inv = 1f / distance;
        return CanvasDirection.toWorldXZ(displacement.x * inv, displacement.y * inv,
                                         ctx.drawRotationDeg());
    }
}
