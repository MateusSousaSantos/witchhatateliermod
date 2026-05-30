package com.crsocial.witchhatatelier.spell.meaning.effect;

import com.google.gson.JsonObject;

import java.util.List;

/**
 * Fire + Levitation → a pyreball, a hovering orb of fire that floats above the
 * glyph and scorches nearby entities. Its {@code effects[]} are authored as
 * {@link UniqueEntitySpawn} entries — a unique spell-driven entity that, absent
 * an entity type, degrades to placing a block. Phase 2 keeps {@code execute} as
 * the inherited no-op; Phase 3 fills in the world mutation per
 * docs/magic_system/implementation_roadmap.md.
 */
public final class PyreballEffect implements EffectKind {

    public static final String KEY = "pyreball";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public List<EffectInstruction> parsePayload(List<JsonObject> effects) {
        return EffectInstruction.parseAll(effects);
    }
}
