package com.crsocial.witchhatatelier.spell.cast;

import com.crsocial.witchhatatelier.spell.meaning.ExecutableSpell;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

/**
 * Immutable per-tick view handed to an
 * {@link com.crsocial.witchhatatelier.spell.meaning.effect.EffectKind}'s
 * {@code begin}/{@code tick}/{@code end} hooks during a channeled cast.
 *
 * <p>{@link #spell()} is the spell recomputed for <em>this</em> tick — its
 * {@code originWorld}/{@code surfaceNormal}/{@code direction} reflect the
 * caster's live aim, so an entity effect can follow the crosshair.</p>
 *
 * <p>{@code level} and {@code caster} may be {@code null} during {@code end}
 * when the cast is torn down after the player has logged out.</p>
 */
public record CastContext(@Nullable ServerLevel level,
                          @Nullable ServerPlayer caster,
                          ExecutableSpell spell,
                          EffectScratch scratch,
                          SpellFuel fuel) {

    /** Fraction of the channel's fuel exhausted (consumed / capacity), in {@code [0,1]}. */
    public float progress() {
        return fuel != null ? fuel.progress() : 0f;
    }
}
