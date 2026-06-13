package com.crsocial.witchhatatelier.client;

import net.minecraft.client.KeyMapping;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;

@OnlyIn(Dist.CLIENT)
public final class ModKeybindings {

    public static final KeyMapping OPEN_DEBUG_TEMPLATE = new KeyMapping(
            "key.witchhatatelier.open_debug_template",
            GLFW.GLFW_KEY_F8,
            "key.categories.witchhatatelier"
    );

    public static final KeyMapping OPEN_DEBUG_RECOGNITION = new KeyMapping(
            "key.witchhatatelier.open_debug_recognition",
            GLFW.GLFW_KEY_F9,
            "key.categories.witchhatatelier"
    );

    public static void register(RegisterKeyMappingsEvent event) {
        event.register(OPEN_DEBUG_TEMPLATE);
        // Recognition debug (F9) is a dev-only tool — don't even expose the binding in production.
        if (!FMLLoader.isProduction()) {
            event.register(OPEN_DEBUG_RECOGNITION);
        }
    }
}
