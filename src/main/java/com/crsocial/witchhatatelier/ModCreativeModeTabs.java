package com.crsocial.witchhatatelier;

import com.crsocial.witchhatatelier.items.ModItems;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModCreativeModeTabs {

    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, com.crsocial.witchhatatelier.WitchHatAtelierMod.MODID);

    public static void register(IEventBus modEventBus) {
        CREATIVE_MODE_TABS.register(modEventBus);
    }

    @SuppressWarnings("unused")
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> WITCH_HAT_ATELIER_TAB = CREATIVE_MODE_TABS.register("witch_hat_atelier_tab",
            () -> CreativeModeTab.builder()
                    .icon(() -> new ItemStack(ModItems.NIB.get()))
                    .title(Component.translatable("creativetab.witchhatatelier.tab"))
                    .displayItems(ModCreativeModeTabs::addItemTabItems)
                    .build()
    );

    @SuppressWarnings("unused")
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> WITCH_HAT_ATELIER_BUILDING_BLOCKS_TAB = CREATIVE_MODE_TABS.register("witch_hat_atelier_building_blocks_tab",
            () -> CreativeModeTab.builder()
                    .icon(() -> new ItemStack(ModItems.SILVER_WOOD_PLANKS.get()))
                    .title(Component.translatable("creativetab.witchhatatelier.building_blocks_tab"))
                    .displayItems(ModCreativeModeTabs::addBuildingBlockTabItems)
                    .build()
    );

    private static void addItemTabItems(CreativeModeTab.ItemDisplayParameters ignored,
                                    CreativeModeTab.Output output) {
        output.accept(ModItems.WAND);
        output.accept(ModItems.NIB);
        output.accept(ModItems.SPELL_BINDER);
        // Blank papers
        output.accept(ModItems.SMALL_SQUARE_PAPER);
        output.accept(ModItems.MEDIUM_SQUARE_PAPER);
        output.accept(ModItems.SMALL_ROUND_PAPER);
        output.accept(ModItems.MEDIUM_ROUND_PAPER);
        // Silver wood non-block items
        output.accept(ModItems.SILVER_WOOD_SEED);
        output.accept(ModItems.SILVER_WOOD_BRANCH);
    }

    private static void addBuildingBlockTabItems(CreativeModeTab.ItemDisplayParameters ignored,
                                    CreativeModeTab.Output output) {
        output.accept(ModItems.CANVAS_PLATE);
        // Silver wood
        output.accept(ModItems.SILVER_WOOD_LOG);
        output.accept(ModItems.SILVER_WOOD_WOOD);
        output.accept(ModItems.STRIPPED_SILVER_LOG);
        output.accept(ModItems.STRIPPED_SILVER_WOOD);
        output.accept(ModItems.SILVER_WOOD_PLANKS);
        output.accept(ModItems.SILVER_WOOD_STAIRS);
        output.accept(ModItems.SILVER_WOOD_SLAB);
        output.accept(ModItems.SILVER_WOOD_FENCE);
        output.accept(ModItems.SILVER_WOOD_FENCE_GATE);
        output.accept(ModItems.SILVER_WOOD_PLATE);
        output.accept(ModItems.SILVER_WOOD_BUTTON);
        output.accept(ModItems.SILVER_WOOD_SIGN);
        output.accept(ModItems.SILVER_WOOD_BOAT);
        output.accept(ModItems.SILVER_WOOD_CHEST_BOAT);
        output.accept(ModItems.SILVER_WOOD_LEAVES);
        output.accept(ModItems.SILVER_WOOD_VINES);
        output.accept(ModItems.BUDDING_SILVER_WOOD);
        output.accept(ModItems.SILVER_TREE_BRANCH_SMALL);
        output.accept(ModItems.SILVER_TREE_BRANCH_MEDIUM);
        output.accept(ModItems.SILVER_TREE_BRANCH_LARGE);
        output.accept(ModItems.SILVER_TREE_BRANCH);
    }
}
