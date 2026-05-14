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
                    .displayItems(ModCreativeModeTabs::addTabItems)
                    .build()
    );

    private static void addTabItems(CreativeModeTab.ItemDisplayParameters ignored,
                                    CreativeModeTab.Output output) {
        output.accept(ModItems.WAND);
        output.accept(ModItems.NIB);
        output.accept(ModItems.SPELL_BINDER);
        // Blank papers
        output.accept(ModItems.SMALL_SQUARE_PAPER);
        output.accept(ModItems.MEDIUM_SQUARE_PAPER);
        output.accept(ModItems.LARGE_SQUARE_PAPER);
        output.accept(ModItems.SMALL_ROUND_PAPER);
        output.accept(ModItems.MEDIUM_ROUND_PAPER);
        output.accept(ModItems.LARGE_ROUND_PAPER);
    }
}
