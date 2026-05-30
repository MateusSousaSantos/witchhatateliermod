package com.crsocial.witchhatatelier.spell.meaning.effect;

import com.crsocial.witchhatatelier.spell.meaning.ExecutableSpell;
import com.google.gson.JsonObject;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;

import java.util.List;

/**
 * Implementation of one {@code behavior_kind} from the spell matrix. Each kind
 * registers a singleton instance via {@link EffectRegistry#register}; the meaning
 * engine looks the kind up at evaluation time and the runtime invokes
 * {@link #execute} when the spell fires.
 *
 * <p>Phase 2 leaves {@link #execute} as a no-op log line; Phase 3 fills in the
 * actual world mutation per {@code docs/magic_system/implementation_roadmap.md}.</p>
 */
public interface EffectKind {

    /** Stable identifier — matches {@code behavior_kind} in the matrix JSON. */
    String key();

    /**
     * Translates the raw effect JSON entries into a payload object carried by
     * the resulting {@link com.crsocial.witchhatatelier.spell.meaning.BehaviorOp}.
     * Phase 2 keeps the payload opaque (the raw list); kinds may swap in a
     * stronger type once execution is wired up.
     */
    default Object parsePayload(List<JsonObject> effects) {
        return effects;
    }

    /**
     * Phase 3+ hook — executes the kind against the world. Phase 2 calls this
     * but the default impl is a no-op so the system can be exercised end-to-end
     * before runtime lands.
     */
    default void execute(ServerLevel level, Player caster, ExecutableSpell spell) {
        // No-op. Phase 3 will override.
    }
}
