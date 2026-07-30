package com.crsocial.witchhatatelier.client.renderer;

import com.crsocial.witchhatatelier.WitchHatAtelierMod;
import com.crsocial.witchhatatelier.entity.SilverWoodBoat;
import com.crsocial.witchhatatelier.entity.SilverWoodChestBoat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.model.BoatModel;
import net.minecraft.client.model.ChestBoatModel;
import net.minecraft.client.model.ListModel;
import net.minecraft.client.model.WaterPatchModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.vehicle.Boat;
import org.joml.Quaternionf;

/**
 * Renders both {@link SilverWoodBoat} and {@link SilverWoodChestBoat}. Vanilla's own
 * {@code BoatRenderer} bakes a {@code Map<Boat.Type, ...>} at construction (a closed
 * vanilla enum we can't add a constant to) — since silver wood only ever needs one
 * texture, this renderer just bakes a single model/texture pair instead, reusing vanilla's
 * {@link BoatModel}/{@link ChestBoatModel} geometry and copying {@code BoatRenderer}'s
 * {@code render()} transform logic.
 */
public class SilverWoodBoatRenderer extends EntityRenderer<Boat> {

    public static final ModelLayerLocation BOAT_LAYER = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath(WitchHatAtelierMod.MODID, "silver_wood_boat"), "main");
    public static final ModelLayerLocation CHEST_BOAT_LAYER = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath(WitchHatAtelierMod.MODID, "silver_wood_chest_boat"), "main");

    private static final ResourceLocation BOAT_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            WitchHatAtelierMod.MODID, "textures/entity/silver_wood_boat.png");
    private static final ResourceLocation CHEST_BOAT_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            WitchHatAtelierMod.MODID, "textures/entity/silver_wood_chest_boat.png");

    private final ListModel<Boat> model;
    private final ResourceLocation texture;

    public SilverWoodBoatRenderer(EntityRendererProvider.Context context, boolean chest) {
        super(context);
        this.shadowRadius = 0.8F;
        ModelPart part = context.bakeLayer(chest ? CHEST_BOAT_LAYER : BOAT_LAYER);
        this.model = chest ? new ChestBoatModel(part) : new BoatModel(part);
        this.texture = chest ? CHEST_BOAT_TEXTURE : BOAT_TEXTURE;
    }

    @Override
    public ResourceLocation getTextureLocation(Boat entity) {
        return this.texture;
    }

    @Override
    public void render(Boat entity, float entityYaw, float partialTick, PoseStack poseStack,
                        MultiBufferSource buffer, int packedLight) {
        poseStack.pushPose();
        poseStack.translate(0.0F, 0.375F, 0.0F);
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - entityYaw));
        float hurtTime = entity.getHurtTime() - partialTick;
        float damage = Math.max(entity.getDamage() - partialTick, 0.0F);
        if (hurtTime > 0.0F) {
            poseStack.mulPose(Axis.XP.rotationDegrees(Mth.sin(hurtTime) * hurtTime * damage / 10.0F * entity.getHurtDir()));
        }

        float bubbleAngle = entity.getBubbleAngle(partialTick);
        if (!Mth.equal(bubbleAngle, 0.0F)) {
            poseStack.mulPose(new Quaternionf().setAngleAxis(bubbleAngle * (float) (Math.PI / 180.0), 1.0F, 0.0F, 1.0F));
        }

        poseStack.scale(-1.0F, -1.0F, 1.0F);
        poseStack.mulPose(Axis.YP.rotationDegrees(90.0F));
        this.model.setupAnim(entity, partialTick, 0.0F, -0.1F, 0.0F, 0.0F);
        VertexConsumer vertexConsumer = buffer.getBuffer(this.model.renderType(this.texture));
        this.model.renderToBuffer(poseStack, vertexConsumer, packedLight, OverlayTexture.NO_OVERLAY);
        if (!entity.isUnderWater() && this.model instanceof WaterPatchModel waterPatchModel) {
            VertexConsumer waterConsumer = buffer.getBuffer(RenderType.waterMask());
            waterPatchModel.waterPatch().render(poseStack, waterConsumer, packedLight, OverlayTexture.NO_OVERLAY);
        }

        poseStack.popPose();
        super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
    }
}
