package com.crsocial.witchhatatelier.events;

import com.crsocial.witchhatatelier.WitchHatAtelierMod;
import com.crsocial.witchhatatelier.client.gesture.GestureCanvasClient;
import com.crsocial.witchhatatelier.items.Wand;
import com.crsocial.witchhatatelier.spell.recognition.SpellTemplateLoader;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

import java.util.List;

/**
 * Global event handlers for the WitchHatAtelier mod, registered on the NeoForge event bus.
 */
@EventBusSubscriber(modid = WitchHatAtelierMod.MODID)
public class ModEvents {

    /**
     * Intercepts right-clicks on vanilla {@code minecraft:paper} in the main hand.
     * Opens the gesture canvas without replacing or modifying the vanilla paper item
     * registration — keeping full compatibility with other mods.
     *
     * <ul>
     *   <li>Main hand holds vanilla paper → canvas opens.</li>
     *   <li>If the player also holds a {@link Wand} in the off-hand → edit mode;
     *       otherwise → read-only mode.</li>
     *   <li>Right-clicking paper in the off-hand is ignored (Wand handles that side).</li>
     * </ul>
     *
     * When the player saves the drawing, {@code SaveGesturePayload} converts the
     * vanilla paper into a {@code spell_paper} item with the gesture data stored in it.
     */
    @SubscribeEvent
    public static void onRightClickVanillaPaper(PlayerInteractEvent.RightClickItem event) {
        // Only react to main-hand use so we don't conflict with wand/off-hand logic.
        if (event.getHand() != InteractionHand.MAIN_HAND) return;

        ItemStack stack = event.getItemStack();

        // Only intercept plain vanilla paper — SpellPaperItem already handles itself via Item#use().
        if (!stack.is(Items.PAPER)) return;

        // Cancel the vanilla "pass" result so nothing else runs for this interaction.
        event.setCanceled(true);

        // Canvas opening is client-only; the server will handle the save packet.
        if (event.getLevel().isClientSide()) {
            boolean hasWand = event.getEntity().getOffhandItem().getItem() instanceof Wand;
            openCanvas(stack, hasWand);
        }
    }

    @OnlyIn(Dist.CLIENT)
    private static void openCanvas(ItemStack stack, boolean editable) {
        // Vanilla paper has no gesture data yet — open with an empty point cloud.
        GestureCanvasClient.openCanvas(stack, List.of(), editable);
    }

    /**
     * Registers the datapack loader for gesture templates. Fires once at server
     * start and again on every {@code /reload}, so authors can iterate without
     * restarting.
     */
    @SubscribeEvent
    public static void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new SpellTemplateLoader());
    }
}


