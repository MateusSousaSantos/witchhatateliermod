package com.crsocial.witchhatatelier.spell.meaning.effect;

import com.google.gson.JsonObject;

import java.util.List;

/**
 * Shared {@link #parsePayload} for column-shaped block effects — promotes the
 * matrix JSON's {@code effects[]} entries into typed {@link EffectInstruction}
 * records so {@link EffectKind#execute} doesn't have to crack JSON at runtime.
 */
public abstract class AbstractPillarEffect implements EffectKind {

    @Override
    public List<EffectInstruction> parsePayload(List<JsonObject> effects) {
        return EffectInstruction.parseAll(effects);
    }
}
