package com.crsocial.witchhatatelier.worldgen;

import com.crsocial.witchhatatelier.WitchHatAtelierMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModStructureTypes {

    public static final DeferredRegister<StructureType<?>> STRUCTURE_TYPES =
            DeferredRegister.create(Registries.STRUCTURE_TYPE, WitchHatAtelierMod.MODID);

    public static final DeferredHolder<StructureType<?>, StructureType<FlatGroundJigsawStructure>> FLAT_GROUND_JIGSAW =
            STRUCTURE_TYPES.register("flat_ground_jigsaw", () -> () -> FlatGroundJigsawStructure.CODEC);

    public static void register(IEventBus modEventBus) {
        STRUCTURE_TYPES.register(modEventBus);
    }
}
