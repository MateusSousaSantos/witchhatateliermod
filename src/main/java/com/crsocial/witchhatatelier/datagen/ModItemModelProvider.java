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

        // ── Silver wood (block items parent generated block models; the block models are
        // written by ModBlockStateProvider, which runs after this provider, so we reference
        // them as unchecked model files the same way canvas_plate does above) ─────────────
        for (String id : new String[]{
                "silver_wood_log", "silver_wood_wood",
                "stripped_silver_log", "stripped_silver_wood",
                "silver_wood_planks", "silver_wood_stairs", "silver_wood_slab",
                "silver_wood_fence_gate", "silver_wood_leaves",
                "budding_silver_wood",
                // Branch stages are solid Blockbench-authored cuboids (not flat billboards
                // like the vines below), so they render fine in-hand/inventory as their real
                // 3D block model instead of a flat generated icon.
                "silver_tree_branch_small", "silver_tree_branch_medium",
                "silver_tree_branch_large", "silver_tree_branch"}) {
            getBuilder(id).parent(new ModelFile.UncheckedModelFile(modLoc("block/" + id)));
        }

        // Fences use the vanilla "fence_inventory" perspective model rather than their
        // (non-flat) block model, matching how vanilla oak_fence's item model is authored.
        withExistingParent("silver_wood_fence", "minecraft:block/fence_inventory")
                .texture("texture", modLoc("block/silver_wood_planks"));

        // Vines: vanilla's own vine item model is a flat generated icon (not the 3D block
        // model, which would render oddly as a floating quad in-hand/inventory).
        withExistingParent("silver_wood_vines", "minecraft:item/generated")
                .texture("layer0", modLoc("block/silver_wood_vines"));

        withExistingParent("silver_wood_seed", "minecraft:item/generated")
                .texture("layer0", modLoc("item/silver_wood_seed"));
        withExistingParent("silver_wood_branch", "minecraft:item/generated")
                .texture("layer0", modLoc("item/silver_wood_branch"));

        // Pressure plate item icon parents its generated "up" block model, same as
        // canvas_plate above (plates are orientation-independent, so this looks fine as-is).
        getBuilder("silver_wood_plate")
                .parent(new ModelFile.UncheckedModelFile(modLoc("block/silver_wood_plate")));

        // Button item icon uses the dedicated "button_inventory" model (a centered floating
        // cube with its own display transforms, generated by ModBlockStateProvider), matching
        // vanilla oak_button — the placed "block/button" model is UV-mapped for its in-world
        // FACING orientation and looks wrong/flat as a floating GUI icon.
        getBuilder("silver_wood_button")
                .parent(new ModelFile.UncheckedModelFile(modLoc("block/silver_wood_button_inventory")));

        // Sign item is a flat generated icon, matching vanilla oak_sign's item model.
        withExistingParent("silver_wood_sign", "minecraft:item/generated")
                .texture("layer0", modLoc("item/silver_wood_sign"));

        // Boats have no block form, so their item icon is always a flat generated icon,
        // matching vanilla oak_boat/oak_chest_boat's item models.
        withExistingParent("silver_wood_boat", "minecraft:item/generated")
                .texture("layer0", modLoc("item/silver_wood_boat"));
        withExistingParent("silver_wood_chest_boat", "minecraft:item/generated")
                .texture("layer0", modLoc("item/silver_wood_chest_boat"));

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
