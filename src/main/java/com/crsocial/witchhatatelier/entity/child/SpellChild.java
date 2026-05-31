package com.crsocial.witchhatatelier.entity.child;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

/**
 * A satellite of a spell-summoned parent entity (e.g. {@link
 * com.crsocial.witchhatatelier.entity.PyreballEntity}). The parent owns a list of
 * children via a {@link ChildManager} and keeps them attached as it moves: every
 * server tick it calls {@link #follow}, and on removal it calls {@link #detach}.
 *
 * <p>Children may be blocks parked in the world (see {@link LightBlockChild}) or
 * sub-entities slaved to an offset — the parent does not care which. All methods run
 * on the server thread only.</p>
 */
public interface SpellChild {

    /** Stable id used to round-trip this child through NBT (see {@link ChildManager}). */
    String typeId();

    /** Reposition this child to follow the parent now centred at {@code pos}. */
    void follow(ServerLevel level, Vec3 pos);

    /** Tear down — remove placed blocks / discard sub-entities. */
    void detach(ServerLevel level);

    /** Persist child state. Default no-op. */
    default void save(CompoundTag tag) {}

    /** Restore child state. Default no-op. */
    default void load(CompoundTag tag) {}
}
