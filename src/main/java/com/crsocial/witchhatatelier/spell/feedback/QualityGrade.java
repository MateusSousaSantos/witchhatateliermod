package com.crsocial.witchhatatelier.spell.feedback;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/**
 * Letter grade shown to the player for a drawing's quality — the recognizer
 * confidence of the central sigil ({@code graph.core().quality()} in [0, 1]).
 *
 * <p>Thresholds are calibrated to the recognizer's de-compressed score scale
 * (pass floor {@code recognitionMinScore} ≈ 0.12, a well-drawn sigil ≈ 0.9),
 * not a naive linear split. Tune only these constants.</p>
 */
public enum QualityGrade {
    S(0.92f, ChatFormatting.GOLD),
    A(0.80f, ChatFormatting.GREEN),
    B(0.65f, ChatFormatting.AQUA),
    C(0.45f, ChatFormatting.YELLOW),
    D(0.25f, ChatFormatting.GRAY),
    F(0.00f, ChatFormatting.DARK_RED);

    private final float minQuality;
    private final ChatFormatting color;

    QualityGrade(float minQuality, ChatFormatting color) {
        this.minQuality = minQuality;
        this.color = color;
    }

    public ChatFormatting color() { return color; }

    /** The grade whose threshold the quality meets, in declaration order (S first). */
    public static QualityGrade fromQuality(float quality) {
        for (QualityGrade g : values()) {
            if (quality >= g.minQuality) return g;
        }
        return F;
    }

    /** The colored grade letter, e.g. a gold {@code S}. */
    public Component letter() {
        return Component.literal(name()).withStyle(color);
    }
}
