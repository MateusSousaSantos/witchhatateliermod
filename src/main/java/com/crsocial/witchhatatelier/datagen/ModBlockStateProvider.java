package com.crsocial.witchhatatelier.datagen;

import com.crsocial.witchhatatelier.WitchHatAtelierMod;
import com.crsocial.witchhatatelier.blocks.ModBlocks;
import com.crsocial.witchhatatelier.blocks.PlacedLargePaper;
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

        largePaperBlock(ModBlocks.PLACED_LARGE_SQUARE_PAPER.get(), "placed_large_square_paper");

        directionalBlock(ModBlocks.PLACED_SMALL_ROUND_PAPER.get(),
                models().getExistingFile(modLoc("block/placed_small_round_paper")));
        directionalBlock(ModBlocks.PLACED_MEDIUM_ROUND_PAPER.get(),
                models().getExistingFile(modLoc("block/placed_medium_round_paper")));

        largePaperBlock(ModBlocks.PLACED_LARGE_ROUND_PAPER.get(), "placed_large_round_paper");
    }

    /** Generates all 24 blockstate variants (6 facings × 2 part_x × 2 part_y) for a 2×2 paper. */
    private void largePaperBlock(Block block, String modelName) {
        ModelFile primary   = models().getExistingFile(modLoc("block/" + modelName));
        ModelFile satellite = models().getExistingFile(modLoc("block/placed_paper_satellite"));
        var builder = getVariantBuilder(block);

        for (Direction facing : Direction.values()) {
            int xRot = facing == Direction.DOWN ? 180 : facing.getAxis().isVertical() ? 0 : 90;
            int yRot = switch (facing) {
                case SOUTH -> 180;
                case EAST  -> 90;
                case WEST  -> 270;
                default    -> 0;
            };

            builder.partialState()
                    .with(PlacedPaper.FACING, facing)
                    .with(PlacedLargePaper.PART_X, 0)
                    .with(PlacedLargePaper.PART_Y, 0)
                    .addModels(new ConfiguredModel(primary, xRot, yRot, false));

            for (int px = 0; px <= 1; px++) {
                for (int py = 0; py <= 1; py++) {
                    if (px == 0 && py == 0) continue;
                    builder.partialState()
                            .with(PlacedPaper.FACING, facing)
                            .with(PlacedLargePaper.PART_X, px)
                            .with(PlacedLargePaper.PART_Y, py)
                            .addModels(new ConfiguredModel(satellite));
                }
            }
        }
    }
}
