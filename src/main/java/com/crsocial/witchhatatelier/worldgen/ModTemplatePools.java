package com.crsocial.witchhatatelier.worldgen;

import com.crsocial.witchhatatelier.WitchHatAtelierMod;
import com.mojang.datafixers.util.Pair;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.data.worldgen.Pools;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;

import java.util.List;
import java.util.function.Function;

public class ModTemplatePools {

    public static final ResourceKey<StructureTemplatePool> SILVER_WOOD_TREE = registerKey("silver_wood_tree");

    public static void bootstrap(BootstrapContext<StructureTemplatePool> context) {
        var templatePools = context.lookup(Registries.TEMPLATE_POOL);
        Holder<StructureTemplatePool> fallback = templatePools.getOrThrow(Pools.EMPTY);

        context.register(SILVER_WOOD_TREE, new StructureTemplatePool(
                fallback,
                List.<Pair<Function<StructureTemplatePool.Projection, ? extends StructurePoolElement>, Integer>>of(
                        Pair.of(StructurePoolElement.single(WitchHatAtelierMod.MODID + ":silver_wood_tree"), 1)
                ),
                StructureTemplatePool.Projection.RIGID
        ));
    }

    private static ResourceKey<StructureTemplatePool> registerKey(String name) {
        return ResourceKey.create(Registries.TEMPLATE_POOL, ResourceLocation.fromNamespaceAndPath(WitchHatAtelierMod.MODID, name));
    }
}
