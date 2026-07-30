package com.crsocial.witchhatatelier.worldgen;

import com.crsocial.witchhatatelier.WitchHatAtelierMod;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadType;

public class ModStructureSets {

    public static final ResourceKey<StructureSet> SILVER_WOOD_TREE = registerKey("silver_wood_tree");

    public static void bootstrap(BootstrapContext<StructureSet> context) {
        HolderGetter<Structure> structures = context.lookup(Registries.STRUCTURE);
        Holder<Structure> silverWoodTree = structures.getOrThrow(ModStructures.SILVER_WOOD_TREE);

        context.register(SILVER_WOOD_TREE, new StructureSet(
                silverWoodTree,
                new RandomSpreadStructurePlacement(20, 8, RandomSpreadType.LINEAR, 84_732_190)
        ));
    }

    private static ResourceKey<StructureSet> registerKey(String name) {
        return ResourceKey.create(Registries.STRUCTURE_SET, ResourceLocation.fromNamespaceAndPath(WitchHatAtelierMod.MODID, name));
    }
}
