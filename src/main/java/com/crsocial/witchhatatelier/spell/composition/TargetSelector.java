package com.crsocial.witchhatatelier.spell.composition;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * The one shared target service every seeking/spreading sign uses (§10):
 * "the nearest N things matching a predicate." Homing signs ask for N = 1;
 * chain signs ask for N = their {@link StackingMode#REPETITION} count.
 */
public final class TargetSelector {

    private TargetSelector() {
    }

    public static List<Entity> nearest(ServerLevel level, Vec3 origin, double searchRadius,
                                       TargetPredicate predicate, ExecutableSpell spell, int n) {
        if (n <= 0) return List.of();
        AABB box = new AABB(origin, origin).inflate(searchRadius);
        return level.getEntities((Entity) null, box, e -> predicate.valid(e, spell)).stream()
                .sorted(Comparator.comparingDouble(e -> e.position().distanceToSqr(origin)))
                .limit(n)
                .collect(Collectors.toList());
    }
}
