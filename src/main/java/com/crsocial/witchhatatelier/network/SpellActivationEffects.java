package com.crsocial.witchhatatelier.network;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/** Server-side sound and particle burst played whenever a closing ring is detected. */
public final class SpellActivationEffects {

    private SpellActivationEffects() {}

    public static void play(SaveGesturePayload payload, Player player) {
        if (payload.activationRingStrokeIds().isEmpty()) return;
        if (player == null) return;
        Level level = player.level();
        if (!(level instanceof ServerLevel serverLevel)) return;

        BlockPos origin = payload.blockOrigin();
        double x, y, z;
        if (origin != null) {
            x = origin.getX() + 0.5;
            y = origin.getY() + 0.5;
            z = origin.getZ() + 0.5;
        } else {
            x = player.getX();
            y = player.getY() + player.getBbHeight() * 0.5;
            z = player.getZ();
        }

        serverLevel.playSound(null, x, y, z,
                SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 1.0f, 1.0f);
        serverLevel.sendParticles(ParticleTypes.SCRAPE,
                x, y, z, 40, 0.4, 0.4, 0.4, 0.6);
        serverLevel.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                x, y, z, 20, 0.8, 0.2, 0.8, 0.3);
    }
}
