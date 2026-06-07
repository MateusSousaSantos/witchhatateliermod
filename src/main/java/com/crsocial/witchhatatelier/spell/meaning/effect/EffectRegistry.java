package com.crsocial.witchhatatelier.spell.meaning.effect;

import com.crsocial.witchhatatelier.spell.meaning.effect.air.WindPillarEffect;
import com.crsocial.witchhatatelier.spell.meaning.effect.earth.ExcavateEffect;
import com.crsocial.witchhatatelier.spell.meaning.effect.earth.StonePillarEffect;
import com.crsocial.witchhatatelier.spell.meaning.effect.fire.FlamePillarEffect;
import com.crsocial.witchhatatelier.spell.meaning.effect.fire.PyreballEffect;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Singleton mapping {@code behavior_kind} strings to {@link EffectKind} impls.
 * Populated once at class-load by {@link #bootstrap()}; new kinds register
 * themselves there as Phase 5 expands the matrix.
 */
public final class EffectRegistry {

    private static final EffectRegistry INSTANCE = new EffectRegistry();

    private final Map<String, EffectKind> kinds = new HashMap<>();

    static {
        bootstrap();
    }

    private EffectRegistry() {}

    public static EffectRegistry get() {
        return INSTANCE;
    }

    private static void bootstrap() {
        INSTANCE.register(new ExcavateEffect());
        INSTANCE.register(new FlamePillarEffect());
        INSTANCE.register(new ParticleEffect());
        INSTANCE.register(new PyreballEffect());
        INSTANCE.register(new StonePillarEffect());
        INSTANCE.register(new WindPillarEffect());
    }

    public synchronized void register(EffectKind kind) {
        kinds.put(kind.key().toLowerCase(Locale.ROOT), kind);
    }

    public synchronized Optional<EffectKind> find(String key) {
        if (key == null) return Optional.empty();
        return Optional.ofNullable(kinds.get(key.toLowerCase(Locale.ROOT)));
    }
}
