package com.crsocial.witchhatatelier.client;

import com.crsocial.witchhatatelier.WitchHatAtelierMod;
import com.zigythebird.playeranim.animation.PlayerAnimationController;
import com.zigythebird.playeranim.api.PlayerAnimationAccess;
import com.zigythebird.playeranim.api.PlayerAnimationFactory;
import com.zigythebird.playeranimcore.animation.layered.IAnimation;
import com.zigythebird.playeranimcore.animation.layered.modifier.AbstractFadeModifier;
import com.zigythebird.playeranimcore.api.firstPerson.FirstPersonConfiguration;
import com.zigythebird.playeranimcore.api.firstPerson.FirstPersonMode;
import com.zigythebird.playeranimcore.easing.EasingType;
import com.zigythebird.playeranimcore.enums.PlayState;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;

/**
 * Client-side PlayerAnimationLib glue for the spell-casting player animation.
 *
 * <p>{@link #register} adds a per-player animation layer during client setup; the
 * layer starts idle (its state handler always returns {@link PlayState#STOP}) and
 * is driven imperatively by {@link #setCasting}, which {@link ClientCastingState}
 * calls whenever a player's channeling state changes.</p>
 *
 * <p>The animation itself lives at
 * {@code assets/witchhatateliermod/player_animations/casting.json} and is loaded
 * by PAL under the id {@link #ANIM_ID}.</p>
 */
public final class CastingAnimation {

    /** Id of the animation layer registered on every player. */
    public static final ResourceLocation LAYER_ID =
            ResourceLocation.fromNamespaceAndPath(WitchHatAtelierMod.MODID, "casting");

    /** Id of the loaded animation (the "casting" entry in casting.json). */
    public static final ResourceLocation ANIM_ID =
            ResourceLocation.fromNamespaceAndPath(WitchHatAtelierMod.MODID, "casting");

    /** Fade length, in ticks, used when raising and lowering the arms. */
    private static final int FADE_TICKS = 5;

    private CastingAnimation() {}

    /** Registers the casting animation layer. Call from {@code FMLClientSetupEvent}. */
    public static void register() {
        PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(
                LAYER_ID, 1500, CastingAnimation::createController);
    }

    private static PlayerAnimationController createController(AbstractClientPlayer player) {
        PlayerAnimationController controller = new PlayerAnimationController(player,
                (c, state, animSetter) -> PlayState.STOP);
        // Render the animated arms in first person, so the caster sees the cast from their
        // own view instead of the static vanilla arms. Show only the off-hand (left) item —
        // the paper — and hide the main-hand (right) item, the wand/pen, so nothing but the
        // paper is visible while casting. Only takes effect while this layer is playing.
        controller.setFirstPersonMode(FirstPersonMode.THIRD_PERSON_MODEL);
        controller.setFirstPersonConfiguration(new FirstPersonConfiguration()
                .setShowLeftArm(true)
                .setShowRightArm(true)
                .setShowLeftItem(true)
                .setShowRightItem(false));
        return controller;
    }

    /** Starts or stops the casting animation on the given player, fading in/out. */
    public static void setCasting(AbstractClientPlayer player, boolean active) {
        IAnimation layer = PlayerAnimationAccess.getPlayerAnimationLayer(player, LAYER_ID);
        if (!(layer instanceof PlayerAnimationController controller)) {
            return;
        }
        if (active) {
            controller.replaceAnimationWithFade(
                    AbstractFadeModifier.standardFadeIn(FADE_TICKS, EasingType.EASE_IN_OUT_SINE),
                    ANIM_ID);
        } else {
            controller.stop();
        }
    }
}
