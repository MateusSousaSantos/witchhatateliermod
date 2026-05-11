package com.crsocial.witchhatatelier.client.gesture;

import com.crsocial.witchhatatelier.WitchHatAtelierMod;
import com.crsocial.witchhatatelier.items.SpellPaperItem;
import com.crsocial.witchhatatelier.network.SaveGesturePayload;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

/**
 * Client-side entry point for opening the gesture canvas.
 *
 * <p>Profile resolution lives here so that the profile records themselves
 * ({@link CanvasProfile.RoundPaperProfile}, etc.) stay free of item references.
 * To add a new item type, add a branch in {@link #resolveProfile(ItemStack)} that
 * returns an appropriate {@link CanvasProfile} implementation — no other file needs
 * to change.</p>
 */
@OnlyIn(Dist.CLIENT)
public final class GestureCanvasClient {

    private GestureCanvasClient() {}

    // ── Public API ───────────────────────────────────────────────────────────────

    public static void openCanvas(ItemStack stack, List<GesturePoint> preloaded, boolean editable) {
        openCanvas(stack, preloaded, editable, null);
    }

    public static void openCanvas(ItemStack stack, List<GesturePoint> preloaded,
                                  boolean editable, BlockPos origin) {
        CanvasProfile profile = resolveProfile(stack);
        Minecraft.getInstance().setScreen(
                new CanvasScreen(profile, preloaded, editable,
                        points -> sendToServer(points, origin), origin));
    }

    // ── Profile resolution ───────────────────────────────────────────────────────

    /**
     * Returns the {@link CanvasProfile} for the given drawable item stack.
     *
     * <p>Add a new {@code instanceof} branch here whenever a new drawable item type
     * is introduced. The profile record itself lives in its own file.</p>
     */
    private static CanvasProfile resolveProfile(ItemStack stack) {
        if (stack.getItem() instanceof SpellPaperItem paper) {
            return paper.isRound()
                    ? new CanvasProfile.RoundPaperProfile()
                    : new CanvasProfile.SpellPaperProfile();
        }
        return new CanvasProfile.FallbackProfile();
    }

    // ── Network ──────────────────────────────────────────────────────────────────

    private static void sendToServer(List<GesturePoint> pointCloud, BlockPos origin) {
        var mc = Minecraft.getInstance();
        if (mc.player == null) return;
        WitchHatAtelierMod.LOGGER.info(
                "[GestureCanvas] Closing — {} point(s). Sending to server.", pointCloud.size());
        PacketDistributor.sendToServer(new SaveGesturePayload(pointCloud, mc.player.position(), origin));
    }
}
