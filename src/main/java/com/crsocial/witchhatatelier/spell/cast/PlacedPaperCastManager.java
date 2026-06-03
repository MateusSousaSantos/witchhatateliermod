package com.crsocial.witchhatatelier.spell.cast;

import com.crsocial.witchhatatelier.Config;
import com.crsocial.witchhatatelier.WitchHatAtelierMod;
import com.crsocial.witchhatatelier.blocks.PlacedPaperBlockEntity;
import com.crsocial.witchhatatelier.spell.meaning.ExecutableSpell;
import com.crsocial.witchhatatelier.spell.meaning.SpellExecutor;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-side registry of in-flight <em>surface</em> casts — placed-paper spells
 * that sustain themselves rather than firing once. It is the {@code PLACED_PAPER}
 * analogue of {@link SpellCastManager}: it reuses the same
 * {@link com.crsocial.witchhatatelier.spell.meaning.effect.EffectKind}
 * {@code begin}/{@code tick}/{@code end} lifecycle and {@link SpellFuel} budget,
 * but anchors the spell to a block face (no live aim) and is keyed by world +
 * position instead of by player.
 *
 * <p>A surface cast is sustained only when its spell has a per-tick fuel cost;
 * the {@link com.crsocial.witchhatatelier.network.SaveGestureHandler} keeps
 * zero-per-tick surface spells on the old one-shot path. Each tick drains fuel;
 * the cast ends — and the {@code placed_paper} is marked spent — when the fuel
 * runs out, the block is broken/unloaded, or it is superseded by a redraw.</p>
 *
 * <p>All state is touched on the server thread only; the map is concurrent and
 * iterated over a snapshot defensively.</p>
 */
public final class PlacedPaperCastManager {

    private static final PlacedPaperCastManager INSTANCE = new PlacedPaperCastManager();

    public static PlacedPaperCastManager get() {
        return INSTANCE;
    }

    /** Identifies a surface cast by the block it emerges from. */
    private record SurfaceKey(ResourceKey<Level> dimension, BlockPos pos) {}

    private final Map<SurfaceKey, ActiveSurfaceCast> active = new ConcurrentHashMap<>();

    private PlacedPaperCastManager() {}

    // ── Lifecycle entry points ──────────────────────────────────────────────────

    /**
     * Begins a sustained surface cast anchored to the {@code placed_paper} at
     * {@code pos}. Fires every effect's {@code begin} immediately, then registers
     * the cast so {@link #tickAll} drives it until its fuel drains. Any pre-existing
     * cast on the same block is superseded (ended without spending it, so the fresh
     * cast owns the block).
     */
    public void start(ServerLevel level, @Nullable Player caster, ExecutableSpell spell, BlockPos pos) {
        if (level == null || spell == null) return;
        SurfaceKey key = new SurfaceKey(level.dimension(), pos.immutable());

        ActiveSurfaceCast existing = active.remove(key);
        if (existing != null) {
            endCast(level, existing, CancelReason.ERROR, false);
        }

        float capacity = Config.DEFAULT_SPELL_FUEL.get().floatValue();
        SpellFuel fuel = new SpellFuel(capacity);
        ActiveSurfaceCast cast = new ActiveSurfaceCast(key, spell, fuel);

        SpellExecutor.forEachDistinctKind(spell, (kind, s) -> {
            try {
                kind.begin(new CastContext(level, null, s, cast.scratch, cast.fuel));
            } catch (Exception e) {
                WitchHatAtelierMod.LOGGER.error(
                        "[SurfaceCast] begin '{}' threw: {}", kind.key(), e.toString(), e);
            }
        });

        active.put(key, cast);
        WitchHatAtelierMod.LOGGER.info(
                "[SurfaceCast] Started cast at {} (fuel={}) for {}.",
                pos, capacity, spell.toLogString());
    }

    /** Drives every active surface cast one tick. Called from {@code ServerTickEvent.Post}. */
    public void tickAll(MinecraftServer server) {
        if (active.isEmpty()) return;

        List<SurfaceKey> toRemove = new ArrayList<>();
        for (ActiveSurfaceCast cast : active.values()) {
            ServerLevel level = server.getLevel(cast.key.dimension());
            BlockPos pos = cast.key.pos();

            // Block gone (broken, replaced, or its chunk unloaded) → tear down. The
            // BE is absent so there is nothing left to mark spent.
            if (level == null || !level.isLoaded(pos)
                    || !(level.getBlockEntity(pos) instanceof PlacedPaperBlockEntity placed)) {
                endCast(level, cast, CancelReason.SOURCE_REMOVED, false);
                toRemove.add(cast.key);
                continue;
            }
            // Already spent (e.g. by an external path) → just clean up.
            if (placed.isSpent()) {
                endCast(level, cast, CancelReason.ERROR, false);
                toRemove.add(cast.key);
                continue;
            }

            final ServerLevel fl = level;
            SpellExecutor.forEachDistinctKind(cast.spell, (kind, s) -> {
                try {
                    kind.tick(new CastContext(fl, null, s, cast.scratch, cast.fuel));
                } catch (Exception e) {
                    WitchHatAtelierMod.LOGGER.error(
                            "[SurfaceCast] tick '{}' threw: {}", kind.key(), e.toString(), e);
                }
            });

            float costThisTick = cast.spell.totalCostPerTick();
            if (costThisTick > 0f && !cast.fuel.consume(costThisTick)) {
                endCast(level, cast, CancelReason.FUEL_EXHAUSTED, true);
                toRemove.add(cast.key);
            }
        }

        for (SurfaceKey id : toRemove) active.remove(id);
    }

    // ── Internals ───────────────────────────────────────────────────────────────

    /**
     * Ends the cast: runs every effect's {@code end}, optionally marks the source
     * {@code placed_paper} spent. {@code markSpent} is {@code false} when the block
     * is already gone or the cast is being superseded by a redraw.
     */
    private void endCast(@Nullable ServerLevel level, ActiveSurfaceCast cast,
                         CancelReason reason, boolean markSpent) {
        if (cast.finished) return;
        cast.finished = true;

        SpellExecutor.forEachDistinctKind(cast.spell, (kind, s) -> {
            try {
                kind.end(new CastContext(level, null, s, cast.scratch, cast.fuel));
            } catch (Exception e) {
                WitchHatAtelierMod.LOGGER.error(
                        "[SurfaceCast] end '{}' threw: {}", kind.key(), e.toString(), e);
            }
        });

        if (markSpent && level != null
                && level.getBlockEntity(cast.key.pos()) instanceof PlacedPaperBlockEntity placed) {
            placed.setSpent(true);
        }
        WitchHatAtelierMod.LOGGER.info(
                "[SurfaceCast] Ended cast at {} ({}).", cast.key.pos(), reason);
    }

    /** Mutable per-block state for one in-flight surface cast. */
    private static final class ActiveSurfaceCast {
        final SurfaceKey key;
        /** Origin/normal are fixed to the block face, so the spell is used as-is each tick. */
        final ExecutableSpell spell;
        final EffectScratch scratch = new EffectScratch();
        final SpellFuel fuel;
        boolean finished;

        ActiveSurfaceCast(SurfaceKey key, ExecutableSpell spell, SpellFuel fuel) {
            this.key = key;
            this.spell = spell;
            this.fuel = fuel;
        }
    }
}
