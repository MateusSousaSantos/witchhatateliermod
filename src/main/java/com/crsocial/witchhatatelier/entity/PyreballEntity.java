package com.crsocial.witchhatatelier.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Entity.RemovalReason;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * The Fire + Levitation spell summon: a hovering orb of fire. The orb's
 * visual size starts at {@link #getMaxScale()} (set by the spell from the
 * caster's power) and shrinks linearly toward {@link #MIN_SCALE} over its
 * lifetime. While alive, a {@link Blocks#LIGHT} block is parked at its
 * position so the world lights up around it.
 */
public class PyreballEntity extends Entity implements GeoEntity {

    private static final EntityDataAccessor<Integer> LIFETIME =
            SynchedEntityData.defineId(PyreballEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> MAX_SCALE =
            SynchedEntityData.defineId(PyreballEntity.class, EntityDataSerializers.FLOAT);

    /** Smallest scale the orb shrinks to right before despawning. */
    public static final float MIN_SCALE = 0.2f;
    /** Light level emitted by the parked {@link Blocks#LIGHT} block (0-15). */
    private static final int LIGHT_LEVEL = 15;
    /** Server ticks between looped fire-ambient sounds while the orb is alive. */
    private static final int LOOP_SOUND_INTERVAL = 40;

    private static final SoundEvent SPAWN_SOUND = SoundEvents.BLAZE_SHOOT;
    private static final SoundEvent LOOP_SOUND = SoundEvents.FIRE_AMBIENT;

    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    private int age;
    private boolean placedLightHere;
    private BlockPos lightPos;

    public PyreballEntity(EntityType<? extends PyreballEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    public void setLifetimeTicks(int ticks) {
        this.entityData.set(LIFETIME, Math.max(1, ticks));
    }

    public int getLifetimeTicks() {
        return this.entityData.get(LIFETIME);
    }

    public void setMaxScale(float scale) {
        this.entityData.set(MAX_SCALE, Math.max(MIN_SCALE, scale));
    }

    public float getMaxScale() {
        return this.entityData.get(MAX_SCALE);
    }

    /**
     * Current rendered scale: linearly interpolated from {@link #getMaxScale()}
     * at spawn down to {@link #MIN_SCALE} when {@code age == lifetimeTicks}.
     */
    public float getCurrentScale() {
        int lifetime = Math.max(1, getLifetimeTicks());
        float t = Mth.clamp((float) age / (float) lifetime, 0f, 1f);
        return Mth.lerp(t, getMaxScale(), MIN_SCALE);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.@NotNull Builder builder) {
        builder.define(LIFETIME, 60);
        builder.define(MAX_SCALE, 1.0f);
    }

    @Override
    public void tick() {
        super.tick();
        this.setDeltaMovement(0, 0, 0);
        if (!level().isClientSide) {
            if (age == 0) {
                tryPlaceLight();
                level().playSound(null, getX(), getY(), getZ(),
                        SPAWN_SOUND, SoundSource.PLAYERS, 1.2f, 0.9f);
            }
            if (age > 0 && age % LOOP_SOUND_INTERVAL == 0) {
                level().playSound(null, getX(), getY(), getZ(),
                        LOOP_SOUND, SoundSource.BLOCKS, 0.6f, 1.0f);
            }
            age++;
            if (age >= getLifetimeTicks()) {
                discard();
            }
        }
    }

    @Override
    public void remove(@NotNull RemovalReason reason) {
        clearLight();
        super.remove(reason);
    }

    private void tryPlaceLight() {
        if (placedLightHere) return;
        BlockPos pos = blockPosition();
        BlockState here = level().getBlockState(pos);
        if (!here.isAir()) return;
        BlockState lit = Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, LIGHT_LEVEL);
        level().setBlock(pos, lit, Block.UPDATE_ALL);
        placedLightHere = true;
        lightPos = pos.immutable();
    }

    private void clearLight() {
        if (!placedLightHere || lightPos == null || level().isClientSide) return;
        BlockState current = level().getBlockState(lightPos);
        if (current.is(Blocks.LIGHT)) {
            level().setBlock(lightPos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
        placedLightHere = false;
        lightPos = null;
    }

    @Override
    protected void readAdditionalSaveData(@NotNull CompoundTag tag) {
        age = tag.getInt("Age");
        if (tag.contains("LifetimeTicks")) {
            setLifetimeTicks(tag.getInt("LifetimeTicks"));
        }
        if (tag.contains("MaxScale")) {
            setMaxScale(tag.getFloat("MaxScale"));
        }
        placedLightHere = tag.getBoolean("PlacedLight");
        if (tag.contains("LightX")) {
            lightPos = new BlockPos(tag.getInt("LightX"), tag.getInt("LightY"), tag.getInt("LightZ"));
        }
    }

    @Override
    protected void addAdditionalSaveData(@NotNull CompoundTag tag) {
        tag.putInt("Age", age);
        tag.putInt("LifetimeTicks", getLifetimeTicks());
        tag.putFloat("MaxScale", getMaxScale());
        tag.putBoolean("PlacedLight", placedLightHere);
        if (lightPos != null) {
            tag.putInt("LightX", lightPos.getX());
            tag.putInt("LightY", lightPos.getY());
            tag.putInt("LightZ", lightPos.getZ());
        }
    }

    // ── GeckoLib ─────────────────────────────────────────────────────────────

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "controller", 0, this::idle));
    }

    private <E extends GeoEntity> PlayState idle(AnimationState<E> state) {
        state.getController().setAnimation(IDLE);
        return PlayState.CONTINUE;
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
