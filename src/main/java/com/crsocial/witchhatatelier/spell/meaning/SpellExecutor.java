package com.crsocial.witchhatatelier.spell.meaning;

import com.crsocial.witchhatatelier.WitchHatAtelierMod;
import com.crsocial.witchhatatelier.spell.meaning.effect.EffectKind;
import com.crsocial.witchhatatelier.spell.meaning.effect.EffectRegistry;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

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
        if (spell == null || spell.ops().isEmpty()) return false;

        Set<String> handled = new HashSet<>();
        boolean any = false;
        for (var op : spell.ops()) {
            String key = op.kind();
            if (key == null || !handled.add(key.toLowerCase(java.util.Locale.ROOT))) continue;
            Optional<EffectKind> kind = EffectRegistry.get().find(key);
            if (kind.isEmpty()) {
                WitchHatAtelierMod.LOGGER.warn(
                        "[SpellExecutor] No EffectKind registered for '{}'; skipping op.", key);
                continue;
            }
            try {
                kind.get().execute(level, caster, spell);
                any = true;
            } catch (Exception e) {
                WitchHatAtelierMod.LOGGER.error(
                        "[SpellExecutor] Effect '{}' threw during execute: {}", key, e.toString(), e);
            }
        }
        return any;
    }
}
