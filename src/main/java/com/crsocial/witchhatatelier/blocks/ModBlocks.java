package com.crsocial.witchhatatelier.blocks;

import com.crsocial.witchhatatelier.WitchHatAtelierMod;
import com.crsocial.witchhatatelier.items.ModItems;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModBlocks {

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(WitchHatAtelierMod.MODID);

    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
    }


    public static final DeferredBlock<PlacedPaper> PLACED_PAPER = BLOCKS.register("placed_paper",
            () -> new PlacedPaper(BlockBehaviour.Properties.of()
                    .noOcclusion()
                    .strength(0.2f)
                    .sound(SoundType.WOOL)) {
            });

}
