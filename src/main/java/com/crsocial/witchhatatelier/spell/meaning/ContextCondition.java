package com.crsocial.witchhatatelier.spell.meaning;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import java.util.Locale;
import java.util.Optional;
import java.util.function.BiPredicate;

/**
 * A world predicate a matrix cell's {@code context_modifiers} can test to scale a spell
 * by its surroundings — "rain boosts water", "night boosts light", "underground boosts
 * earth". Evaluated once at cast time in {@link MeaningEngine} against the casting
 * position, so a spell is {@code (Sigil × Signs × Context)} the way the spec promises,
 * with no per-spell code — authors reference these by name in JSON.
 *
 * <p>Adding a condition here makes it available to <em>every</em> cell immediately. Each
 * test is a cheap, read-only world query; keep it that way so evaluation stays free of
 * side effects.</p>
 */
public enum ContextCondition {
    /** Rain is actually falling on this block (biome can rain, open to sky, correct height). */
    RAINING((l, p) -> l.isRainingAt(p)),
    /** A thunderstorm is active and reaching this block. */
    THUNDERING((l, p) -> l.isThundering() && l.isRainingAt(p)),
    /** Overworld daytime (day-time in {@code [0, 12000)}). */
    DAY((l, p) -> isDay(l)),
    /** Overworld night (the complement of {@link #DAY}). */
    NIGHT((l, p) -> !isDay(l)),
    /** No sky access and below sea level — a cave/mine, not merely indoors. */
    UNDERGROUND((l, p) -> !l.canSeeSky(p) && p.getY() < l.getSeaLevel()),
    /** Open to the sky at this position. */
    EXPOSED_TO_SKY((l, p) -> l.canSeeSky(p)),
    /** A hot biome (desert, savanna, nether-like): base temperature ≥ 1.0. */
    HOT((l, p) -> l.getBiome(p).value().getBaseTemperature() >= 1.0f),
    /** A freezing biome (where it snows): base temperature ≤ 0.15. */
    COLD((l, p) -> l.getBiome(p).value().getBaseTemperature() <= 0.15f);

    private final BiPredicate<Level, BlockPos> predicate;

    ContextCondition(BiPredicate<Level, BlockPos> predicate) {
        this.predicate = predicate;
    }

    /** Whether this condition holds for {@code pos} in {@code level}. */
    public boolean test(Level level, BlockPos pos) {
        return predicate.test(level, pos);
    }

    /** Resolves the JSON {@code when} string (case-insensitive), or empty if unknown. */
    public static Optional<ContextCondition> fromId(String id) {
        if (id == null) return Optional.empty();
        try {
            return Optional.of(valueOf(id.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private static boolean isDay(Level level) {
        long t = level.getDayTime() % 24000L;
        if (t < 0) t += 24000L;
        return t < 12000L;
    }
}
