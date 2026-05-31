package com.crsocial.witchhatatelier.entity;

import com.crsocial.witchhatatelier.entity.child.ChildManager;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Entity.RemovalReason;
import net.minecraft.world.level.Level;
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
 * lifetime. While alive, its {@link #children()} (e.g. a light block) are kept
 * attached and follow it around — see {@link ChildManager}.
 */
public class PyreballEntity extends Entity implements GeoEntity {

    private static final EntityDataAccessor<Integer> LIFETIME =
            SynchedEntityData.defineId(PyreballEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> MAX_SCALE =
            SynchedEntityData.defineId(PyreballEntity.class, EntityDataSerializers.FLOAT);

    /** Smallest scale the orb shrinks to right before despawning. */
    public static final float MIN_SCALE = 0.2f;
    /** Server ticks between looped fire-ambient sounds while the orb is alive. */
    private static final int LOOP_SOUND_INTERVAL = 40;

    private static final SoundEvent SPAWN_SOUND = SoundEvents.BLAZE_SHOOT;
    private static final SoundEvent LOOP_SOUND = SoundEvents.FIRE_AMBIENT;

    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    /** Satellites (e.g. the light block) that follow the orb around. */
    private final ChildManager children = new ChildManager();

    private int age;

    // Client-side smoothing: base Entity#lerpTo snaps instantly, which looks janky
    // while the orb is driven across the world each tick to follow the caster's aim.
    // We store the latest synced position and ease toward it over a few client ticks.
    private double lerpTargetX, lerpTargetY, lerpTargetZ;
    private int lerpSteps;

    public PyreballEntity(EntityType<? extends PyreballEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    /** The orb's child satellites; the spell effect attaches children here at spawn. */
    public ChildManager children() {
        return children;
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

        if (level().isClientSide) {
            tickClientLerp();
            return;
        }

        children.followAll((ServerLevel) level(), position());
        if (age == 0) {
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

    /**
     * Captures the latest server-synced position instead of snapping to it (the base
     * {@link Entity#lerpTo} behaviour). {@link #tickClientLerp} then eases toward it,
     * and the renderer interpolates {@code xOld→x} between frames — smooth motion.
     */
    @Override
    public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps) {
        this.lerpTargetX = x;
        this.lerpTargetY = y;
        this.lerpTargetZ = z;
        this.lerpSteps = Math.max(steps, 1);
    }

    private void tickClientLerp() {
        if (lerpSteps <= 0) return;
        double nx = getX() + (lerpTargetX - getX()) / lerpSteps;
        double ny = getY() + (lerpTargetY - getY()) / lerpSteps;
        double nz = getZ() + (lerpTargetZ - getZ()) / lerpSteps;
        lerpSteps--;
        setPos(nx, ny, nz);
    }

    @Override
    public void remove(@NotNull RemovalReason reason) {
        if (level() instanceof ServerLevel serverLevel) {
            children.detachAll(serverLevel);
        }
        super.remove(reason);
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
        children.load(tag);
    }

    @Override
    protected void addAdditionalSaveData(@NotNull CompoundTag tag) {
        tag.putInt("Age", age);
        tag.putInt("LifetimeTicks", getLifetimeTicks());
        tag.putFloat("MaxScale", getMaxScale());
        children.save(tag);
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
