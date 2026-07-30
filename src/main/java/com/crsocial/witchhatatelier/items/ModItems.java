package com.crsocial.witchhatatelier.items;

import com.crsocial.witchhatatelier.WitchHatAtelierMod;
import com.crsocial.witchhatatelier.blocks.ModBlocks;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SignItem;
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

    // ── Blocks ───────────────────────────────────────────────────────────────────

    public static final DeferredItem<BlockItem> CANVAS_PLATE = ITEMS.register("canvas_plate",
            () -> new BlockItem(ModBlocks.CANVAS_PLATE.get(), new Item.Properties()));

    // ── Silver wood ──────────────────────────────────────────────────────────────

    public static final DeferredItem<BlockItem> SILVER_WOOD_LOG = ITEMS.register("silver_wood_log",
            () -> new BlockItem(ModBlocks.SILVER_WOOD_LOG.get(), new Item.Properties()));

    public static final DeferredItem<BlockItem> SILVER_WOOD_WOOD = ITEMS.register("silver_wood_wood",
            () -> new BlockItem(ModBlocks.SILVER_WOOD_WOOD.get(), new Item.Properties()));

    public static final DeferredItem<BlockItem> STRIPPED_SILVER_LOG = ITEMS.register("stripped_silver_log",
            () -> new BlockItem(ModBlocks.STRIPPED_SILVER_LOG.get(), new Item.Properties()));

    public static final DeferredItem<BlockItem> STRIPPED_SILVER_WOOD = ITEMS.register("stripped_silver_wood",
            () -> new BlockItem(ModBlocks.STRIPPED_SILVER_WOOD.get(), new Item.Properties()));

    public static final DeferredItem<BlockItem> SILVER_WOOD_PLANKS = ITEMS.register("silver_wood_planks",
            () -> new BlockItem(ModBlocks.SILVER_WOOD_PLANKS.get(), new Item.Properties()));

    public static final DeferredItem<BlockItem> SILVER_WOOD_STAIRS = ITEMS.register("silver_wood_stairs",
            () -> new BlockItem(ModBlocks.SILVER_WOOD_STAIRS.get(), new Item.Properties()));

    public static final DeferredItem<BlockItem> SILVER_WOOD_SLAB = ITEMS.register("silver_wood_slab",
            () -> new BlockItem(ModBlocks.SILVER_WOOD_SLAB.get(), new Item.Properties()));

    public static final DeferredItem<BlockItem> SILVER_WOOD_FENCE = ITEMS.register("silver_wood_fence",
            () -> new BlockItem(ModBlocks.SILVER_WOOD_FENCE.get(), new Item.Properties()));

    public static final DeferredItem<BlockItem> SILVER_WOOD_FENCE_GATE = ITEMS.register("silver_wood_fence_gate",
            () -> new BlockItem(ModBlocks.SILVER_WOOD_FENCE_GATE.get(), new Item.Properties()));

    public static final DeferredItem<BlockItem> SILVER_WOOD_LEAVES = ITEMS.register("silver_wood_leaves",
            () -> new BlockItem(ModBlocks.SILVER_WOOD_LEAVES.get(), new Item.Properties()));

    public static final DeferredItem<BlockItem> SILVER_WOOD_VINES = ITEMS.register("silver_wood_vines",
            () -> new BlockItem(ModBlocks.SILVER_WOOD_VINES.get(), new Item.Properties()));

    public static final DeferredItem<BlockItem> BUDDING_SILVER_WOOD = ITEMS.register("budding_silver_wood",
            () -> new BlockItem(ModBlocks.BUDDING_SILVER_WOOD.get(), new Item.Properties()));

    public static final DeferredItem<BlockItem> SILVER_TREE_BRANCH_SMALL = ITEMS.register("silver_tree_branch_small",
            () -> new BlockItem(ModBlocks.SILVER_TREE_BRANCH_SMALL.get(), new Item.Properties()));

    public static final DeferredItem<BlockItem> SILVER_TREE_BRANCH_MEDIUM = ITEMS.register("silver_tree_branch_medium",
            () -> new BlockItem(ModBlocks.SILVER_TREE_BRANCH_MEDIUM.get(), new Item.Properties()));

    public static final DeferredItem<BlockItem> SILVER_TREE_BRANCH_LARGE = ITEMS.register("silver_tree_branch_large",
            () -> new BlockItem(ModBlocks.SILVER_TREE_BRANCH_LARGE.get(), new Item.Properties()));

    public static final DeferredItem<BlockItem> SILVER_TREE_BRANCH = ITEMS.register("silver_tree_branch",
            () -> new BlockItem(ModBlocks.SILVER_TREE_BRANCH.get(), new Item.Properties()));

    public static final DeferredItem<Item> SILVER_WOOD_SEED = ITEMS.register("silver_wood_seed",
            () -> new Item(new Item.Properties()));

    public static final DeferredItem<Item> SILVER_WOOD_BRANCH = ITEMS.register("silver_wood_branch",
            () -> new Item(new Item.Properties()));

    public static final DeferredItem<BlockItem> SILVER_WOOD_PLATE = ITEMS.register("silver_wood_plate",
            () -> new BlockItem(ModBlocks.SILVER_WOOD_PLATE.get(), new Item.Properties()));

    public static final DeferredItem<BlockItem> SILVER_WOOD_BUTTON = ITEMS.register("silver_wood_button",
            () -> new BlockItem(ModBlocks.SILVER_WOOD_BUTTON.get(), new Item.Properties()));

    public static final DeferredItem<SignItem> SILVER_WOOD_SIGN = ITEMS.register("silver_wood_sign",
            () -> new SignItem(new Item.Properties().stacksTo(16),
                    ModBlocks.SILVER_WOOD_SIGN.get(), ModBlocks.SILVER_WOOD_WALL_SIGN.get()));

    public static final DeferredItem<SilverWoodBoatItem> SILVER_WOOD_BOAT = ITEMS.register("silver_wood_boat",
            () -> new SilverWoodBoatItem(false, new Item.Properties().stacksTo(1)));

    public static final DeferredItem<SilverWoodBoatItem> SILVER_WOOD_CHEST_BOAT = ITEMS.register("silver_wood_chest_boat",
            () -> new SilverWoodBoatItem(true, new Item.Properties().stacksTo(1)));

    // ── Blank papers (stackable, placeable) ──────────────────────────────────────

    public static final DeferredItem<Item> SMALL_SQUARE_PAPER = ITEMS.register("small_square_paper",
            () -> new SpellPaperItem(new Item.Properties().stacksTo(16), PaperType.SMALL_SQUARE, true));

    public static final DeferredItem<Item> MEDIUM_SQUARE_PAPER = ITEMS.register("medium_square_paper",
            () -> new SpellPaperItem(new Item.Properties().stacksTo(16), PaperType.MEDIUM_SQUARE, true));

    public static final DeferredItem<Item> SMALL_ROUND_PAPER = ITEMS.register("small_round_paper",
            () -> new SpellPaperItem(new Item.Properties().stacksTo(16), PaperType.SMALL_ROUND, true));

    public static final DeferredItem<Item> MEDIUM_ROUND_PAPER = ITEMS.register("medium_round_paper",
            () -> new SpellPaperItem(new Item.Properties().stacksTo(16), PaperType.MEDIUM_ROUND, true));

    // ── Inscribed spell papers (non-stackable, carry gesture NBT) ────────────────

    public static final DeferredItem<Item> SMALL_SQUARE_SPELL_PAPER = ITEMS.register("small_square_spell_paper",
            () -> new SpellPaperItem(new Item.Properties().stacksTo(1), PaperType.SMALL_SQUARE, false));

    public static final DeferredItem<Item> MEDIUM_SQUARE_SPELL_PAPER = ITEMS.register("medium_square_spell_paper",
            () -> new SpellPaperItem(new Item.Properties().stacksTo(1), PaperType.MEDIUM_SQUARE, false));

    public static final DeferredItem<Item> SMALL_ROUND_SPELL_PAPER = ITEMS.register("small_round_spell_paper",
            () -> new SpellPaperItem(new Item.Properties().stacksTo(1), PaperType.SMALL_ROUND, false));

    public static final DeferredItem<Item> MEDIUM_ROUND_SPELL_PAPER = ITEMS.register("medium_round_spell_paper",
            () -> new SpellPaperItem(new Item.Properties().stacksTo(1), PaperType.MEDIUM_ROUND, false));

    // ── PaperType → item lookup maps ─────────────────────────────────────────────

    private static final EnumMap<PaperType, DeferredItem<Item>> BLANK_MAP   = new EnumMap<>(PaperType.class);
    private static final EnumMap<PaperType, DeferredItem<Item>> INSCRIBED_MAP = new EnumMap<>(PaperType.class);

    static {
        BLANK_MAP.put(PaperType.SMALL_SQUARE,  SMALL_SQUARE_PAPER);
        BLANK_MAP.put(PaperType.MEDIUM_SQUARE, MEDIUM_SQUARE_PAPER);
        BLANK_MAP.put(PaperType.SMALL_ROUND,   SMALL_ROUND_PAPER);
        BLANK_MAP.put(PaperType.MEDIUM_ROUND,  MEDIUM_ROUND_PAPER);

        INSCRIBED_MAP.put(PaperType.SMALL_SQUARE,  SMALL_SQUARE_SPELL_PAPER);
        INSCRIBED_MAP.put(PaperType.MEDIUM_SQUARE, MEDIUM_SQUARE_SPELL_PAPER);
        INSCRIBED_MAP.put(PaperType.SMALL_ROUND,   SMALL_ROUND_SPELL_PAPER);
        INSCRIBED_MAP.put(PaperType.MEDIUM_ROUND,  MEDIUM_ROUND_SPELL_PAPER);
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
