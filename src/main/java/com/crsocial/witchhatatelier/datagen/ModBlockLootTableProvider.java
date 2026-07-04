package com.crsocial.witchhatatelier.datagen;


import com.crsocial.witchhatatelier.blocks.ModBlocks;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.loot.BlockLootSubProvider;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.loot.LootTable;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

public class ModBlockLootTableProvider extends BlockLootSubProvider {
    protected ModBlockLootTableProvider(HolderLookup.Provider registries) {
        super(Set.of(), FeatureFlags.REGISTRY.allFlags(), registries);
    }
    @Override
    protected @NotNull Iterable<Block> getKnownBlocks() {
        return ModBlocks.BLOCKS.getEntries().stream().map(Holder::value)::iterator;
    }
    @Override
    protected void generate() {
        add(ModBlocks.PLACED_PAPER.get(),               LootTable.lootTable());
        add(ModBlocks.PLACED_SQUARE_PAPER.get(),        LootTable.lootTable());
        add(ModBlocks.PLACED_ROUND_PAPER.get(),         LootTable.lootTable());
        add(ModBlocks.PLACED_SMALL_SQUARE_PAPER.get(),  LootTable.lootTable());
        add(ModBlocks.PLACED_MEDIUM_SQUARE_PAPER.get(), LootTable.lootTable());
        add(ModBlocks.PLACED_SMALL_ROUND_PAPER.get(),   LootTable.lootTable());
        add(ModBlocks.PLACED_MEDIUM_ROUND_PAPER.get(),  LootTable.lootTable());
        dropSelf(ModBlocks.CANVAS_PLATE.get());
    }
}
