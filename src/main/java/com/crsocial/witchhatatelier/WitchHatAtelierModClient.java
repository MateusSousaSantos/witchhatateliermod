package com.crsocial.witchhatatelier;

import com.crsocial.witchhatatelier.blocks.ModBlockEntities;
import com.crsocial.witchhatatelier.client.CastingAnimation;
import com.crsocial.witchhatatelier.client.ModKeybindings;
import com.crsocial.witchhatatelier.client.gesture.DebugTemplateScreen;
import com.crsocial.witchhatatelier.client.gesture.RecognitionDebugScreen;
import com.crsocial.witchhatatelier.client.renderer.PlacedPaperBlockEntityRenderer;
import com.crsocial.witchhatatelier.client.renderer.PyreballRenderer;
import com.crsocial.witchhatatelier.client.renderer.SilverWoodBoatRenderer;
import com.crsocial.witchhatatelier.entity.ModEntities;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.BoatModel;
import net.minecraft.client.model.ChestBoatModel;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

@Mod(value = WitchHatAtelierMod.MODID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = WitchHatAtelierMod.MODID, value = Dist.CLIENT)
public class WitchHatAtelierModClient {
    public WitchHatAtelierModClient(ModContainer container, IEventBus modEventBus) {
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
        modEventBus.addListener(ModKeybindings::register);
        modEventBus.addListener(WitchHatAtelierModClient::onRegisterLayerDefinitions);
    }

    private static void onRegisterLayerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(SilverWoodBoatRenderer.BOAT_LAYER, BoatModel::createBodyModel);
        event.registerLayerDefinition(SilverWoodBoatRenderer.CHEST_BOAT_LAYER, ChestBoatModel::createBodyModel);
    }

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        while (ModKeybindings.OPEN_DEBUG_TEMPLATE.consumeClick()) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.screen == null) mc.setScreen(new DebugTemplateScreen());
        }
        while (ModKeybindings.OPEN_DEBUG_RECOGNITION.consumeClick()) {
            // Recognition debug screen is a dev-only tool — never openable in a production install.
            if (FMLLoader.isProduction()) continue;
            Minecraft mc = Minecraft.getInstance();
            if (mc.screen == null) mc.setScreen(new RecognitionDebugScreen());
        }
    }

    @SubscribeEvent
    static void onClientSetup(FMLClientSetupEvent event) {
        WitchHatAtelierMod.LOGGER.info("MINECRAFT NAME >> {}", Minecraft.getInstance().getUser().getName());
        event.enqueueWork(() -> {
            BlockEntityRenderers.register(ModBlockEntities.PLACED_PAPER.get(), PlacedPaperBlockEntityRenderer::new);
            EntityRenderers.register(ModEntities.PYREBALL.get(), PyreballRenderer::new);
            EntityRenderers.register(ModEntities.SILVER_WOOD_BOAT.get(), context -> new SilverWoodBoatRenderer(context, false));
            EntityRenderers.register(ModEntities.SILVER_WOOD_CHEST_BOAT.get(), context -> new SilverWoodBoatRenderer(context, true));
            CastingAnimation.register();
        });
    }
}
