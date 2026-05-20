package com.crsocial.witchhatatelier.datagen;

import com.crsocial.witchhatatelier.WitchHatAtelierMod;
import com.crsocial.witchhatatelier.blocks.ModBlocks;
import com.crsocial.witchhatatelier.blocks.PlacedPaper;
import net.minecraft.core.Direction;
import net.minecraft.data.PackOutput;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.client.model.generators.BlockStateProvider;
import net.neoforged.neoforge.client.model.generators.ConfiguredModel;
import net.neoforged.neoforge.client.model.generators.ModelFile;
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

    }
}
