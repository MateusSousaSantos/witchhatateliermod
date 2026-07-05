package com.crsocial.witchhatatelier.client.gesture;

import com.crsocial.witchhatatelier.WitchHatAtelierMod;
import com.crsocial.witchhatatelier.items.PaperType;
import com.crsocial.witchhatatelier.items.SpellPaperItem;
import com.crsocial.witchhatatelier.network.SaveGesturePayload;
import com.crsocial.witchhatatelier.spell.feedback.InscriptionSummary;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

/**
 * Client-side entry point for opening the gesture canvas.
 *
 * <p>Provides two openCanvas variants:</p>
 * <ul>
 *   <li>{@link #openCanvas(ItemStack, List, boolean)} / {@link #openCanvas(ItemStack, List, boolean, BlockPos)} —
 *       derives the {@link CanvasProfile} from the item stack (used for in-hand drawing).</li>
 *   <li>{@link #openCanvas(PaperType, List, boolean, BlockPos)} —
 *       derives the profile from an explicit {@link PaperType} (used for placed-paper blocks).</li>
 * </ul>
 */
@OnlyIn(Dist.CLIENT)
public final class GestureCanvasClient {

    private GestureCanvasClient() {}

    // ── Public API ───────────────────────────────────────────────────────────────

    /** Opens the canvas for an in-hand item with no block origin. */
    public static void openCanvas(ItemStack stack, List<GesturePoint> preloaded, boolean editable) {
        openCanvas(stack, preloaded, editable, null);
    }

    /** Opens the canvas for an in-hand item, optionally bound to a placed-paper block. */
    public static void openCanvas(ItemStack stack, List<GesturePoint> preloaded,
                                  boolean editable, BlockPos origin) {
        CanvasProfile profile = resolveProfile(stack);
        // Parse the stamped inscription summary now — the screen never keeps the stack.
        InscriptionSummary summary = SpellPaperItem.readInscription(stack).orElse(null);
        Minecraft.getInstance().setScreen(
                new CanvasScreen(profile, preloaded, editable,
                        (points, ringIds) -> sendToServer(points, origin, ringIds), origin, summary));
    }

    /** Opens the canvas for a placed-paper block, using its stored {@link PaperType}. */
    public static void openCanvas(PaperType paperType, List<GesturePoint> preloaded,
                                  boolean editable, BlockPos origin) {
        openCanvas(new CanvasProfile.PaperTypeProfile(paperType), preloaded, editable, origin);
    }

    /** Opens the canvas for a block with an explicit {@link CanvasProfile} (e.g. the canvas plate). */
    public static void openCanvas(CanvasProfile profile, List<GesturePoint> preloaded,
                                  boolean editable, BlockPos origin) {
        Minecraft.getInstance().setScreen(
                new CanvasScreen(profile, preloaded, editable,
                        (points, ringIds) -> sendToServer(points, origin, ringIds), origin));
    }

    // ── Profile resolution ───────────────────────────────────────────────────────

    private static CanvasProfile resolveProfile(ItemStack stack) {
        if (stack.getItem() instanceof SpellPaperItem paper) {
            return new CanvasProfile.PaperTypeProfile(paper.getPaperType());
        }
        if (stack.is(Items.PAPER)) {
            return new CanvasProfile.PaperTypeProfile(PaperType.MEDIUM_SQUARE);
        }
        return new CanvasProfile.FallbackProfile();
    }

    // ── Network ──────────────────────────────────────────────────────────────────

    private static void sendToServer(List<GesturePoint> pointCloud, BlockPos origin,
                                     List<Integer> activationRingStrokeIds) {
        var mc = Minecraft.getInstance();
        if (mc.player == null) return;
        WitchHatAtelierMod.LOGGER.info(
                "[GestureCanvas] Closing — {} point(s), ring strokes={}. Sending to server.",
                pointCloud.size(), activationRingStrokeIds);
        PacketDistributor.sendToServer(new SaveGesturePayload(
                pointCloud, mc.player.position(), origin, activationRingStrokeIds));

        // Capture the same pipeline state on the client for the F9 debug viewer.
        // In single-player the TemplateRegistry singleton is shared with the server,
        // so this mirrors what the server saw.
        LastRecognitionDebug.capture(pointCloud, activationRingStrokeIds);
    }
}
