package com.crsocial.witchhatatelier.spell.meaning.effect;

import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A single parsed entry from a matrix cell's {@code effects[]} array. Each entry
 * carries a {@code type} discriminator plus type-specific fields (see
 * {@code docs/magic_system/03_meaning_engine.md}); this layer turns the raw
 * {@link JsonObject} into a typed instruction an {@link EffectKind} can act on.
 *
 * <p>Phase 2 parses the shapes but execution stays a no-op — {@link EffectKind#execute}
 * will consume these instructions once Phase 3 wires runtime execution.</p>
 */
public sealed interface EffectInstruction
        permits UniqueEntitySpawn, SpawnBlocksInstruction, SpawnParticlesInstruction, RawInstruction {

    /** The {@code type} string this instruction was parsed from. */
    String type();

    /** Parses every entry, preserving unknown types as {@link RawInstruction}. */
    static List<EffectInstruction> parseAll(List<JsonObject> effects) {
        List<EffectInstruction> out = new ArrayList<>(effects.size());
        for (JsonObject o : effects) out.add(parse(o));
        return List.copyOf(out);
    }

    /** Dispatches one entry on its {@code type}; unrecognized types stay raw. */
    static EffectInstruction parse(JsonObject o) {
        String type = o.has("type") ? o.get("type").getAsString().toLowerCase(Locale.ROOT) : "";
        return switch (type) {
            case UniqueEntitySpawn.TYPE -> UniqueEntitySpawn.fromJson(o);
            case SpawnBlocksInstruction.TYPE -> SpawnBlocksInstruction.fromJson(o);
            case SpawnParticlesInstruction.TYPE -> SpawnParticlesInstruction.fromJson(o);
            default -> new RawInstruction(type, o);
        };
    }
}
