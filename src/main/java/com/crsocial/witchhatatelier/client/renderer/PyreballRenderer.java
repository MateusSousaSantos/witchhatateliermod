package com.crsocial.witchhatatelier.client.renderer;

import com.crsocial.witchhatatelier.client.model.PyreballModel;
import com.crsocial.witchhatatelier.entity.PyreballEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/**
 * GeckoLib renderer for {@link PyreballEntity}. Applies the entity's current
 * (lifetime-interpolated) scale to the pose so the orb visibly shrinks over
 * its lifetime.
 */
public class PyreballRenderer extends GeoEntityRenderer<PyreballEntity> {

    public PyreballRenderer(EntityRendererProvider.Context context) {
        super(context, new PyreballModel());
        this.shadowRadius = 0.0f;
    }

    @Override
    public void preRender(PoseStack poseStack, PyreballEntity animatable, BakedGeoModel model,
                          @Nullable MultiBufferSource bufferSource, @Nullable VertexConsumer buffer,
                          boolean isReRender, float partialTick, int packedLight,
                          int packedOverlay, int colour) {
        float s = animatable.getCurrentScale();
        poseStack.scale(s, s, s);
        super.preRender(poseStack, animatable, model, bufferSource, buffer, isReRender,
                partialTick, packedLight, packedOverlay, colour);
    }
}
