package com.crsocial.witchhatatelier.spell.cast;

import net.minecraft.world.entity.player.Player;
import org.joml.Vector3f;

/**
 * Derives a hand cast's world-space origin and surface normal from the player's
 * <em>live</em> aim. Mirrors the one-shot math originally in
 * {@code SaveGestureHandler.buildCastingContext} so the in-world channel can
 * re-aim the spell every tick from a single source of truth.
 */
public final class CastOrigin {

    /** Distance in front of the eyes the spell emerges from. */
    public static final float FORWARD_OFFSET = 1.5f;

    private CastOrigin() {}

    public static OriginPair fromLiveAim(Player player) {
        var look = player.getLookAngle();
        Vector3f normal = new Vector3f((float) look.x, (float) look.y, (float) look.z);
        if (normal.lengthSquared() < 1e-6f) {
            normal.set(0f, 1f, 0f);
        } else {
            normal.normalize();
        }
        Vector3f origin = new Vector3f(
                (float) player.getX(), (float) player.getEyeY(), (float) player.getZ())
                .add(new Vector3f(normal).mul(FORWARD_OFFSET));
        return new OriginPair(origin, normal);
    }

    public record OriginPair(Vector3f originWorld, Vector3f surfaceNormal) {}
}
