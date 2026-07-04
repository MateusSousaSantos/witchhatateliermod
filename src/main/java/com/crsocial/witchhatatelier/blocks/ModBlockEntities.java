package com.crsocial.witchhatatelier.blocks;

import com.crsocial.witchhatatelier.WitchHatAtelierMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModBlockEntities {

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, WitchHatAtelierMod.MODID);

    public static void register(IEventBus modEventBus) {
        BLOCK_ENTITIES.register(modEventBus);
    }

    @SuppressWarnings("DataFlowIssue")
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PlacedPaperBlockEntity>>
            PLACED_PAPER = BLOCK_ENTITIES.register("placed_paper",
                    () -> BlockEntityType.Builder
                            .of(PlacedPaperBlockEntity::new,
                                    ModBlocks.PLACED_PAPER.get(),
                                    ModBlocks.PLACED_SQUARE_PAPER.get(),
                                    ModBlocks.PLACED_ROUND_PAPER.get(),
                                    ModBlocks.PLACED_SMALL_SQUARE_PAPER.get(),
                                    ModBlocks.PLACED_MEDIUM_SQUARE_PAPER.get(),
                                    ModBlocks.PLACED_SMALL_ROUND_PAPER.get(),
                                    ModBlocks.PLACED_MEDIUM_ROUND_PAPER.get())
                            .build(null));

    @SuppressWarnings("DataFlowIssue")
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CanvasPlateBlockEntity>>
            CANVAS_PLATE = BLOCK_ENTITIES.register("canvas_plate",
                    () -> BlockEntityType.Builder
                            .of(CanvasPlateBlockEntity::new, ModBlocks.CANVAS_PLATE.get())
                            .build(null));
}
