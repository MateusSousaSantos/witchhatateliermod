package com.crsocial.witchhatatelier.entity;

import com.crsocial.witchhatatelier.items.ModItems;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.entity.vehicle.ChestBoat;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

/**
 * Silver wood's chest boat. Vanilla {@link ChestBoat} already implements all of its
 * container/loot-table logic generically (only {@link #getDropItem()} switches on the
 * closed {@code Boat.Type} enum), so this subclass just supplies its own {@link EntityType}
 * and item drop — see {@link SilverWoodBoat} for why a full custom renderer is needed too.
 */
public class SilverWoodChestBoat extends ChestBoat {

    public SilverWoodChestBoat(EntityType<? extends Boat> type, Level level) {
        super(type, level);
    }

    public SilverWoodChestBoat(Level level, double x, double y, double z) {
        this(ModEntities.SILVER_WOOD_CHEST_BOAT.get(), level);
        this.setPos(x, y, z);
    }

    @Override
    public Item getDropItem() {
        return ModItems.SILVER_WOOD_CHEST_BOAT.get();
    }
}
