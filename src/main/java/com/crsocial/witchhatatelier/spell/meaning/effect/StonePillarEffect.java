package com.crsocial.witchhatatelier.spell.meaning.effect;

/**
 * Earth + Column → a stone pillar rising from the glyph surface, height scaled
 * by spell size. Phase 2 keeps {@code execute} as the inherited no-op; Phase 3
 * fills in the block-placement logic.
 */
public final class StonePillarEffect implements EffectKind {

    public static final String KEY = "stone_pillar";

    @Override
    public String key() {
        return KEY;
    }
}
