package com.crsocial.witchhatatelier.spell.meaning.effect.light;

import com.crsocial.witchhatatelier.WitchHatAtelierMod;
import com.crsocial.witchhatatelier.spell.meaning.BehaviorOp;
import com.crsocial.witchhatatelier.spell.meaning.ExecutableSpell;
import com.crsocial.witchhatatelier.spell.meaning.effect.EffectInstruction;
import com.crsocial.witchhatatelier.spell.meaning.effect.EffectKind;
import com.crsocial.witchhatatelier.spell.meaning.effect.ParticleSupport;
import com.crsocial.witchhatatelier.spell.meaning.effect.SpawnParticlesInstruction;
import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;

import java.util.List;

/**
 * {@code behavior_kind: "light"} — the LIGHT sigil's default manifestation: a little puff
 * of (data-driven) particles with a {@link Blocks#LIGHT} block parked at the origin for the
 * duration of the puff, then cleared. Particles come entirely from the cell's
 * {@link SpawnParticlesInstruction}s — {@code minecraft:dust} with a {@code color} gives the
 * warm yellow glow; see {@link ParticleSupport}.
 *
 * <p>The puff is a one-shot: {@link #execute} (surface cast) and the default {@code begin}
 * (channel start) both fire a single burst and place the transient light. There is no
 * per-tick streaming — a light spell is a flash, not a sustained beam.</p>
 */
public final class LightEffect implements EffectKind {

    public static final String KEY = "light";

    /** Light level emitted by the transient {@link Blocks#LIGHT} block (0-15). */
    private static final int LIGHT_LEVEL = 15;
    /** How long the light block lingers — the lifespan of the puff, in ticks (60t = 3s). */
    private static final int PUFF_TICKS = 60;
    /** Hard ceiling per emission so a heavily-stacked cast can't flood the network. */
    private static final int MAX_PARTICLES_PER_EMISSION = 256;

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public List<EffectInstruction> parsePayload(List<JsonObject> effects) {
        return EffectInstruction.parseAll(effects);
    }

    @Override
    public void execute(ServerLevel level, Player caster, ExecutableSpell spell) {
        puff(level, spell);
        placeTransientLight(level, spell);
    }

    /** Emits the cell's particles once at the spell origin, scaled by size and sign stack. */
    @SuppressWarnings("unchecked")
    private void puff(ServerLevel level, ExecutableSpell spell) {
        var o = spell.originWorld();
        float size = Math.max(0.1f, spell.magnitude().sizeNormalized());

        for (BehaviorOp op : spell.ops()) {
            if (!KEY.equalsIgnoreCase(op.kind())) continue;
            if (!(op.payload() instanceof List<?> raw)) continue;

            for (EffectInstruction ins : (List<EffectInstruction>) raw) {
                if (!(ins instanceof SpawnParticlesInstruction sp)) continue;
                ParticleOptions particle = ParticleSupport.resolve(sp, KEY);
                if (particle == null) continue;

                int count = Math.min(MAX_PARTICLES_PER_EMISSION,
                        Math.max(1, Math.round(sp.count() * size * Math.max(1, op.count()))));
                level.sendParticles(particle,
                        o.x, o.y + sp.verticalOffset(), o.z,
                        count, sp.spread(), sp.spread(), sp.spread(), sp.speed());
            }
        }
    }

    /**
     * Parks a {@link Blocks#LIGHT} block at the origin (into air only) and schedules its
     * removal once the puff is over. Uses {@code UPDATE_CLIENTS} so the purely-cosmetic block
     * doesn't fire neighbour/redstone updates; the lighting engine relights regardless.
     */
    private void placeTransientLight(ServerLevel level, ExecutableSpell spell) {
        var o = spell.originWorld();
        BlockPos pos = BlockPos.containing(o.x, o.y, o.z);
        if (!level.getBlockState(pos).isAir()) return;

        level.setBlock(pos,
                Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, LIGHT_LEVEL),
                Block.UPDATE_CLIENTS);

        MinecraftServer server = level.getServer();
        server.tell(new TickTask(server.getTickCount() + PUFF_TICKS, () -> {
            if (level.getBlockState(pos).is(Blocks.LIGHT)) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            }
        }));
        WitchHatAtelierMod.LOGGER.info(
                "[{}] Puff light at {} for {} ticks.", KEY, pos, PUFF_TICKS);
    }
}
