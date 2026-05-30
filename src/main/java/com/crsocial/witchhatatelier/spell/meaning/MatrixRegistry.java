package com.crsocial.witchhatatelier.spell.meaning;

import com.crsocial.witchhatatelier.spell.compiler.SigilType;
import com.crsocial.witchhatatelier.spell.compiler.SignType;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

/**
 * Server-side singleton holding every loaded {@link MatrixEntry}, keyed by
 * {@code (sigil, sign)}. Cleared and rebuilt on every datapack reload by
 * {@link MatrixLoader}.
 */
public final class MatrixRegistry {

    private static final MatrixRegistry INSTANCE = new MatrixRegistry();

    private final Map<SigilType, EnumMap<SignType, MatrixEntry>> cells = new EnumMap<>(SigilType.class);

    private MatrixRegistry() {}

    public static MatrixRegistry get() {
        return INSTANCE;
    }

    public synchronized void clear() {
        cells.clear();
    }

    public synchronized void register(MatrixEntry entry) {
        cells.computeIfAbsent(entry.sigil(), k -> new EnumMap<>(SignType.class))
                .put(entry.sign(), entry);
    }

    /** Returns the matrix cell for this {@code (sigil, sign)} pair, or empty if no JSON exists. */
    public synchronized Optional<MatrixEntry> find(SigilType sigil, SignType sign) {
        EnumMap<SignType, MatrixEntry> row = cells.get(sigil);
        if (row == null) return Optional.empty();
        return Optional.ofNullable(row.get(sign));
    }

    /** Total number of loaded cells across all sigils. */
    public synchronized int size() {
        int n = 0;
        for (EnumMap<SignType, MatrixEntry> row : cells.values()) n += row.size();
        return n;
    }
}
