package com.crsocial.witchhatatelier.spell.meaning.effect;

import com.crsocial.witchhatatelier.WitchHatAtelierMod;
import com.crsocial.witchhatatelier.spell.meaning.BehaviorOp;
import com.crsocial.witchhatatelier.spell.meaning.ExecutableSpell;
import com.crsocial.witchhatatelier.spell.meaning.Magnitude;
import com.crsocial.witchhatatelier.spell.meaning.Origin;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import org.joml.Vector3f;

import java.util.List;

/**
 * Shared {@link EffectKind#execute} body for column-shaped block effects. The
 * stone-pillar and flame-pillar cells use identical placement semantics — only
 * the block id differs, and that comes from the matrix JSON. Centralizing the
 * math here keeps {@link com.crsocial.witchhatatelier.spell.meaning.effect.earth.StonePillarEffect}
 * / {@link com.crsocial.witchhatatelier.spell.meaning.effect.fire.FlamePillarEffect} as thin
 * wrappers carrying just a fallback block id and a {@link #key()}.
 */
public final class PillarEffects {

    /** Column length (blocks) for a particle pillar whose instruction sets no {@code reach}. */
    private static final float DEFAULT_PARTICLE_REACH = 4.0f;

    private PillarEffects() {}

    /**
     * Direction the pillar grows. The spell's {@code direction} is the authoritative
     * aim — {@code MeaningEngine.resolveDirection} builds it from the surface normal's
     * vertical plus the sign-placement net, and the Column sign's {@code directionBias}
     * leans it toward the side it was drawn (scaled by quality × size). We grow along
     * that so an off-centre Column skews the pillar; a centred/undirected cast leaves
     * {@code direction} zero and we fall back to the straight surface normal.
     */
    private static Vector3f columnGrow(ExecutableSpell spell) {
        // Direction-based skew is a surface-cast feature. Hand casts re-aim to the
        // crosshair each tick, so they grow straight along the (live-aim) surface
        // normal and ignore the canvas-derived lean.
        Vector3f dir = spell.direction();
        if (spell.origin() != Origin.HAND && dir != null && dir.lengthSquared() > 1e-6f) {
            return new Vector3f(dir).normalize();
        }
        return ColumnPlacer.growDirection(spell.surfaceNormal());
    }

    /**
     * Pillar-placement workhorse. Loops over the parsed {@code spawn_blocks}
     * instructions on {@code op} and extrudes a column from the spell's
     * world-space origin along its surface normal for each one.
     */
    @SuppressWarnings("unchecked")
    public static int executeColumn(ServerLevel level, ExecutableSpell spell, BehaviorOp op,
                             Block fallbackBlock, String kindKey) {
        if (!(op.payload() instanceof List<?> raw) || raw.isEmpty()) {
            WitchHatAtelierMod.LOGGER.debug(
                    "[{}] No spawn_blocks instructions on op; nothing to place.", kindKey);
            return 0;
        }
        List<EffectInstruction> instructions = (List<EffectInstruction>) raw;

        Magnitude m = spell.magnitude();
        float quality = Math.max(0.1f, m.quality());
        float size = Math.max(0.1f, m.sizeNormalized());
        Vector3f origin = spell.originWorld();
        Vector3f grow = columnGrow(spell);
        BlockPos originBlock = BlockPos.containing(origin.x, origin.y, origin.z);

        int totalPlaced = 0;
        for (EffectInstruction ins : instructions) {
            if (!(ins instanceof SpawnBlocksInstruction sb)) continue;

            Block block = resolveBlock(sb.block(), fallbackBlock);
            int count = Math.max(1, op.count());
            int height = Math.max(1, Math.round(sb.blocksPerMagnitude() * count * size * quality));
            int placed = ColumnPlacer.placeColumn(level, origin, grow, height, block);
            totalPlaced += placed;

            WitchHatAtelierMod.LOGGER.info(
                    "[{}] Placed {}/{} {} block(s) at {} growing ({}, {}, {}) (count={}, size={}, quality={}).",
                    kindKey, placed, height,
                    BuiltInRegistries.BLOCK.getKey(block), originBlock,
                    String.format(java.util.Locale.ROOT, "%.2f", grow.x),
                    String.format(java.util.Locale.ROOT, "%.2f", grow.y),
                    String.format(java.util.Locale.ROOT, "%.2f", grow.z), count,
                    String.format(java.util.Locale.ROOT, "%.2f", size),
                    String.format(java.util.Locale.ROOT, "%.2f", quality));
        }
        return totalPlaced;
    }

    /**
     * Emits a column of {@code particle}s along the very geometry
     * {@link #executeColumn} places blocks on — a trail "forming" the pillar from
     * its source up to its tip. Recomputes origin / grow direction / height from
     * {@code spell} and {@code op} so the particles track the placed blocks exactly.
     * Cheap and stateless, so it's safe to call every tick of a channeled cast to
     * keep the flame alive.
     *
     * <p>The column length is read from whichever instruction the kind carries:
     * {@link SpawnBlocksInstruction} pillars (stone/flame) scale by
     * {@code blocks_per_magnitude} × quality; {@link SpawnParticlesInstruction}
     * pillars (the blockless {@code wind_pillar}) scale by the instruction's
     * {@code reach}. Either way the rendered particle is the one passed in, so a
     * particle pillar looks just like the flame pillar's trail with a different
     * particle.</p>
     */
    @SuppressWarnings("unchecked")
    public static void emitColumnTrail(ServerLevel level, ExecutableSpell spell, BehaviorOp op,
                                       ParticleOptions particle) {
        if (!(op.payload() instanceof List<?> raw) || raw.isEmpty()) return;
        List<EffectInstruction> instructions = (List<EffectInstruction>) raw;

        Magnitude m = spell.magnitude();
        float quality = Math.max(0.1f, m.quality());
        float size = Math.max(0.1f, m.sizeNormalized());
        Vector3f origin = spell.originWorld();
        Vector3f grow = columnGrow(spell);
        int perBlock = Math.max(1, Math.round(size * 2f));
        int count = Math.max(1, op.count());

        for (EffectInstruction ins : instructions) {
            int height;
            if (ins instanceof SpawnBlocksInstruction sb) {
                height = Math.max(1, Math.min(ColumnPlacer.MAX_HEIGHT,
                        Math.round(sb.blocksPerMagnitude() * count * size * quality)));
            } else if (ins instanceof SpawnParticlesInstruction sp) {
                float reach = sp.reach() > 0f ? sp.reach() : DEFAULT_PARTICLE_REACH;
                height = Math.max(1, Math.min(ColumnPlacer.MAX_HEIGHT,
                        Math.round(reach * count * size)));
            } else {
                continue;
            }
            // Emit along the continuous diagonal line so the trail follows the aim.
            for (int i = 0; i < height; i++) {
                double px = origin.x + grow.x * (i + 0.5);
                double py = origin.y + grow.y * (i + 0.5);
                double pz = origin.z + grow.z * (i + 0.5);
                level.sendParticles(particle, px, py, pz, perBlock, 0.2, 0.2, 0.2, 0.01);
            }
        }
    }

    private static Block resolveBlock(ResourceLocation id, Block fallback) {
        if (id == null) return fallback;
        Block b = BuiltInRegistries.BLOCK.getOptional(id).orElse(null);
        if (b == null || b == Blocks.AIR) {
            WitchHatAtelierMod.LOGGER.warn(
                    "[PillarEffects] Unknown block id '{}'; using fallback {}.",
                    id, BuiltInRegistries.BLOCK.getKey(fallback));
            return fallback;
        }
        return b;
    }
}
