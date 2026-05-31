package com.crsocial.witchhatatelier.client.model;

import com.crsocial.witchhatatelier.WitchHatAtelierMod;
import com.crsocial.witchhatatelier.entity.PyreballEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

/**
 * GeckoLib model binding for {@link PyreballEntity}. The model and texture
 * live under {@code assets/witchhatateliermod/geo/pyreball/}, while the
 * animation lives under {@code assets/witchhatateliermod/animations/pyreball/}
 * (GeckoLib's conventional animations location), so all three resource
 * locations are specified explicitly.
 */
public class PyreballModel extends GeoModel<PyreballEntity> {

    private static final ResourceLocation MODEL =
            ResourceLocation.fromNamespaceAndPath(WitchHatAtelierMod.MODID, "geo/pyreball/fire.geo.json");
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(WitchHatAtelierMod.MODID, "geo/pyreball/texture.png");
    private static final ResourceLocation ANIMATION =
            ResourceLocation.fromNamespaceAndPath(WitchHatAtelierMod.MODID, "animations/pyreball/fire.animation.json");

    @Override
    public ResourceLocation getModelResource(PyreballEntity entity) {
        return MODEL;
    }

    @Override
    public ResourceLocation getTextureResource(PyreballEntity entity) {
        return TEXTURE;
    }

    @Override
    public ResourceLocation getAnimationResource(PyreballEntity entity) {
        return ANIMATION;
    }
}
