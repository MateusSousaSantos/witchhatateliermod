package com.crsocial.witchhatatelier.entity.child;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Composition helper that any spell-summoned entity can own to track its
 * {@link SpellChild children} and keep them attached as it moves. The host calls
 * {@link #followAll} from its server tick and {@link #detachAll} on removal, and
 * delegates NBT to {@link #save}/{@link #load}.
 *
 * <p>Children are persisted polymorphically: each entry stores its {@link
 * SpellChild#typeId()} alongside its own data, and {@link #load} rebuilds via the
 * {@link #FACTORIES} registry. New child types register themselves there.</p>
 */
public final class ChildManager {

    /** {@code typeId → factory} used to reconstruct children from NBT. */
    private static final Map<String, Function<CompoundTag, SpellChild>> FACTORIES = Map.of(
            LightBlockChild.TYPE, LightBlockChild::fromTag);

    private static final String LIST_KEY = "SpellChildren";

    private final List<SpellChild> children = new ArrayList<>();

    public void add(SpellChild child) {
        if (child != null) children.add(child);
    }

    /** Reposition every child to follow the parent now centred at {@code pos}. */
    public void followAll(ServerLevel level, Vec3 pos) {
        for (SpellChild child : children) {
            child.follow(level, pos);
        }
    }

    /** Tear down and forget every child. */
    public void detachAll(ServerLevel level) {
        for (SpellChild child : children) {
            child.detach(level);
        }
        children.clear();
    }

    public void save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (SpellChild child : children) {
            CompoundTag entry = new CompoundTag();
            entry.putString("type", child.typeId());
            CompoundTag data = new CompoundTag();
            child.save(data);
            entry.put("data", data);
            list.add(entry);
        }
        tag.put(LIST_KEY, list);
    }

    public void load(CompoundTag tag) {
        children.clear();
        if (!tag.contains(LIST_KEY)) return;
        ListTag list = tag.getList(LIST_KEY, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            Function<CompoundTag, SpellChild> factory = FACTORIES.get(entry.getString("type"));
            if (factory != null) {
                children.add(factory.apply(entry.getCompound("data")));
            }
        }
    }
}
