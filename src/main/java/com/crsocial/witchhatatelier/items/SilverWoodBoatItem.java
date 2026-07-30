package com.crsocial.witchhatatelier.items;

import com.crsocial.witchhatatelier.entity.SilverWoodBoat;
import com.crsocial.witchhatatelier.entity.SilverWoodChestBoat;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.function.Predicate;

/**
 * Placement item for {@link SilverWoodBoat} / {@link SilverWoodChestBoat}. Copies vanilla
 * {@code BoatItem}'s placement logic, minus the {@code Boat.Type} field — silver wood has
 * only one texture, so there's nothing to select.
 */
public class SilverWoodBoatItem extends Item {

    private static final Predicate<Entity> ENTITY_PREDICATE = EntitySelector.NO_SPECTATORS.and(Entity::isPickable);

    private final boolean hasChest;

    public SilverWoodBoatItem(boolean hasChest, Item.Properties properties) {
        super(properties);
        this.hasChest = hasChest;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack itemstack = player.getItemInHand(hand);
        HitResult hitresult = getPlayerPOVHitResult(level, player, ClipContext.Fluid.ANY);
        if (hitresult.getType() == HitResult.Type.MISS) {
            return InteractionResultHolder.pass(itemstack);
        }

        Vec3 viewVec = player.getViewVector(1.0F);
        List<Entity> nearby = level.getEntities(player,
                player.getBoundingBox().expandTowards(viewVec.scale(5.0)).inflate(1.0), ENTITY_PREDICATE);
        if (!nearby.isEmpty()) {
            Vec3 eyePos = player.getEyePosition();
            for (Entity entity : nearby) {
                AABB aabb = entity.getBoundingBox().inflate(entity.getPickRadius());
                if (aabb.contains(eyePos)) {
                    return InteractionResultHolder.pass(itemstack);
                }
            }
        }

        if (hitresult.getType() != HitResult.Type.BLOCK) {
            return InteractionResultHolder.pass(itemstack);
        }

        Boat boat = getBoat(level, hitresult, itemstack, player);
        boat.setYRot(player.getYRot());
        if (!level.noCollision(boat, boat.getBoundingBox())) {
            return InteractionResultHolder.fail(itemstack);
        }

        if (!level.isClientSide) {
            level.addFreshEntity(boat);
            level.gameEvent(player, GameEvent.ENTITY_PLACE, hitresult.getLocation());
            itemstack.consume(1, player);
        }

        player.awardStat(Stats.ITEM_USED.get(this));
        return InteractionResultHolder.sidedSuccess(itemstack, level.isClientSide());
    }

    private Boat getBoat(Level level, HitResult hitResult, ItemStack stack, Player player) {
        Vec3 pos = hitResult.getLocation();
        Boat boat = this.hasChest
                ? new SilverWoodChestBoat(level, pos.x, pos.y, pos.z)
                : new SilverWoodBoat(level, pos.x, pos.y, pos.z);
        if (level instanceof ServerLevel serverLevel) {
            EntityType.<Boat>createDefaultStackConfig(serverLevel, stack, player).accept(boat);
        }

        return boat;
    }
}
