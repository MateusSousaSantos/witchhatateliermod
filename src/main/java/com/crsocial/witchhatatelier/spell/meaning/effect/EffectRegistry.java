package com.crsocial.witchhatatelier.spell.meaning.effect;

import com.crsocial.witchhatatelier.spell.meaning.effect.air.AirPillarEffect;
import com.crsocial.witchhatatelier.spell.meaning.effect.earth.EarthPillarEffect;
import com.crsocial.witchhatatelier.spell.meaning.effect.fire.FirePillarEffect;
import com.crsocial.witchhatatelier.spell.meaning.effect.fire.PyreballEffect;
import com.crsocial.witchhatatelier.spell.meaning.effect.water.WaterPillarEffect;

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
        INSTANCE.register(new EarthPillarEffect());
        INSTANCE.register(new WaterPillarEffect());
        INSTANCE.register(new FirePillarEffect());
        INSTANCE.register(new AirPillarEffect());
        INSTANCE.register(new ParticleEffect());
        INSTANCE.register(new PyreballEffect());
    }

    public synchronized void register(EffectKind kind) {
        kinds.put(kind.key().toLowerCase(Locale.ROOT), kind);
    }

    public synchronized Optional<EffectKind> find(String key) {
        if (key == null) return Optional.empty();
        return Optional.ofNullable(kinds.get(key.toLowerCase(Locale.ROOT)));
    }
}
