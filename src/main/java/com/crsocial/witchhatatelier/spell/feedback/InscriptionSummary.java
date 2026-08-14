package com.crsocial.witchhatatelier.spell.feedback;

import com.crsocial.witchhatatelier.spell.compiler.CompileResult;
import com.crsocial.witchhatatelier.spell.compiler.EffectBundle;
import com.crsocial.witchhatatelier.spell.compiler.EffectType;
import com.crsocial.witchhatatelier.spell.compiler.ElementType;
import com.crsocial.witchhatatelier.spell.compiler.FormBundle;
import com.crsocial.witchhatatelier.spell.compiler.FormType;
import com.crsocial.witchhatatelier.spell.compiler.SpellGraph;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * What the spell pipeline concluded about an inscription, stamped onto the
 * inscribed paper item (under its {@code CUSTOM_DATA} root) and the
 * {@code PlacedPaperBlockEntity} after every save — including ring-less saves,
 * where nothing executes. This is the single source the item tooltip, the
 * action-bar line, and the canvas status header read from, so the player can
 * always tell what a paper holds without casting it.
 *
 * @param state                 what the drawing resolved to (see {@link InscriptionState})
 * @param element               the core element, or {@code null} when no graph compiled
 * @param forms                 form occurrences in draw order (empty when none / no graph)
 * @param effects               effect occurrences in draw order (empty when none / no graph)
 * @param convergence           whether a Convergence glyph was drawn
 * @param quality               recognizer confidence of the core element in [0, 1], or −1 when
 *                              no graph compiled (no quality line is shown)
 * @param unrecognizedStrokeIds content stroke ids the recognizer could not read (red-tint review)
 */
public record InscriptionSummary(InscriptionState state,
                                 @Nullable ElementType element,
                                 List<FormEntry> forms,
                                 List<EffectEntry> effects,
                                 boolean convergence,
                                 float quality,
                                 int[] unrecognizedStrokeIds) {

    /** Outcome class of a saved drawing, orthogonal to the {@code spent} flag. */
    public enum InscriptionState {
        /** Compiled into a legible, structured glyph graph. */
        READY,
        /** Glyphs were recognized but don't form a structurally valid graph. */
        FIZZLE,
        /** Nothing legible on the paper. */
        ILLEGIBLE
    }

    /** One form type and how many times it was drawn. */
    public record FormEntry(FormType form, int count) {}

    /** One effect type and how many times it was drawn. */
    public record EffectEntry(EffectType effect, int count) {}

    /** NBT key of the summary sub-compound on the item root tag / BE tag. */
    public static final String NBT_KEY = "inscription";

    // ── Construction ─────────────────────────────────────────────────────────────

    /** Derives the summary from a pipeline pass (ring-closed or ring-less alike). */
    public static InscriptionSummary of(CompileResult result,
                                        Map<String, Integer> recogCounts, int[] unrecognizedIds) {
        if (result.isSuccess()) {
            SpellGraph graph = result.graph().get();
            List<FormEntry> forms = new ArrayList<>();
            for (FormBundle b : graph.formsByType()) {
                forms.add(new FormEntry(b.type(), b.count()));
            }
            List<EffectEntry> effects = new ArrayList<>();
            for (EffectBundle b : graph.effectsByType()) {
                effects.add(new EffectEntry(b.type(), b.count()));
            }
            return new InscriptionSummary(
                    InscriptionState.READY,
                    graph.core().type(), List.copyOf(forms), List.copyOf(effects), graph.convergence(),
                    graph.core().quality(), unrecognizedIds);
        }
        return new InscriptionSummary(
                recogCounts.isEmpty() ? InscriptionState.ILLEGIBLE : InscriptionState.FIZZLE,
                null, List.of(), List.of(), false, -1f, unrecognizedIds);
    }

    /** Summary for a save with no readable content at all. */
    public static InscriptionSummary illegible() {
        return new InscriptionSummary(InscriptionState.ILLEGIBLE, null, List.of(), List.of(), false, -1f, new int[0]);
    }

    // ── NBT round-trip ───────────────────────────────────────────────────────────

    public CompoundTag toNbt() {
        CompoundTag tag = new CompoundTag();
        tag.putString("state", state.name());
        if (element != null) tag.putString("element", element.name());
        if (!forms.isEmpty()) {
            ListTag list = new ListTag();
            for (FormEntry e : forms) {
                CompoundTag s = new CompoundTag();
                s.putString("form", e.form().name());
                s.putInt("count", e.count());
                list.add(s);
            }
            tag.put("forms", list);
        }
        if (!effects.isEmpty()) {
            ListTag list = new ListTag();
            for (EffectEntry e : effects) {
                CompoundTag s = new CompoundTag();
                s.putString("effect", e.effect().name());
                s.putInt("count", e.count());
                list.add(s);
            }
            tag.put("effects", list);
        }
        tag.putBoolean("convergence", convergence);
        tag.putFloat("quality", quality);
        tag.putIntArray("unrecognized", unrecognizedStrokeIds);
        return tag;
    }

    /** Reads the summary from a root tag holding an {@value #NBT_KEY} sub-compound. */
    public static Optional<InscriptionSummary> fromNbt(@Nullable CompoundTag root) {
        if (root == null || !root.contains(NBT_KEY, Tag.TAG_COMPOUND)) return Optional.empty();
        return fromNbtCompound(root.getCompound(NBT_KEY));
    }

    /** Reads the summary directly from its own compound (the BE stores it unwrapped). */
    public static Optional<InscriptionSummary> fromNbtCompound(CompoundTag tag) {
        InscriptionState state;
        try {
            state = InscriptionState.valueOf(tag.getString("state"));
        } catch (IllegalArgumentException unknown) {
            return Optional.empty(); // forward-safe: a state from a newer version reads as absent
        }
        ElementType element = null;
        if (tag.contains("element", Tag.TAG_STRING)) {
            try {
                element = ElementType.valueOf(tag.getString("element"));
            } catch (IllegalArgumentException ignored) { /* unknown element → omitted */ }
        }
        List<FormEntry> forms = new ArrayList<>();
        for (Tag t : tag.getList("forms", Tag.TAG_COMPOUND)) {
            CompoundTag s = (CompoundTag) t;
            try {
                forms.add(new FormEntry(FormType.valueOf(s.getString("form")), s.getInt("count")));
            } catch (IllegalArgumentException ignored) { /* unknown form → skipped */ }
        }
        List<EffectEntry> effects = new ArrayList<>();
        for (Tag t : tag.getList("effects", Tag.TAG_COMPOUND)) {
            CompoundTag s = (CompoundTag) t;
            try {
                effects.add(new EffectEntry(EffectType.valueOf(s.getString("effect")), s.getInt("count")));
            } catch (IllegalArgumentException ignored) { /* unknown effect → skipped */ }
        }
        boolean convergence = tag.getBoolean("convergence");
        return Optional.of(new InscriptionSummary(state, element, List.copyOf(forms), List.copyOf(effects),
                convergence, tag.getFloat("quality"), tag.getIntArray("unrecognized")));
    }

    // ── Display (translatable; keys live in the lang files) ─────────────────────

    public static MutableComponent elementName(ElementType type) {
        return Component.translatable("sigil.witchhatateliermod." + type.name().toLowerCase(Locale.ROOT));
    }

    public static MutableComponent formName(FormType type) {
        return Component.translatable("sign.witchhatateliermod." + type.name().toLowerCase(Locale.ROOT));
    }

    public static MutableComponent effectName(EffectType type) {
        return Component.translatable("sign.witchhatateliermod." + type.name().toLowerCase(Locale.ROOT));
    }

    /**
     * Full composition line, e.g. {@code "Fire — Column ×2 + Levitation"}; just the
     * element name when nothing else was drawn; empty when no graph compiled.
     */
    public Optional<MutableComponent> composition() {
        if (element == null) return Optional.empty();
        MutableComponent elementComponent = elementName(element).withStyle(ChatFormatting.YELLOW);
        if (forms.isEmpty() && effects.isEmpty() && !convergence) return Optional.of(elementComponent);
        return Optional.of(Component.translatable("inscription.witchhatateliermod.composition",
                elementComponent, glyphList(true)));
    }

    /** Compact composition for the action bar, e.g. {@code "Fire Column"}. */
    public Optional<MutableComponent> compositionCompact() {
        if (element == null) return Optional.empty();
        MutableComponent elementComponent = elementName(element).withStyle(ChatFormatting.YELLOW);
        if (forms.isEmpty() && effects.isEmpty() && !convergence) return Optional.of(elementComponent);
        return Optional.of(Component.translatable("inscription.witchhatateliermod.composition.compact",
                elementComponent, glyphList(false)));
    }

    private MutableComponent glyphList(boolean withCounts) {
        MutableComponent list = Component.empty();
        boolean first = true;
        if (convergence) {
            list.append(Component.translatable("sign.witchhatateliermod.convergence").withStyle(ChatFormatting.LIGHT_PURPLE));
            first = false;
        }
        for (FormEntry e : forms) {
            if (!first) list.append(Component.literal(" + ").withStyle(ChatFormatting.GRAY));
            list.append(glyphComponent(formName(e.form()), e.count(), withCounts));
            first = false;
        }
        for (EffectEntry e : effects) {
            if (!first) list.append(Component.literal(" + ").withStyle(ChatFormatting.GRAY));
            list.append(glyphComponent(effectName(e.effect()), e.count(), withCounts));
            first = false;
        }
        return list;
    }

    private static MutableComponent glyphComponent(MutableComponent name, int count, boolean withCounts) {
        MutableComponent styled = name.withStyle(ChatFormatting.AQUA);
        if (withCounts && count > 1) {
            return Component.translatable("inscription.witchhatateliermod.sign_count", styled, count)
                    .withStyle(ChatFormatting.AQUA);
        }
        return styled;
    }

    /** The state description line, styled per state. */
    public MutableComponent stateLine() {
        return switch (state) {
            case READY -> Component.translatable("inscription.witchhatateliermod.state.ready")
                    .withStyle(ChatFormatting.GREEN);
            case FIZZLE -> Component.translatable("inscription.witchhatateliermod.state.fizzle")
                    .withStyle(ChatFormatting.RED);
            case ILLEGIBLE -> Component.translatable("inscription.witchhatateliermod.state.illegible")
                    .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC);
        };
    }

    /** {@code "Quality: A"} with the colored grade letter; empty when no graph compiled. */
    public Optional<Component> qualityLine() {
        if (quality < 0f) return Optional.empty();
        return Optional.of(Component.translatable("inscription.witchhatateliermod.quality",
                QualityGrade.fromQuality(quality).letter()).withStyle(ChatFormatting.GRAY));
    }

    /** The colored grade letter; empty when no graph compiled. */
    public Optional<Component> gradeLetter() {
        if (quality < 0f) return Optional.empty();
        return Optional.of(QualityGrade.fromQuality(quality).letter());
    }
}
