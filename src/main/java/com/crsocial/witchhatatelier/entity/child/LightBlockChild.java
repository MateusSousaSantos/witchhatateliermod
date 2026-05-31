package com.crsocial.witchhatatelier.entity.child;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * A {@link SpellChild} that parks a {@link Blocks#LIGHT} block at the parent's
 * position so the orb lights up the world around it. As the parent moves, the light
 * hops block-to-block to follow: the old block is cleared and a fresh one placed at
 * the new cell (only into air, mirroring the original behaviour).
 */
public final class LightBlockChild implements SpellChild {

    public static final String TYPE = "light_block";

    /** Light level emitted by the parked {@link Blocks#LIGHT} block (0-15). */
    private final int lightLevel;
    /** Cell the light currently occupies, or {@code null} when none is placed. */
    private BlockPos current;

    public LightBlockChild(int lightLevel) {
        this.lightLevel = Math.max(0, Math.min(15, lightLevel));
    }

    @Override
    public String typeId() {
        return TYPE;
    }

    @Override
    public void follow(ServerLevel level, Vec3 pos) {
        BlockPos target = BlockPos.containing(pos);
        if (target.equals(current)) return;
        clear(level);
        BlockState here = level.getBlockState(target);
        if (here.isAir()) {
            BlockState lit = Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, lightLevel);
            level.setBlock(target, lit, Block.UPDATE_ALL);
            current = target.immutable();
        }
    }

    @Override
    public void detach(ServerLevel level) {
        clear(level);
    }

    private void clear(ServerLevel level) {
        if (current == null) return;
        if (level.getBlockState(current).is(Blocks.LIGHT)) {
            level.setBlock(current, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
        current = null;
    }

    @Override
    public void save(CompoundTag tag) {
        tag.putInt("LightLevel", lightLevel);
        if (current != null) {
            tag.putInt("X", current.getX());
            tag.putInt("Y", current.getY());
            tag.putInt("Z", current.getZ());
        }
    }

    @Override
    public void load(CompoundTag tag) {
        if (tag.contains("X")) {
            current = new BlockPos(tag.getInt("X"), tag.getInt("Y"), tag.getInt("Z"));
        }
    }

    /** Factory for {@link ChildManager} NBT reconstruction. */
    public static LightBlockChild fromTag(CompoundTag tag) {
        LightBlockChild child = new LightBlockChild(tag.getInt("LightLevel"));
        child.load(tag);
        return child;
    }
}
