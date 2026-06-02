package com.crsocial.witchhatatelier.spell.meaning.effect;

import com.crsocial.witchhatatelier.WitchHatAtelierMod;
import com.crsocial.witchhatatelier.entity.ModEntities;
import com.crsocial.witchhatatelier.entity.PyreballEntity;
import com.crsocial.witchhatatelier.entity.child.LightBlockChild;
import com.crsocial.witchhatatelier.spell.cast.CastContext;
import com.crsocial.witchhatatelier.spell.meaning.BehaviorOp;
import com.crsocial.witchhatatelier.spell.meaning.ExecutableSpell;
import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * Fire + Levitation → a {@link PyreballEntity}. Attempts to resolve the matrix
 * cell's {@code entity} id and spawn the entity at the spell origin; falls back
 * to the configured {@code fallback_block} only when the entity type is
 * unregistered or spawning fails outright.
 */
public final class PyreballEffect implements EffectKind {

    public static final String KEY = "pyreball";

    /** Light level the orb's light-block child emits (0-15). */
    private static final int LIGHT_LEVEL = 15;

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public List<EffectInstruction> parsePayload(List<JsonObject> effects) {
        return EffectInstruction.parseAll(effects);
    }

    @Override
    @SuppressWarnings("unchecked")
    public void execute(ServerLevel level, Player caster, ExecutableSpell spell) {
        BlockPos origin = BlockPos.containing(
                spell.originWorld().x, spell.originWorld().y, spell.originWorld().z);

        for (BehaviorOp op : spell.ops()) {
            if (!KEY.equalsIgnoreCase(op.kind())) continue;
            if (!(op.payload() instanceof List<?> raw)) continue;
            List<EffectInstruction> instructions = (List<EffectInstruction>) raw;

            for (EffectInstruction ins : instructions) {
                if (!(ins instanceof UniqueEntitySpawn spawn)) continue;
                if (spawnEntity(level, spell, origin, spawn)) continue;
                placeFallbackBlock(level, origin, spawn);
            }
        }
    }

    // ── Channeled-cast lifecycle ────────────────────────────────────────────────
    // The orb is spawned once and then re-positioned to the caster's live crosshair
    // every tick so it follows the player's aim for the channel's duration.

    @Override
    public void begin(CastContext ctx) {
        float cpt = ctx.spell().totalCostPerTick();
        int lifetime = cpt > 0f
                ? (int) Math.min(Integer.MAX_VALUE, ctx.fuel().capacity() / cpt)
                : Integer.MAX_VALUE;
        PyreballEntity orb = spawnTracked(ctx.level(), ctx.spell(), lifetime);
        if (orb != null) {
            ctx.scratch().put(KEY, orb);
        } else {
            // Entity unavailable (unregistered/failed) → fall back to the one-shot path.
            execute(ctx.level(), ctx.caster(), ctx.spell());
        }
    }

    @Override
    public void tick(CastContext ctx) {
        if (!(ctx.scratch().get(KEY) instanceof PyreballEntity orb)) return;
        if (orb.isRemoved()) return;
        var o = ctx.spell().originWorld();
        orb.setPos(o.x, o.y, o.z);
    }

    @Override
    public void end(CastContext ctx) {
        if (ctx.scratch().get(KEY) instanceof PyreballEntity orb && !orb.isRemoved()) {
            orb.discard();
        }
    }

    /**
     * Spawns a single {@link PyreballEntity} at the spell origin with its lifetime
     * pinned to the channel duration, and returns it for per-tick tracking. Returns
     * {@code null} when the matrix cell does not resolve to a pyreball entity (the
     * caller then uses the one-shot fallback).
     */
    @SuppressWarnings("unchecked")
    private PyreballEntity spawnTracked(ServerLevel level, ExecutableSpell spell, int lifetimeTicks) {
        for (BehaviorOp op : spell.ops()) {
            if (!KEY.equalsIgnoreCase(op.kind())) continue;
            if (!(op.payload() instanceof List<?> raw)) continue;
            for (EffectInstruction ins : (List<EffectInstruction>) raw) {
                if (!(ins instanceof UniqueEntitySpawn spawn)) continue;
                ResourceLocation entityId = spawn.entityId();
                if (entityId == null) continue;
                EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getOptional(entityId).orElse(null);
                if (type != ModEntities.PYREBALL.get()) continue;

                PyreballEntity orb = ModEntities.PYREBALL.get().create(level);
                if (orb == null) continue;
                float power = spell.magnitude().power();
                float maxScale = Mth.clamp(0.5f + 0.5f * power, 0.5f, 4.0f);
                orb.moveTo(spell.originWorld().x, spell.originWorld().y, spell.originWorld().z, 0f, 0f);
                configureOrb(orb, lifetimeTicks, maxScale);
                level.addFreshEntity(orb);
                WitchHatAtelierMod.LOGGER.info(
                        "[{}] Channel orb spawned (lifetime={}t, maxScale={}).", KEY, lifetimeTicks, maxScale);
                return orb;
            }
        }
        return null;
    }

    /**
     * Applies the per-cast lifetime and scale to a freshly created orb and declares
     * its children — here, a light block that follows the orb around. Future
     * pyreball variants can attach more children at this single point.
     */
    private void configureOrb(PyreballEntity orb, int lifetimeTicks, float maxScale) {
        orb.setLifetimeTicks(lifetimeTicks);
        orb.setMaxScale(maxScale);
        orb.children().add(new LightBlockChild(LIGHT_LEVEL));
    }

    private boolean spawnEntity(ServerLevel level, ExecutableSpell spell,
                                BlockPos origin, UniqueEntitySpawn spawn) {
        ResourceLocation entityId = spawn.entityId();
        if (entityId == null) return false;
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getOptional(entityId).orElse(null);
        if (type == null) {
            WitchHatAtelierMod.LOGGER.warn(
                    "[{}] Entity id '{}' is not registered; falling back to block.", KEY, entityId);
            return false;
        }

        int lifetime = 200; // 10 seconds, constant for one-shot fallback (no fuel context)
        float power = spell.magnitude().power();
        float maxScale = Mth.clamp(0.5f + 0.5f * power, 0.5f, 4.0f);

        if (type == ModEntities.PYREBALL.get()) {
            PyreballEntity entity = ModEntities.PYREBALL.get().create(level);
            if (entity == null) return false;
            entity.moveTo(spell.originWorld().x, spell.originWorld().y, spell.originWorld().z,
                    0f, 0f);
            configureOrb(entity, lifetime, maxScale);
            level.addFreshEntity(entity);
            WitchHatAtelierMod.LOGGER.info(
                    "[{}] Spawned {} at {} (lifetime={}t, maxScale={}).",
                    KEY, entityId, origin, lifetime, maxScale);
            return true;
        }

        var generic = type.spawn(level, origin, MobSpawnType.MOB_SUMMONED);
        if (generic == null) {
            WitchHatAtelierMod.LOGGER.warn("[{}] Failed to spawn '{}' at {}.", KEY, entityId, origin);
            return false;
        }
        WitchHatAtelierMod.LOGGER.info(
                "[{}] Spawned generic entity {} at {} (lifetime hint={}t, not applied).",
                KEY, entityId, origin, lifetime);
        return true;
    }

    private void placeFallbackBlock(ServerLevel level, BlockPos origin, UniqueEntitySpawn spawn) {
        if (spawn.fallbackBlock() == null) {
            WitchHatAtelierMod.LOGGER.info(
                    "[{}] No fallback_block configured; skipping.", KEY);
            return;
        }
        Block block = BuiltInRegistries.BLOCK.getOptional(spawn.fallbackBlock()).orElse(null);
        if (block == null) {
            WitchHatAtelierMod.LOGGER.warn(
                    "[{}] Unknown fallback_block '{}'; skipping.", KEY, spawn.fallbackBlock());
            return;
        }
        BlockState existing = level.getBlockState(origin);
        if (existing.isAir() || existing.canBeReplaced()) {
            level.setBlock(origin, block.defaultBlockState(), Block.UPDATE_ALL);
            WitchHatAtelierMod.LOGGER.info(
                    "[{}] Placed fallback block {} at {}.", KEY, spawn.fallbackBlock(), origin);
        }
    }
}
