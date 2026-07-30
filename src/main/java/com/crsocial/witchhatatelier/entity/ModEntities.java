package com.crsocial.witchhatatelier.entity;

import com.crsocial.witchhatatelier.WitchHatAtelierMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Registry for mod entity types. Mirrors the pattern used by
 * {@link com.crsocial.witchhatatelier.items.ModItems} and
 * {@link com.crsocial.witchhatatelier.blocks.ModBlocks}.
 */
public final class ModEntities {

    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, WitchHatAtelierMod.MODID);

    private ModEntities() {}

    public static void register(IEventBus modEventBus) {
        ENTITY_TYPES.register(modEventBus);
    }

    public static final DeferredHolder<EntityType<?>, EntityType<PyreballEntity>> PYREBALL =
            ENTITY_TYPES.register("pyreball", () ->
                    EntityType.Builder.<PyreballEntity>of(PyreballEntity::new, MobCategory.MISC)
                            .sized(1.0f, 1.0f)
                            .clientTrackingRange(8)
                            .updateInterval(1)
                            .build(ResourceLocation.fromNamespaceAndPath(
                                    WitchHatAtelierMod.MODID, "pyreball").toString()));

    public static final DeferredHolder<EntityType<?>, EntityType<SilverWoodBoat>> SILVER_WOOD_BOAT =
            ENTITY_TYPES.register("silver_wood_boat", () ->
                    EntityType.Builder.<SilverWoodBoat>of(SilverWoodBoat::new, MobCategory.MISC)
                            .sized(1.375f, 0.5625f)
                            .eyeHeight(0.5625f)
                            .clientTrackingRange(10)
                            .build(ResourceLocation.fromNamespaceAndPath(
                                    WitchHatAtelierMod.MODID, "silver_wood_boat").toString()));

    public static final DeferredHolder<EntityType<?>, EntityType<SilverWoodChestBoat>> SILVER_WOOD_CHEST_BOAT =
            ENTITY_TYPES.register("silver_wood_chest_boat", () ->
                    EntityType.Builder.<SilverWoodChestBoat>of(SilverWoodChestBoat::new, MobCategory.MISC)
                            .sized(1.375f, 0.5625f)
                            .eyeHeight(0.5625f)
                            .clientTrackingRange(10)
                            .build(ResourceLocation.fromNamespaceAndPath(
                                    WitchHatAtelierMod.MODID, "silver_wood_chest_boat").toString()));
}
