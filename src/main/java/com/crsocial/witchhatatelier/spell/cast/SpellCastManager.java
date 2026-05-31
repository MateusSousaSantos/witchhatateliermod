package com.crsocial.witchhatatelier.spell.cast;

import com.crsocial.witchhatatelier.WitchHatAtelierMod;
import com.crsocial.witchhatatelier.items.SpellPaperItem;
import com.crsocial.witchhatatelier.network.CastingStatePayload;
import com.crsocial.witchhatatelier.spell.meaning.ExecutableSpell;
import com.crsocial.witchhatatelier.spell.meaning.SpellExecutor;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-side registry of in-flight channeled hand casts. A cast is started when
 * recognition succeeds for a hand-held paper; thereafter it is driven once per
 * {@code ServerTickEvent.Post} (see {@link SpellCastEvents}):
 *
 * <ul>
 *   <li>each tick re-aims the spell at the caster's live crosshair so entity
 *       effects follow the player's look;</li>
 *   <li>block effects fired once at {@code begin} and simply hold;</li>
 *   <li>swapping the held paper, logging out, or dying cancels the cast;</li>
 *   <li>the paper is marked spent on completion <em>or</em> cancellation.</li>
 * </ul>
 *
 * All state is touched on the server thread only; the map is concurrent and
 * iterated over a snapshot defensively.
 */
public final class SpellCastManager {

    private static final SpellCastManager INSTANCE = new SpellCastManager();

    public static SpellCastManager get() {
        return INSTANCE;
    }

    private final Map<UUID, ActiveSpellCast> active = new ConcurrentHashMap<>();

    private SpellCastManager() {}

    // ── Lifecycle entry points ──────────────────────────────────────────────────

    /**
     * Begins a channeled cast keyed to the inscribed {@code paper} the save step
     * produced. The paper may be in a hand (casting needs a Wand in the main hand,
     * so usually the off-hand) or in the inventory (drawn on a stack → the new
     * inscribed copy landed in a free slot). Fires every effect's {@code begin}
     * immediately, then registers the cast. If {@code paper} can't be tracked, the
     * spell is fired once so it still manifests. Any pre-existing cast is canceled.
     */
    public void start(ServerPlayer caster, ExecutableSpell spell, ItemStack paper) {
        if (caster == null || spell == null) return;
        final ServerLevel level = caster.serverLevel();

        if (paper == null || paper.isEmpty()
                || !(paper.getItem() instanceof SpellPaperItem sp) || sp.isBlank()
                || SpellPaperItem.isSpent(paper)) {
            WitchHatAtelierMod.LOGGER.info(
                    "[SpellCast] No trackable paper for '{}'; firing once: {}",
                    caster.getScoreboardName(), spell.toLogString());
            SpellExecutor.run(level, caster, spell);
            return;
        }

        ActiveSpellCast existing = active.remove(caster.getUUID());
        if (existing != null) {
            endCast(caster, existing, CancelReason.ERROR);
        }

        int total = (int) Math.max(1L, Math.min(Integer.MAX_VALUE, spell.durationTicks()));
        UUID castId = UUID.randomUUID();
        SpellPaperItem.markCastInProgress(paper, castId);
        boolean heldAtStart = findHeldCastHand(caster, castId) != null;

        ActiveSpellCast cast = new ActiveSpellCast(caster.getUUID(), spell, castId, total, heldAtStart);
        final ExecutableSpell live = liveSpell(cast, caster);
        SpellExecutor.forEachDistinctKind(live, (kind, s) -> {
            try {
                kind.begin(new CastContext(level, caster, s, total, total, cast.scratch));
            } catch (Exception e) {
                WitchHatAtelierMod.LOGGER.error(
                        "[SpellCast] begin '{}' threw: {}", kind.key(), e.toString(), e);
            }
        });

        active.put(caster.getUUID(), cast);
        sendCastingState(caster, true);
        WitchHatAtelierMod.LOGGER.info(
                "[SpellCast] Started cast {} ({}t, heldAtStart={}) for '{}'.",
                castId, total, heldAtStart, caster.getScoreboardName());
    }

    /** Cancels the player's active cast, if any (used on logout). */
    public void cancel(ServerPlayer player, CancelReason reason) {
        if (player == null) return;
        ActiveSpellCast cast = active.remove(player.getUUID());
        if (cast != null) {
            endCast(player, cast, reason);
        }
    }

    /** Drives every active cast one tick. Called from {@code ServerTickEvent.Post}. */
    public void tickAll(MinecraftServer server) {
        if (active.isEmpty()) return;

        List<UUID> toRemove = new ArrayList<>();
        for (ActiveSpellCast cast : active.values()) {
            ServerPlayer player = server.getPlayerList().getPlayer(cast.casterId);

            // Player gone, removed, or dying → tear down.
            if (player == null || player.isRemoved() || player.isDeadOrDying()) {
                endCast(player, cast, player == null ? CancelReason.LOGOUT : CancelReason.DEATH);
                toRemove.add(cast.casterId);
                continue;
            }

            // Cancellation depends on how the cast began:
            //  • held in a hand → cancel when removed from BOTH hands (it uses a Wand
            //    in the main hand, so the paper normally lives in the off-hand).
            //  • began in the inventory (drawn on a stack) → only cancel if the paper
            //    disappears entirely; otherwise run to completion.
            boolean stillValid = cast.heldAtStart
                    ? findHeldCastHand(player, cast.castId) != null
                    : SpellPaperItem.findByCastId(player, cast.castId) != null;
            if (!stillValid) {
                endCast(player, cast, CancelReason.ITEM_CHANGED);
                toRemove.add(cast.casterId);
                continue;
            }

            // Drive this tick with the spell re-aimed at the live crosshair.
            final ServerPlayer fp = player;
            final ServerLevel level = player.serverLevel();
            final ExecutableSpell live = liveSpell(cast, player);
            final int remaining = cast.remainingTicks;
            SpellExecutor.forEachDistinctKind(live, (kind, s) -> {
                try {
                    kind.tick(new CastContext(level, fp, s, cast.totalTicks, remaining, cast.scratch));
                } catch (Exception e) {
                    WitchHatAtelierMod.LOGGER.error(
                            "[SpellCast] tick '{}' threw: {}", kind.key(), e.toString(), e);
                }
            });

            cast.remainingTicks--;
            if (cast.remainingTicks <= 0) {
                endCast(player, cast, CancelReason.COMPLETED);
                toRemove.add(cast.casterId);
            }
        }

        for (UUID id : toRemove) active.remove(id);
    }

    // ── Internals ───────────────────────────────────────────────────────────────

    /** Ends the channel: runs every effect's {@code end}, consumes the paper, syncs state. */
    private void endCast(@Nullable ServerPlayer player, ActiveSpellCast cast, CancelReason reason) {
        if (cast.finished) return;
        cast.finished = true;

        final ServerLevel level = player != null ? player.serverLevel() : null;
        SpellExecutor.forEachDistinctKind(cast.baseSpell, (kind, s) -> {
            try {
                kind.end(new CastContext(level, player, s, cast.totalTicks, cast.remainingTicks, cast.scratch));
            } catch (Exception e) {
                WitchHatAtelierMod.LOGGER.error(
                        "[SpellCast] end '{}' threw: {}", kind.key(), e.toString(), e);
            }
        });

        consumePaper(player, cast);
        if (player != null) {
            sendCastingState(player, false);
        }
        WitchHatAtelierMod.LOGGER.info(
                "[SpellCast] Ended cast {} ({}) for {}.", cast.castId, reason, cast.casterId);
    }

    /** Marks the cast's paper spent, locating it by {@code castId} (survives slot moves). */
    private void consumePaper(@Nullable ServerPlayer player, ActiveSpellCast cast) {
        if (player == null) return;
        ItemStack paper = SpellPaperItem.findByCastId(player, cast.castId);
        if (paper != null && !paper.isEmpty()) {
            SpellPaperItem.markSpent(paper);
            SpellPaperItem.clearCastId(paper);
        }
    }

    /** Hand currently holding the paper stamped with {@code castId}, or {@code null}. */
    private static InteractionHand findHeldCastHand(ServerPlayer player, UUID castId) {
        for (InteractionHand h : InteractionHand.values()) {
            if (isMatchingPaper(player.getItemInHand(h), castId)) {
                return h;
            }
        }
        return null;
    }

    /** Rebuilds the spell with origin/normal taken from the caster's live aim. */
    private ExecutableSpell liveSpell(ActiveSpellCast cast, ServerPlayer player) {
        CastOrigin.OriginPair aim = CastOrigin.fromLiveAim(player);
        return cast.baseSpell.withOrigin(
                aim.originWorld(), aim.surfaceNormal(), cast.baseSpell.direction());
    }

    private void sendCastingState(ServerPlayer player, boolean activeFlag) {
        CastingStatePayload payload = new CastingStatePayload(player.getId(), activeFlag);
        PacketDistributor.sendToPlayer(player, payload);
        PacketDistributor.sendToPlayersTrackingEntity(player, payload);
    }

    private static boolean isMatchingPaper(ItemStack stack, UUID castId) {
        if (stack == null || stack.isEmpty()) return false;
        if (!(stack.getItem() instanceof SpellPaperItem paper) || paper.isBlank()) return false;
        if (SpellPaperItem.isSpent(stack)) return false;
        return castId.equals(SpellPaperItem.getCastId(stack));
    }
}
