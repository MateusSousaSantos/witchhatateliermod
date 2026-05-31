package com.crsocial.witchhatatelier.spell.meaning;

import com.crsocial.witchhatatelier.WitchHatAtelierMod;
import com.crsocial.witchhatatelier.spell.meaning.effect.EffectKind;
import com.crsocial.witchhatatelier.spell.meaning.effect.EffectRegistry;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;

import java.util.HashSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;

/**
 * Server-side dispatcher: walks an {@link ExecutableSpell}'s ops, resolves each
 * one to an {@link EffectKind}, and invokes {@link EffectKind#execute} exactly
 * once per distinct kind. The kind itself iterates {@link ExecutableSpell#ops()}
 * to handle stacked ops sharing the same behaviour, so we deduplicate here.
 */
public final class SpellExecutor {

    private SpellExecutor() {}

    /**
     * Runs the spell against the world.
     *
     * @return {@code true} if at least one effect kind handled the spell — the
     *         caller uses this to decide whether to consume the medium.
     */
    public static boolean run(ServerLevel level, Player caster, ExecutableSpell spell) {
        boolean[] any = {false};
        forEachDistinctKind(spell, (kind, s) -> {
            try {
                kind.execute(level, caster, s);
                any[0] = true;
            } catch (Exception e) {
                WitchHatAtelierMod.LOGGER.error(
                        "[SpellExecutor] Effect '{}' threw during execute: {}", kind.key(), e.toString(), e);
            }
        });
        return any[0];
    }

    /**
     * Resolves each <em>distinct</em> behaviour kind in the spell to its
     * {@link EffectKind} and invokes {@code action} once per kind. Shared by
     * {@link #run} (one-shot surface casts) and the channeled-cast manager so
     * both deduplicate stacked ops the same way.
     */
    public static void forEachDistinctKind(ExecutableSpell spell,
                                           BiConsumer<EffectKind, ExecutableSpell> action) {
        if (spell == null || spell.ops().isEmpty()) return;
        Set<String> handled = new HashSet<>();
        for (BehaviorOp op : spell.ops()) {
            String key = op.kind();
            if (key == null || !handled.add(key.toLowerCase(Locale.ROOT))) continue;
            Optional<EffectKind> kind = EffectRegistry.get().find(key);
            if (kind.isEmpty()) {
                WitchHatAtelierMod.LOGGER.warn(
                        "[SpellExecutor] No EffectKind registered for '{}'; skipping op.", key);
                continue;
            }
            action.accept(kind.get(), spell);
        }
    }
}
