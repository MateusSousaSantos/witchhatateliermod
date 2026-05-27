package com.crsocial.witchhatatelier.spell.compiler;

import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * Result of {@link SpellGraphBuilder#build}: either a successfully compiled
 * {@link SpellGraph} or a human-readable rejection reason.
 */
public record CompileResult(Optional<SpellGraph> graph, @Nullable String rejectionReason) {

    public static CompileResult success(SpellGraph g) {
        return new CompileResult(Optional.of(g), null);
    }

    public static CompileResult rejected(String reason) {
        return new CompileResult(Optional.empty(), reason);
    }

    public boolean isSuccess() {
        return graph.isPresent();
    }
}
