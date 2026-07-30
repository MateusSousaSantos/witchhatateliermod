package com.crsocial.witchhatatelier.datagen;

import com.crsocial.witchhatatelier.WitchHatAtelierMod;
import com.crsocial.witchhatatelier.blocks.ModBlocks;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.tags.BlockTags;
import net.neoforged.neoforge.common.data.BlockTagsProvider;
import net.neoforged.neoforge.common.data.ExistingFileHelper;

import java.util.concurrent.CompletableFuture;

public class ModBlockTagProvider extends BlockTagsProvider {

    public ModBlockTagProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> lookupProvider,
                                ExistingFileHelper existingFileHelper) {
        super(output, lookupProvider, WitchHatAtelierMod.MODID, existingFileHelper);
    }

    @Override
    protected void addTags(HolderLookup.Provider provider) {
        tag(BlockTags.LOGS_THAT_BURN).add(
                ModBlocks.SILVER_WOOD_LOG.get(), ModBlocks.SILVER_WOOD_WOOD.get(),
                ModBlocks.STRIPPED_SILVER_LOG.get(), ModBlocks.STRIPPED_SILVER_WOOD.get());
        tag(BlockTags.PLANKS).add(ModBlocks.SILVER_WOOD_PLANKS.get());
        tag(BlockTags.WOODEN_STAIRS).add(ModBlocks.SILVER_WOOD_STAIRS.get());
        tag(BlockTags.WOODEN_SLABS).add(ModBlocks.SILVER_WOOD_SLAB.get());
        tag(BlockTags.WOODEN_FENCES).add(ModBlocks.SILVER_WOOD_FENCE.get());
        tag(BlockTags.FENCE_GATES).add(ModBlocks.SILVER_WOOD_FENCE_GATE.get());
        tag(BlockTags.LEAVES).add(ModBlocks.SILVER_WOOD_LEAVES.get());
        tag(BlockTags.CLIMBABLE).add(ModBlocks.SILVER_WOOD_VINES.get());
        tag(BlockTags.WOODEN_PRESSURE_PLATES).add(ModBlocks.SILVER_WOOD_PLATE.get());
        tag(BlockTags.WOODEN_BUTTONS).add(ModBlocks.SILVER_WOOD_BUTTON.get());
        tag(BlockTags.STANDING_SIGNS).add(ModBlocks.SILVER_WOOD_SIGN.get());
        tag(BlockTags.WALL_SIGNS).add(ModBlocks.SILVER_WOOD_WALL_SIGN.get());

        tag(BlockTags.MINEABLE_WITH_AXE).add(
                ModBlocks.SILVER_WOOD_LOG.get(), ModBlocks.SILVER_WOOD_WOOD.get(),
                ModBlocks.STRIPPED_SILVER_LOG.get(), ModBlocks.STRIPPED_SILVER_WOOD.get(),
                ModBlocks.SILVER_WOOD_PLANKS.get(), ModBlocks.SILVER_WOOD_STAIRS.get(),
                ModBlocks.SILVER_WOOD_SLAB.get(), ModBlocks.SILVER_WOOD_FENCE.get(),
                ModBlocks.SILVER_WOOD_FENCE_GATE.get(), ModBlocks.SILVER_WOOD_PLATE.get(),
                ModBlocks.SILVER_WOOD_BUTTON.get(), ModBlocks.SILVER_WOOD_SIGN.get(),
                ModBlocks.SILVER_WOOD_WALL_SIGN.get());
        tag(BlockTags.MINEABLE_WITH_HOE).add(ModBlocks.SILVER_WOOD_LEAVES.get());

        tag(BlockTags.MINEABLE_WITH_PICKAXE).add(
                ModBlocks.BUDDING_SILVER_WOOD.get(),
                ModBlocks.SILVER_TREE_BRANCH_SMALL.get(), ModBlocks.SILVER_TREE_BRANCH_MEDIUM.get(),
                ModBlocks.SILVER_TREE_BRANCH_LARGE.get(), ModBlocks.SILVER_TREE_BRANCH.get());
    }
}
