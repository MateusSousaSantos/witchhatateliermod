package com.crsocial.witchhatatelier;

import com.crsocial.witchhatatelier.blocks.ModBlockEntities;
import com.crsocial.witchhatatelier.blocks.ModBlocks;
import com.crsocial.witchhatatelier.datagen.DataGenerators;
import com.crsocial.witchhatatelier.entity.ModEntities;
import com.crsocial.witchhatatelier.items.ModItems;
import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import com.crsocial.witchhatatelier.network.SaveGestureHandler;
import com.crsocial.witchhatatelier.network.SaveGesturePayload;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

@Mod(WitchHatAtelierMod.MODID)
public class WitchHatAtelierMod {
    public static final String MODID = "witchhatateliermod";
    public static final Logger LOGGER = LogUtils.getLogger();

    public WitchHatAtelierMod(IEventBus modEventBus, ModContainer modContainer) {
        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::registerPayloads);

        modEventBus.addListener(DataGenerators::gatherData);
        ModItems.register(modEventBus);
        ModBlocks.register(modEventBus);
        ModBlockEntities.register(modEventBus);
        ModEntities.register(modEventBus);
        ModCreativeModeTabs.register(modEventBus);
        NeoForge.EVENT_BUS.register(this);

        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }

    private void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToServer(
                SaveGesturePayload.TYPE,
                SaveGesturePayload.STREAM_CODEC,
                SaveGestureHandler::handle
        );
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        LOGGER.info("COMMON SETUP");
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        LOGGER.info("HELLO from server starting");
    }
}
