package com.crsocial.witchhatatelier.items;

import com.crsocial.witchhatatelier.blocks.ModBlocks;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(com.crsocial.witchhatatelier.WitchHatAtelierMod.MODID);

    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
    }

    public static final DeferredItem<Item> WAND = ITEMS.register("wand", () -> new Wand(new Item.Properties()
            .stacksTo(1)
            .durability(500)));
    public static final DeferredItem<Item> NIB = ITEMS.register("nib", () -> new Item(new Item.Properties()
            .stacksTo(16)
    ));

    public static final DeferredItem<Item> ROUND_PAPER = ITEMS.register("round_paper", () -> new SpellPaperItem(new Item.Properties()
            .stacksTo(16), true
    ));

    public static final DeferredItem<Item> SPELL_PAPER = ITEMS.register("spell_paper", () -> new SpellPaperItem(new Item.Properties()
            .stacksTo(1), false
    ));
    public static final DeferredItem<Item> ROUND_SPELL_PAPER = ITEMS.register("round_spell_paper", () -> new SpellPaperItem(new Item.Properties()
            .stacksTo(1), true
    ));
    public static final DeferredItem<Item> SPELL_BINDER = ITEMS.register("spell_binder", () -> new Item(new Item.Properties()
            .stacksTo(1)
    ));

    public static final DeferredItem<BlockItem> PLACED_PAPER = ITEMS.register("placed_paper",
            () -> new BlockItem(ModBlocks.PLACED_PAPER.get(), new Item.Properties()));
}
