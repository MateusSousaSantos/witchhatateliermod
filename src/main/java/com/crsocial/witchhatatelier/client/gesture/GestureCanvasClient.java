package com.crsocial.witchhatatelier.client.gesture;

import com.crsocial.witchhatatelier.WitchHatAtelierMod;
import com.crsocial.witchhatatelier.items.SpellPaperItem;
import com.crsocial.witchhatatelier.network.SaveGesturePayload;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;


/**
 * Client-side opener and profile resolver for drawable item screens.
 */
@OnlyIn(Dist.CLIENT)
public final class GestureCanvasClient {

    private static final GestureCanvasProfile PAPER_PROFILE = new GestureCanvasProfile(
            "screen.witchhatatelier.gesture_canvas.paper",
            "screen.witchhatatelier.gesture_canvas.paper.read_only",
            ResourceLocation.fromNamespaceAndPath(WitchHatAtelierMod.MODID, "textures/gui/blank.png"),
            16,
            16,
            CanvasInputShape.RECTANGLE,
            0.55f,
            0xFFFCFCF2,
            0xFFF3F3FF,
            0xFFE9EAEB,
            0xFF000000,
            0xFFCF31C2,
            5,
            0.5f,   // Lazy-Mouse: moderate drag, good all-round feel
            true    // angle snap enabled
    );


    private static final GestureCanvasProfile ROUND_PAPER_PROFILE = new GestureCanvasProfile(
            "screen.witchhatatelier.gesture_canvas.paper",
            "screen.witchhatatelier.gesture_canvas.paper.read_only",
            ResourceLocation.fromNamespaceAndPath(WitchHatAtelierMod.MODID, "textures/gui/blank.png"),
            16,
            16,
            CanvasInputShape.CIRCLE,
            0.55f,
            0xFFFCFCF2,
            0xFFF3F3FF,
            0xFFE9EAEB,
            0xFF000000,
            0xFFCF31C2,
            5,
            0.5f,   // Lazy-Mouse: moderate drag
            true    // angle snap enabled
    );

    private static final GestureCanvasProfile SPELL_PAPER_PROFILE = new GestureCanvasProfile(
            "screen.witchhatatelier.gesture_canvas.spell_paper",
            "screen.witchhatatelier.gesture_canvas.spell_paper.read_only",
            ResourceLocation.fromNamespaceAndPath(WitchHatAtelierMod.MODID, "textures/gui/blank.png"),
            16,
            16,
            CanvasInputShape.RECTANGLE,
            0.55f,
            0xFFFCFCF2,
            0xFFF3F3FF,
            0xFFE9EAEB,
            0xFF000000,
            0xFFCF31C2,
            5,
            0.5f,   // Lazy-Mouse: moderate drag
            true    // angle snap enabled
    );

    private GestureCanvasClient() {
    }
    public static void openCanvas(ItemStack drawableStack, List<GesturePoint> preloadedPoints, boolean editable) {
        openCanvas(drawableStack, preloadedPoints, editable, null);
    }


    private static GestureCanvasProfile resolveProfile(ItemStack drawableStack) {
        if (drawableStack.getItem() instanceof SpellPaperItem paper) {
            return paper.isRound() ? ROUND_PAPER_PROFILE : SPELL_PAPER_PROFILE;
        }
        return GestureCanvasProfile.fallback();
    }

    public static void openCanvas(ItemStack drawableStack, List<GesturePoint> preloadedPoints, boolean editable, net.minecraft.core.BlockPos origin) {
        GestureCanvasProfile profile = resolveProfile(drawableStack);
        Minecraft.getInstance().setScreen(new GestureCanvasScreen(profile, preloadedPoints, editable, points -> sendToServer(points, origin), origin));
    }


    private static void sendToServer(List<GesturePoint> pointCloud, net.minecraft.core.BlockPos origin) {
        var mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }

        var playerPos = mc.player.position();
        WitchHatAtelierMod.LOGGER.info("[GestureCanvas] Closing - {} point(s). Sending to server.", pointCloud.size());
        PacketDistributor.sendToServer(new SaveGesturePayload(pointCloud, playerPos, origin));
    }

}

