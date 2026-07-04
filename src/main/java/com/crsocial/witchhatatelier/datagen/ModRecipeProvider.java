package com.crsocial.witchhatatelier.datagen;

import com.crsocial.witchhatatelier.blocks.ModBlocks;
import com.crsocial.witchhatatelier.items.ModItems;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.data.recipes.ShapedRecipeBuilder;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;

public class ModRecipeProvider extends RecipeProvider {


    public ModRecipeProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
        super(output, registries);
    }

    @Override
    protected void buildRecipes(@NotNull RecipeOutput output) {
        // Nib: two gold nuggets stacked vertically
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.NIB.get())
                .pattern("g")
                .pattern("g")
                .define('g', Items.GOLD_NUGGET)
                .unlockedBy("has_gold_nugget", has(Items.GOLD_NUGGET))
                .save(output);

        // Wand: stick + iron ingot + nib in a diagonal
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.WAND.get())
                .pattern("  s")
                .pattern(" i ")
                .pattern("n  ")
                .define('s', Items.STICK)
                .define('i', Items.IRON_INGOT)
                .define('n', ModItems.NIB.get())
                .unlockedBy("has_nib", has(ModItems.NIB.get()))
                .save(output);

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.SPELL_BINDER.get())
                .pattern("s  ")
                .pattern("sli")
                .pattern("s  ")
                .define('i', Items.IRON_NUGGET)
                .define('s', Items.STRING)
                .define('l', Items.LEATHER)
                .unlockedBy("has_medium_square_paper", has(ModItems.MEDIUM_SQUARE_PAPER.get()))
                .save(output);

        // Canvas pressure plate: a paper laid over a stone pressure plate.
        ShapedRecipeBuilder.shaped(RecipeCategory.REDSTONE, ModBlocks.CANVAS_PLATE.get())
                .pattern("p")
                .pattern("s")
                .define('p', ModItems.MEDIUM_SQUARE_PAPER.get())
                .define('s', Items.STONE_PRESSURE_PLATE)
                .unlockedBy("has_medium_square_paper", has(ModItems.MEDIUM_SQUARE_PAPER.get()))
                .save(output);
    }
}
