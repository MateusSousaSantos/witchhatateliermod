package com.crsocial.witchhatatelier.items;

import com.crsocial.witchhatatelier.WitchHatAtelierMod;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.EnumMap;

public class ModItems {
    public static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(WitchHatAtelierMod.MODID);

    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
    }

    // ── Tools ────────────────────────────────────────────────────────────────────

    public static final DeferredItem<Item> WAND = ITEMS.register("wand",
            () -> new Wand(new Item.Properties().stacksTo(1).durability(500)));

    public static final DeferredItem<Item> NIB = ITEMS.register("nib",
            () -> new Item(new Item.Properties().stacksTo(16)));

    public static final DeferredItem<Item> SPELL_BINDER = ITEMS.register("spell_binder",
            () -> new Item(new Item.Properties().stacksTo(1)));

    // ── Blank papers (stackable, placeable) ──────────────────────────────────────

    public static final DeferredItem<Item> SMALL_SQUARE_PAPER = ITEMS.register("small_square_paper",
            () -> new SpellPaperItem(new Item.Properties().stacksTo(16), PaperType.SMALL_SQUARE, true));

    public static final DeferredItem<Item> MEDIUM_SQUARE_PAPER = ITEMS.register("medium_square_paper",
            () -> new SpellPaperItem(new Item.Properties().stacksTo(16), PaperType.MEDIUM_SQUARE, true));

    public static final DeferredItem<Item> LARGE_SQUARE_PAPER = ITEMS.register("large_square_paper",
            () -> new SpellPaperItem(new Item.Properties().stacksTo(16), PaperType.LARGE_SQUARE, true));

    public static final DeferredItem<Item> SMALL_ROUND_PAPER = ITEMS.register("small_round_paper",
            () -> new SpellPaperItem(new Item.Properties().stacksTo(16), PaperType.SMALL_ROUND, true));

    public static final DeferredItem<Item> MEDIUM_ROUND_PAPER = ITEMS.register("medium_round_paper",
            () -> new SpellPaperItem(new Item.Properties().stacksTo(16), PaperType.MEDIUM_ROUND, true));

    public static final DeferredItem<Item> LARGE_ROUND_PAPER = ITEMS.register("large_round_paper",
            () -> new SpellPaperItem(new Item.Properties().stacksTo(16), PaperType.LARGE_ROUND, true));

    // ── Inscribed spell papers (non-stackable, carry gesture NBT) ────────────────

    public static final DeferredItem<Item> SMALL_SQUARE_SPELL_PAPER = ITEMS.register("small_square_spell_paper",
            () -> new SpellPaperItem(new Item.Properties().stacksTo(1), PaperType.SMALL_SQUARE, false));

    public static final DeferredItem<Item> MEDIUM_SQUARE_SPELL_PAPER = ITEMS.register("medium_square_spell_paper",
            () -> new SpellPaperItem(new Item.Properties().stacksTo(1), PaperType.MEDIUM_SQUARE, false));

    public static final DeferredItem<Item> LARGE_SQUARE_SPELL_PAPER = ITEMS.register("large_square_spell_paper",
            () -> new SpellPaperItem(new Item.Properties().stacksTo(1), PaperType.LARGE_SQUARE, false));

    public static final DeferredItem<Item> SMALL_ROUND_SPELL_PAPER = ITEMS.register("small_round_spell_paper",
            () -> new SpellPaperItem(new Item.Properties().stacksTo(1), PaperType.SMALL_ROUND, false));

    public static final DeferredItem<Item> MEDIUM_ROUND_SPELL_PAPER = ITEMS.register("medium_round_spell_paper",
            () -> new SpellPaperItem(new Item.Properties().stacksTo(1), PaperType.MEDIUM_ROUND, false));

    public static final DeferredItem<Item> LARGE_ROUND_SPELL_PAPER = ITEMS.register("large_round_spell_paper",
            () -> new SpellPaperItem(new Item.Properties().stacksTo(1), PaperType.LARGE_ROUND, false));

    // ── PaperType → item lookup maps ─────────────────────────────────────────────

    private static final EnumMap<PaperType, DeferredItem<Item>> BLANK_MAP   = new EnumMap<>(PaperType.class);
    private static final EnumMap<PaperType, DeferredItem<Item>> INSCRIBED_MAP = new EnumMap<>(PaperType.class);

    static {
        BLANK_MAP.put(PaperType.SMALL_SQUARE,  SMALL_SQUARE_PAPER);
        BLANK_MAP.put(PaperType.MEDIUM_SQUARE, MEDIUM_SQUARE_PAPER);
        BLANK_MAP.put(PaperType.LARGE_SQUARE,  LARGE_SQUARE_PAPER);
        BLANK_MAP.put(PaperType.SMALL_ROUND,   SMALL_ROUND_PAPER);
        BLANK_MAP.put(PaperType.MEDIUM_ROUND,  MEDIUM_ROUND_PAPER);
        BLANK_MAP.put(PaperType.LARGE_ROUND,   LARGE_ROUND_PAPER);

        INSCRIBED_MAP.put(PaperType.SMALL_SQUARE,  SMALL_SQUARE_SPELL_PAPER);
        INSCRIBED_MAP.put(PaperType.MEDIUM_SQUARE, MEDIUM_SQUARE_SPELL_PAPER);
        INSCRIBED_MAP.put(PaperType.LARGE_SQUARE,  LARGE_SQUARE_SPELL_PAPER);
        INSCRIBED_MAP.put(PaperType.SMALL_ROUND,   SMALL_ROUND_SPELL_PAPER);
        INSCRIBED_MAP.put(PaperType.MEDIUM_ROUND,  MEDIUM_ROUND_SPELL_PAPER);
        INSCRIBED_MAP.put(PaperType.LARGE_ROUND,   LARGE_ROUND_SPELL_PAPER);
    }

    /** Returns the blank paper {@link DeferredItem} for the given {@link PaperType}. */
    public static DeferredItem<Item> blankFor(PaperType type) {
        return BLANK_MAP.get(type);
    }

    /** Returns the inscribed spell-paper {@link DeferredItem} for the given {@link PaperType}. */
    public static DeferredItem<Item> inscribedFor(PaperType type) {
        return INSCRIBED_MAP.get(type);
    }
}
