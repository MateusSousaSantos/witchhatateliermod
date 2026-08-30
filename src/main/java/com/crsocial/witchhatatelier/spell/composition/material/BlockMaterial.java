package com.crsocial.witchhatatelier.spell.composition.material;

import net.minecraft.world.level.block.Block;

/** A material that manifests as a placeable block. */
public record BlockMaterial(Block block) implements Material {
}
