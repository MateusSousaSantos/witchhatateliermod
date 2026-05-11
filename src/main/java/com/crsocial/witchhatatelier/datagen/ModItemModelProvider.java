package com.crsocial.witchhatatelier.datagen;

import com.crsocial.witchhatatelier.WitchHatAtelierMod;
import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.data.PackOutput;
import net.minecraft.world.item.ItemDisplayContext;
import net.neoforged.neoforge.client.model.generators.ItemModelProvider;
import net.neoforged.neoforge.client.model.generators.loaders.SeparateTransformsModelBuilder;
import net.neoforged.neoforge.common.data.ExistingFileHelper;

public class ModItemModelProvider extends ItemModelProvider {

    public ModItemModelProvider(PackOutput output, ExistingFileHelper existingFileHelper) {
        super(output, WitchHatAtelierMod.MODID, existingFileHelper);
    }

    @Override
    protected void registerModels() {
        withExistingParent("nib", "minecraft:item/generated")
                .texture("layer0", modLoc("item/nib"));
        withExistingParent("round_paper", "minecraft:item/generated")
                .texture("layer0", modLoc("item/round_paper"));
        withExistingParent("spell_paper", "minecraft:item/generated")
                .texture("layer0", modLoc("item/spell_paper"));
        withExistingParent("round_spell_paper", "minecraft:item/generated")
                .texture("layer0", modLoc("item/round_spell_paper"));
        withExistingParent("spell_binder", "minecraft:item/generated")
                .texture("layer0", modLoc("item/spell_binder"));
        withExistingParent("used_spell_paper", "minecraft:item/generated")
                .texture("layer0", modLoc("item/used_spell_paper"));
        // Wand 2D sub-model (used for GUI / ground / fixed perspectives)
        withExistingParent("wand_2d", "minecraft:item/generated")
                .guiLight(BlockModel.GuiLight.FRONT)
                .texture("layer0", modLoc("item/wand"));

        getBuilder("wand")
                .guiLight(BlockModel.GuiLight.FRONT)
                .customLoader(SeparateTransformsModelBuilder::begin)
                .base(nested().parent(getExistingFile(modLoc("item/wand_3d"))))
                .perspective(ItemDisplayContext.GUI,
                        nested().parent(getExistingFile(modLoc("item/wand_2d"))))
                .perspective(ItemDisplayContext.GROUND,
                        nested().parent(getExistingFile(modLoc("item/wand_2d"))))
                .perspective(ItemDisplayContext.FIXED,
                        nested().parent(getExistingFile(modLoc("item/wand_2d"))));
    }
}
