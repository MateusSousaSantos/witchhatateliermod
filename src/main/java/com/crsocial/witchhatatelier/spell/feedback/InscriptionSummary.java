package com.crsocial.witchhatatelier.spell.feedback;

import com.crsocial.witchhatatelier.spell.compiler.CompileResult;
import com.crsocial.witchhatatelier.spell.compiler.SigilType;
import com.crsocial.witchhatatelier.spell.compiler.SignBundle;
import com.crsocial.witchhatatelier.spell.compiler.SignType;
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
 * @param element               the core sigil's element, or {@code null} when no graph compiled
 * @param signs                 sign occurrences in draw order (empty when none / no graph)
 * @param quality               recognizer confidence of the core sigil in [0, 1], or −1 when
 *                              no graph compiled (no quality line is shown)
 * @param unrecognizedStrokeIds content stroke ids the recognizer could not read (red-tint review)
 */
public record InscriptionSummary(InscriptionState state,
                                 @Nullable SigilType element,
                                 List<SignEntry> signs,
                                 float quality,
                                 int[] unrecognizedStrokeIds) {

    /** Outcome class of a saved drawing, orthogonal to the {@code spent} flag. */
    public enum InscriptionState {
        /** Compiled and a matrix cell exists — will cast when the ring closes. */
        READY,
        /** Compiled, but no matrix cell is wired for the combination. */
        INERT,
        /** Glyphs were recognized but don't form a castable spell. */
        FIZZLE,
        /** Nothing legible on the paper. */
        ILLEGIBLE
    }

    /** One sign type and how many times it was drawn. */
    public record SignEntry(SignType sign, int count) {}

    /** NBT key of the summary sub-compound on the item root tag / BE tag. */
    public static final String NBT_KEY = "inscription";

    // ── Construction ─────────────────────────────────────────────────────────────

    /** Derives the summary from a pipeline pass (ring-closed or ring-less alike). */
    public static InscriptionSummary of(CompileResult result, boolean hasMatrixCell,
                                        Map<String, Integer> recogCounts, int[] unrecognizedIds) {
        if (result.isSuccess()) {
            SpellGraph graph = result.graph().get();
            List<SignEntry> signs = new ArrayList<>();
            for (SignBundle b : graph.signsByType()) {
                signs.add(new SignEntry(b.type(), b.count()));
            }
            return new InscriptionSummary(
                    hasMatrixCell ? InscriptionState.READY : InscriptionState.INERT,
                    graph.core().type(), List.copyOf(signs), graph.core().quality(),
                    unrecognizedIds);
        }
        return new InscriptionSummary(
                recogCounts.isEmpty() ? InscriptionState.ILLEGIBLE : InscriptionState.FIZZLE,
                null, List.of(), -1f, unrecognizedIds);
    }

    /** Summary for a save with no readable content at all. */
    public static InscriptionSummary illegible() {
        return new InscriptionSummary(InscriptionState.ILLEGIBLE, null, List.of(), -1f, new int[0]);
    }

    // ── NBT round-trip ───────────────────────────────────────────────────────────

    public CompoundTag toNbt() {
        CompoundTag tag = new CompoundTag();
        tag.putString("state", state.name());
        if (element != null) tag.putString("element", element.name());
        if (!signs.isEmpty()) {
            ListTag list = new ListTag();
            for (SignEntry e : signs) {
                CompoundTag s = new CompoundTag();
                s.putString("sign", e.sign().name());
                s.putInt("count", e.count());
                list.add(s);
            }
            tag.put("signs", list);
        }
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
        SigilType element = null;
        if (tag.contains("element", Tag.TAG_STRING)) {
            try {
                element = SigilType.valueOf(tag.getString("element"));
            } catch (IllegalArgumentException ignored) { /* unknown element → omitted */ }
        }
        List<SignEntry> signs = new ArrayList<>();
        for (Tag t : tag.getList("signs", Tag.TAG_COMPOUND)) {
            CompoundTag s = (CompoundTag) t;
            try {
                signs.add(new SignEntry(SignType.valueOf(s.getString("sign")), s.getInt("count")));
            } catch (IllegalArgumentException ignored) { /* unknown sign → skipped */ }
        }
        return Optional.of(new InscriptionSummary(state, element, List.copyOf(signs),
                tag.getFloat("quality"), tag.getIntArray("unrecognized")));
    }

    // ── Display (translatable; keys live in the lang files) ─────────────────────

    public static MutableComponent sigilName(SigilType type) {
        return Component.translatable("sigil.witchhatateliermod." + type.name().toLowerCase(Locale.ROOT));
    }

    public static MutableComponent signName(SignType type) {
        return Component.translatable("sign.witchhatateliermod." + type.name().toLowerCase(Locale.ROOT));
    }

    /**
     * Full composition line, e.g. {@code "Fire — Column ×2 + Dispersion"}; just the
     * element name when no signs were drawn; empty when no graph compiled.
     */
    public Optional<MutableComponent> composition() {
        if (element == null) return Optional.empty();
        MutableComponent elementName = sigilName(element).withStyle(ChatFormatting.YELLOW);
        if (signs.isEmpty()) return Optional.of(elementName);
        return Optional.of(Component.translatable("inscription.witchhatateliermod.composition",
                elementName, signList(true)));
    }

    /** Compact composition for the action bar, e.g. {@code "Fire Column"}. */
    public Optional<MutableComponent> compositionCompact() {
        if (element == null) return Optional.empty();
        MutableComponent elementName = sigilName(element).withStyle(ChatFormatting.YELLOW);
        if (signs.isEmpty()) return Optional.of(elementName);
        return Optional.of(Component.translatable("inscription.witchhatateliermod.composition.compact",
                elementName, signList(false)));
    }

    private MutableComponent signList(boolean withCounts) {
        MutableComponent list = Component.empty();
        boolean first = true;
        for (SignEntry e : signs) {
            if (!first) list.append(Component.literal(" + ").withStyle(ChatFormatting.GRAY));
            MutableComponent name = signName(e.sign()).withStyle(ChatFormatting.AQUA);
            if (withCounts && e.count() > 1) {
                list.append(Component.translatable("inscription.witchhatateliermod.sign_count",
                        name, e.count()).withStyle(ChatFormatting.AQUA));
            } else {
                list.append(name);
            }
            first = false;
        }
        return list;
    }

    /** The state description line, styled per state. */
    public MutableComponent stateLine() {
        return switch (state) {
            case READY -> Component.translatable("inscription.witchhatateliermod.state.ready")
                    .withStyle(ChatFormatting.GREEN);
            case INERT -> Component.translatable("inscription.witchhatateliermod.state.inert")
                    .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC);
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
