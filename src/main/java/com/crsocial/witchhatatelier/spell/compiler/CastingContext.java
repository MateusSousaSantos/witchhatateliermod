package com.crsocial.witchhatatelier.spell.compiler;

import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

/**
 * Per-cast environment threaded through the compiler and (later) the meaning
 * engine. The reserved {@link #ink} and {@link #wand} slots are {@code null}
 * today; future systems populate them without an API change. See
 * {@code docs/magic_system/02_spell_compiler.md}.
 */
public record CastingContext(@Nullable InkId ink,
                             @Nullable WandId wand,
                             MediumKind medium,
                             Vector3f originWorld,
                             Vector3f surfaceNormal) {

    /** Where the inscription lives — affects origin/direction, never spell content. */
    public enum MediumKind { PAPER_ITEM, PLACED_PAPER, INSCRIBED_BLOCK }

    /** Reserved — the ink system is not implemented yet. */
    public record InkId(String id) {}

    /** Reserved — the wand system is not implemented yet. */
    public record WandId(String id) {}

    /** Builds a context with the reserved ink/wand slots left null. */
    public static CastingContext of(MediumKind medium, Vector3f originWorld, Vector3f surfaceNormal) {
        return new CastingContext(null, null, medium, originWorld, surfaceNormal);
    }
}
