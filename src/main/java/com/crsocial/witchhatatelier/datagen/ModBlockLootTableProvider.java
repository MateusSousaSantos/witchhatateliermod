package com.crsocial.witchhatatelier.datagen;


import com.crsocial.witchhatatelier.blocks.ModBlocks;
import com.crsocial.witchhatatelier.items.ModItems;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.loot.BlockLootSubProvider;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.entries.LootPoolSingletonContainer;
import net.minecraft.world.level.storage.loot.predicates.BonusLevelTableCondition;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
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

        // ── Silver wood ──────────────────────────────────────────────────────
        dropSelf(ModBlocks.SILVER_WOOD_LOG.get());
        dropSelf(ModBlocks.SILVER_WOOD_WOOD.get());
        dropSelf(ModBlocks.STRIPPED_SILVER_LOG.get());
        dropSelf(ModBlocks.STRIPPED_SILVER_WOOD.get());
        dropSelf(ModBlocks.SILVER_WOOD_PLANKS.get());
        dropSelf(ModBlocks.SILVER_WOOD_STAIRS.get());
        add(ModBlocks.SILVER_WOOD_SLAB.get(), createSlabItemTable(ModBlocks.SILVER_WOOD_SLAB.get()));
        dropSelf(ModBlocks.SILVER_WOOD_FENCE.get());
        dropSelf(ModBlocks.SILVER_WOOD_FENCE_GATE.get());
        add(ModBlocks.SILVER_WOOD_LEAVES.get(), createSilverWoodLeavesDrops());
        add(ModBlocks.SILVER_WOOD_VINES.get(), createShearsOnlyDrop(ModBlocks.SILVER_WOOD_VINES.get()));
        dropSelf(ModBlocks.SILVER_WOOD_PLATE.get());
        dropSelf(ModBlocks.SILVER_WOOD_BUTTON.get());
        // The wall sign has no loot table of its own — signProps().dropsLike(SILVER_WOOD_SIGN)
        // in ModBlocks points its loot table id directly at the standing sign's, same as
        // vanilla (there's no oak_wall_sign.json either).
        dropSelf(ModBlocks.SILVER_WOOD_SIGN.get());

        // ── Budding silver wood ──────────────────────────────────────────────
        // Mirrors budding_amethyst: drops nothing, not even with silk touch.
        add(ModBlocks.BUDDING_SILVER_WOOD.get(), LootTable.lootTable());
        add(ModBlocks.SILVER_TREE_BRANCH_SMALL.get(), createSilkTouchOnlyTable(ModBlocks.SILVER_TREE_BRANCH_SMALL.get()));
        add(ModBlocks.SILVER_TREE_BRANCH_MEDIUM.get(), createSilkTouchOnlyTable(ModBlocks.SILVER_TREE_BRANCH_MEDIUM.get()));
        add(ModBlocks.SILVER_TREE_BRANCH_LARGE.get(), createSilkTouchOnlyTable(ModBlocks.SILVER_TREE_BRANCH_LARGE.get()));
        // Final stage mirrors amethyst_cluster's loot shape: silk touch drops itself,
        // otherwise drops silver wood branch items.
        add(ModBlocks.SILVER_TREE_BRANCH.get(), createSingleItemTableWithSilkTouch(
                ModBlocks.SILVER_TREE_BRANCH.get(), ModItems.SILVER_WOOD_BRANCH.get(), ConstantValue.exactly(2.0F)));
    }

    // Mirrors vanilla's leaf loot shape (self via silk touch/shears, otherwise a low fortune-
    // scaled chance of a seed) without a sapling block to key off, since silver wood has none.
    private LootTable.Builder createSilverWoodLeavesDrops() {
        Block leaves = ModBlocks.SILVER_WOOD_LEAVES.get();
        HolderLookup.RegistryLookup<Enchantment> enchantments = this.registries.lookupOrThrow(Registries.ENCHANTMENT);
        return createSilkTouchOrShearsDispatchTable(
                leaves,
                ((LootPoolSingletonContainer.Builder) applyExplosionCondition(
                        leaves, LootItem.lootTableItem(ModItems.SILVER_WOOD_SEED.get())))
                        .when(BonusLevelTableCondition.bonusLevelFlatChance(
                                enchantments.getOrThrow(Enchantments.FORTUNE),
                                0.05f, 0.0625f, 0.083333336f, 0.1f))
        );
    }
}
