package com.crsocial.witchhatatelier.worldgen;

import com.crsocial.witchhatatelier.WitchHatAtelierMod;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.VerticalAnchor;
import net.minecraft.world.level.levelgen.heightproviders.ConstantHeight;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.TerrainAdjustment;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;

import java.util.Map;

public class ModStructures {

    public static final ResourceKey<Structure> SILVER_WOOD_TREE = registerKey("silver_wood_tree");

    public static void bootstrap(BootstrapContext<Structure> context) {
        HolderGetter<Biome> biomes = context.lookup(Registries.BIOME);
        HolderGetter<StructureTemplatePool> templatePools = context.lookup(Registries.TEMPLATE_POOL);

        HolderSet<Biome> validBiomes = biomes.getOrThrow(
                TagKey.create(Registries.BIOME, ResourceLocation.fromNamespaceAndPath(WitchHatAtelierMod.MODID, "has_structure/silver_wood_tree")));

        context.register(SILVER_WOOD_TREE, new FlatGroundJigsawStructure(
                new Structure.StructureSettings(
                        validBiomes,
                        Map.of(),
                        GenerationStep.Decoration.SURFACE_STRUCTURES,
                        TerrainAdjustment.BEARD_THIN
                ),
                templatePools.getOrThrow(ModTemplatePools.SILVER_WOOD_TREE),
                // Must be >=1 even for a single non-jigsaw piece: JigsawPlacement only transfers
                // the start piece into the structure's piece list inside its "maxDepth > 0" branch.
                1,
                ConstantHeight.of(VerticalAnchor.absolute(1)),
                false,
                Heightmap.Types.WORLD_SURFACE_WG,
                // silver_wood_tree.nbt is 15x15 in plan, so half-footprint radius is 7.
                7,
                3
        ));
    }

    private static ResourceKey<Structure> registerKey(String name) {
        return ResourceKey.create(Registries.STRUCTURE, ResourceLocation.fromNamespaceAndPath(WitchHatAtelierMod.MODID, name));
    }
}
