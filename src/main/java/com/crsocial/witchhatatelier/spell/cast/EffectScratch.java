package com.crsocial.witchhatatelier.spell.cast;

import java.util.HashMap;
import java.util.Map;

/**
 * Per-cast mutable scratch store, keyed by effect {@code key()}. Lets a single
 * {@link com.crsocial.witchhatatelier.spell.meaning.effect.EffectKind} stash
 * state at {@code begin} (e.g. a spawned entity) and read it back during
 * {@code tick}/{@code end} of the same channel. Touched only on the server thread.
 */
public final class EffectScratch {

    private final Map<String, Object> data = new HashMap<>();

    public void put(String key, Object value) {
        data.put(key, value);
    }

    public Object get(String key) {
        return data.get(key);
    }
}
