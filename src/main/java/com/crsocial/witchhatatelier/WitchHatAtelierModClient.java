package com.crsocial.witchhatatelier;

import com.crsocial.witchhatatelier.blocks.ModBlockEntities;
import com.crsocial.witchhatatelier.client.renderer.PlacedPaperBlockEntityRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

@Mod(value = WitchHatAtelierMod.MODID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = WitchHatAtelierMod.MODID, value = Dist.CLIENT)
public class WitchHatAtelierModClient {
    public WitchHatAtelierModClient(ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }

    @SubscribeEvent
    static void onClientSetup(FMLClientSetupEvent event) {
        WitchHatAtelierMod.LOGGER.info("MINECRAFT NAME >> {}", Minecraft.getInstance().getUser().getName());
        event.enqueueWork(() ->
            BlockEntityRenderers.register(ModBlockEntities.PLACED_PAPER.get(), PlacedPaperBlockEntityRenderer::new)
        );
    }
}
