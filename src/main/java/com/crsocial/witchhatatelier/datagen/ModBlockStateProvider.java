package com.crsocial.witchhatatelier.datagen;

import com.crsocial.witchhatatelier.WitchHatAtelierMod;
import com.crsocial.witchhatatelier.blocks.ModBlocks;
import net.minecraft.data.PackOutput;
import net.neoforged.neoforge.client.model.generators.BlockStateProvider;
import net.neoforged.neoforge.client.model.generators.ModelFile;
import net.neoforged.neoforge.common.data.ExistingFileHelper;

public class ModBlockStateProvider extends BlockStateProvider {
    public ModBlockStateProvider(PackOutput output, ExistingFileHelper existingFileHelper) {
        super(output, WitchHatAtelierMod.MODID, existingFileHelper);
    }
    protected void registerStatesAndModels() {
        ModelFile model = models().getExistingFile(modLoc("block/placed_paper"));
        directionalBlock(ModBlocks.PLACED_PAPER.get(), model);
    }
}
