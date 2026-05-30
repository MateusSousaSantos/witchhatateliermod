package com.crsocial.witchhatatelier.spell.meaning.effect;

import com.google.gson.JsonObject;

/**
 * Fallback {@link EffectInstruction} for {@code effects[]} entries whose
 * {@code type} has no typed parser yet (e.g. {@code spawn_blocks} in Phase 2).
 * Keeps the original JSON intact so no data is lost before the type is promoted
 * to a dedicated record.
 *
 * @param type the raw {@code type} string (empty when the entry omitted it)
 * @param raw  the untouched JSON object
 */
public record RawInstruction(String type, JsonObject raw) implements EffectInstruction {}
