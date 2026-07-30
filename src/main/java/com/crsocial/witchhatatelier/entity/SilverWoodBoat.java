package com.crsocial.witchhatatelier.entity;

import com.crsocial.witchhatatelier.items.ModItems;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

/**
 * Silver wood's boat. Vanilla {@link Boat} keys its item drop and its client-side texture
 * off the closed {@code Boat.Type} enum, which cannot be extended with a new constant
 * without ASM/mixins — so this subclass carries its own {@link EntityType} and only
 * overrides {@link #getDropItem()}; rendering is handled entirely by a separate custom
 * renderer that never reads {@code getVariant()}.
 */
public class SilverWoodBoat extends Boat {

    public SilverWoodBoat(EntityType<? extends Boat> type, Level level) {
        super(type, level);
    }

    public SilverWoodBoat(Level level, double x, double y, double z) {
        this(ModEntities.SILVER_WOOD_BOAT.get(), level);
        this.setPos(x, y, z);
    }

    @Override
    public Item getDropItem() {
        return ModItems.SILVER_WOOD_BOAT.get();
    }
}
