package com.crsocial.witchhatatelier.datagen;

import com.crsocial.witchhatatelier.WitchHatAtelierMod;
import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.data.PackOutput;
import net.minecraft.world.item.ItemDisplayContext;
import net.neoforged.neoforge.client.model.generators.ItemModelProvider;
import net.neoforged.neoforge.client.model.generators.ModelFile;
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
        withExistingParent("spell_binder", "minecraft:item/generated")
                .texture("layer0", modLoc("item/spell_binder"));

        // ── Blank square papers (share spell_paper texture as placeholder) ────
        withExistingParent("small_square_paper", "minecraft:item/generated")
                .texture("layer0", modLoc("item/small_square_paper"));
        withExistingParent("medium_square_paper", "minecraft:item/generated")
                .texture("layer0", modLoc("item/medium_square_paper"));
        withExistingParent("large_square_paper", "minecraft:item/generated")
                .texture("layer0", modLoc("item/large_square_paper"));

        // ── Blank round papers (share round_paper texture as placeholder) ─────
        withExistingParent("small_round_paper", "minecraft:item/generated")
                .texture("layer0", modLoc("item/small_round_paper"));
        withExistingParent("medium_round_paper", "minecraft:item/generated")
                .texture("layer0", modLoc("item/medium_round_paper"));
        withExistingParent("large_round_paper", "minecraft:item/generated")
                .texture("layer0", modLoc("item/large_round_paper"));

        // ── Inscribed square spell papers ─────────────────────────────────────
        withExistingParent("small_square_spell_paper", "minecraft:item/generated")
                .texture("layer0", modLoc("item/small_square_spell_paper"));
        withExistingParent("medium_square_spell_paper", "minecraft:item/generated")
                .texture("layer0", modLoc("item/medium_square_spell_paper"));
        withExistingParent("large_square_spell_paper", "minecraft:item/generated")
                .texture("layer0", modLoc("item/large_square_spell_paper"));

        // ── Inscribed round spell papers ──────────────────────────────────────
        withExistingParent("small_round_spell_paper", "minecraft:item/generated")
                .texture("layer0", modLoc("item/small_round_spell_paper"));
        withExistingParent("medium_round_spell_paper", "minecraft:item/generated")
                .texture("layer0", modLoc("item/medium_round_spell_paper"));
        withExistingParent("large_round_spell_paper", "minecraft:item/generated")
                .texture("layer0", modLoc("item/large_round_spell_paper"));

        // ── Canvas pressure plate (block item parents the generated "up" model) ─
        getBuilder("canvas_plate")
                .parent(new ModelFile.UncheckedModelFile(modLoc("block/canvas_plate")));

        // ── Wand (unchanged) ──────────────────────────────────────────────────
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
