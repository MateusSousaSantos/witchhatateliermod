package com.crsocial.witchhatatelier.spell.meaning.effect;

/**
 * Fire + Column → a roaring pillar of flame erupting upward from the glyph,
 * igniting and scorching anything caught in the column. Height scales with
 * spell size, mirroring earth's stone pillar but as vertical flame. Phase 2
 * keeps {@code execute} as the inherited no-op; Phase 3 fills in the
 * block-placement logic per docs/magic_system/implementation_roadmap.md.
 */
public final class FlamePillarEffect implements EffectKind {

    public static final String KEY = "flame_pillar";

    @Override
    public String key() {
        return KEY;
    }
}
