package com.crsocial.witchhatatelier.client.renderer;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class ModRenderTypes {

    private ModRenderTypes() {}

    /**
     * Translucent, no-texture quad layer used to paint ink strokes on placed paper blocks.
     * Uses POSITION_COLOR vertex format (no UV needed — strokes are solid-colour geometry).
     * Polygon offset layering prevents z-fighting with the paper block texture.
     */
    public static final RenderType INK_STROKE = RenderType.create(
            "witchhatateliermod:ink_stroke",
            DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.QUADS,
            4096,
            false,
            false,
            RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.POSITION_COLOR_SHADER)
                    .setLayeringState(RenderStateShard.POLYGON_OFFSET_LAYERING)
                    .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                    .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
                    .setOutputState(RenderStateShard.TRANSLUCENT_TARGET)
                    .setCullState(RenderStateShard.NO_CULL)
                    .createCompositeState(false)
    );
}
