package com.crsocial.witchhatatelier.datagen;

import com.crsocial.witchhatatelier.WitchHatAtelierMod;
import com.crsocial.witchhatatelier.blocks.ModBlocks;
import net.minecraft.data.PackOutput;
import net.minecraft.world.level.block.VineBlock;
import net.neoforged.neoforge.client.model.generators.BlockStateProvider;
import net.neoforged.neoforge.client.model.generators.ModelFile;
import net.neoforged.neoforge.client.model.generators.MultiPartBlockStateBuilder;
import net.neoforged.neoforge.common.data.ExistingFileHelper;

public class ModBlockStateProvider extends BlockStateProvider {
    public ModBlockStateProvider(PackOutput output, ExistingFileHelper existingFileHelper) {
        super(output, WitchHatAtelierMod.MODID, existingFileHelper);
    }

    @Override
    protected void registerStatesAndModels() {
        directionalBlock(ModBlocks.PLACED_PAPER.get(),
                models().getExistingFile(modLoc("block/placed_paper")));
        directionalBlock(ModBlocks.PLACED_SQUARE_PAPER.get(),
                models().getExistingFile(modLoc("block/placed_square_paper")));
        directionalBlock(ModBlocks.PLACED_ROUND_PAPER.get(),
                models().getExistingFile(modLoc("block/placed_round_paper")));

        directionalBlock(ModBlocks.PLACED_SMALL_SQUARE_PAPER.get(),
                models().getExistingFile(modLoc("block/placed_small_square_paper")));
        directionalBlock(ModBlocks.PLACED_MEDIUM_SQUARE_PAPER.get(),
                models().getExistingFile(modLoc("block/placed_medium_square_paper")));


        directionalBlock(ModBlocks.PLACED_SMALL_ROUND_PAPER.get(),
                models().getExistingFile(modLoc("block/placed_small_round_paper")));
        directionalBlock(ModBlocks.PLACED_MEDIUM_ROUND_PAPER.get(),
                models().getExistingFile(modLoc("block/placed_medium_round_paper")));

        // Canvas pressure plate — vanilla stone texture, so it reads as a stone pressure plate.
        pressurePlateBlock(ModBlocks.CANVAS_PLATE.get(), mcLoc("block/stone"));

        // ── Silver wood ──────────────────────────────────────────────────────
        logBlock(ModBlocks.SILVER_WOOD_LOG.get());
        axisBlock(ModBlocks.SILVER_WOOD_WOOD.get(),
                modLoc("block/silver_wood_log"), modLoc("block/silver_wood_log"));
        // Texture file is "striped_silver_log" (not "stripped_..."); no dedicated stripped-top
        // texture was provided, so the regular log top is reused here.
        axisBlock(ModBlocks.STRIPPED_SILVER_LOG.get(),
                modLoc("block/striped_silver_log"), modLoc("block/silver_wood_log_top"));
        axisBlock(ModBlocks.STRIPPED_SILVER_WOOD.get(),
                modLoc("block/striped_silver_log"), modLoc("block/striped_silver_log"));

        simpleBlock(ModBlocks.SILVER_WOOD_PLANKS.get());

        stairsBlock(ModBlocks.SILVER_WOOD_STAIRS.get(), modLoc("block/silver_wood_planks"));
        slabBlock(ModBlocks.SILVER_WOOD_SLAB.get(),
                modLoc("block/silver_wood_planks"), modLoc("block/silver_wood_planks"));
        fenceBlock(ModBlocks.SILVER_WOOD_FENCE.get(), modLoc("block/silver_wood_planks"));
        fenceGateBlock(ModBlocks.SILVER_WOOD_FENCE_GATE.get(), modLoc("block/silver_wood_planks"));

        simpleBlock(ModBlocks.SILVER_WOOD_LEAVES.get(),
                models().cubeAll("silver_wood_leaves", modLoc("block/silver_wood_leaves")).renderType("cutout"));

        vinesBlock();
    }

    // Vines have no dedicated NeoForge BlockStateProvider helper; this mirrors vanilla's
    // multipart vine.json/vine blockstate against a hand-authored decal model.
    private void vinesBlock() {
        ModelFile vineModel = models().getExistingFile(modLoc("block/silver_wood_vines"));
        MultiPartBlockStateBuilder vines = getMultipartBuilder(ModBlocks.SILVER_WOOD_VINES.get());

        vines.part().modelFile(vineModel).addModel()
                .condition(VineBlock.NORTH, true);
        vines.part().modelFile(vineModel).addModel()
                .condition(VineBlock.NORTH, false).condition(VineBlock.EAST, false)
                .condition(VineBlock.SOUTH, false).condition(VineBlock.WEST, false)
                .condition(VineBlock.UP, false);

        vines.part().modelFile(vineModel).rotationY(90).uvLock(true).addModel()
                .condition(VineBlock.EAST, true);
        vines.part().modelFile(vineModel).rotationY(90).uvLock(true).addModel()
                .condition(VineBlock.NORTH, false).condition(VineBlock.EAST, false)
                .condition(VineBlock.SOUTH, false).condition(VineBlock.WEST, false)
                .condition(VineBlock.UP, false);

        vines.part().modelFile(vineModel).rotationY(180).uvLock(true).addModel()
                .condition(VineBlock.SOUTH, true);
        vines.part().modelFile(vineModel).rotationY(180).uvLock(true).addModel()
                .condition(VineBlock.NORTH, false).condition(VineBlock.EAST, false)
                .condition(VineBlock.SOUTH, false).condition(VineBlock.WEST, false)
                .condition(VineBlock.UP, false);

        vines.part().modelFile(vineModel).rotationY(270).uvLock(true).addModel()
                .condition(VineBlock.WEST, true);
        vines.part().modelFile(vineModel).rotationY(270).uvLock(true).addModel()
                .condition(VineBlock.NORTH, false).condition(VineBlock.EAST, false)
                .condition(VineBlock.SOUTH, false).condition(VineBlock.WEST, false)
                .condition(VineBlock.UP, false);

        vines.part().modelFile(vineModel).rotationX(270).uvLock(true).addModel()
                .condition(VineBlock.UP, true);
        vines.part().modelFile(vineModel).rotationX(270).uvLock(true).addModel()
                .condition(VineBlock.NORTH, false).condition(VineBlock.EAST, false)
                .condition(VineBlock.SOUTH, false).condition(VineBlock.WEST, false)
                .condition(VineBlock.UP, false);
    }
}
