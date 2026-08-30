package com.crsocial.witchhatatelier.spell.composition.material;

import net.minecraft.resources.ResourceLocation;

/**
 * A material that manifests as a custom model/entity rather than a vanilla
 * block or particle — reserved for a bespoke convergence or override that
 * wants its own asset. Unused by any default registration today.
 */
public record ModelMaterial(ResourceLocation modelId) implements Material {
}
