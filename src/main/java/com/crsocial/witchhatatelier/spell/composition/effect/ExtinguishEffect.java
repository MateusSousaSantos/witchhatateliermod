package com.crsocial.witchhatatelier.spell.composition.effect;

import com.crsocial.witchhatatelier.spell.compiler.EffectType;
import com.crsocial.witchhatatelier.spell.composition.CastContext;
import com.crsocial.witchhatatelier.spell.composition.StackingMode;
import com.crsocial.witchhatatelier.spell.composition.Trigger;
import com.crsocial.witchhatatelier.spell.composition.manifest.Manifestation;
import com.crsocial.witchhatatelier.spell.composition.manifest.NoneManifestation;
import com.crsocial.witchhatatelier.spell.composition.material.Material;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import org.joml.Vector3f;

import java.util.Optional;

/**
 * Generic default for {@link EffectType#EXTINGUISH} — the engine's reactive
 * (§8) worked example: watches for fire near the cast origin and, when the
 * trigger fires, puts it out. Has no gesture template yet ({@code
 * ElementType}/{@code FormType}/{@code EffectType} javadoc), so it's
 * undrawable in-game today — exercised only by gametests that hand-build a
 * {@code SpellGraph} with an {@code EXTINGUISH} occurrence, bypassing the
 * recognizer. {@code CompositionEngine} attaches {@link #trigger()} to the
 * compiled {@code ExecutableSpell}; nothing evaluates it yet — no reactive
 * runtime exists (see this engine's own scope note).
 */
public final class ExtinguishEffect implements Effect {

    private static final int WATCH_RADIUS = 3;

    @Override
    public EffectType type() {
        return EffectType.EXTINGUISH;
    }

    @Override
    public StackingMode stacking() {
        return StackingMode.MODIFIER;
    }

    /** Armed and idle at compose time — nothing manifests up front; only the {@link Trigger} matters. */
    @Override
    public Manifestation carry(Material working, CastContext ctx) {
        return new NoneManifestation();
    }

    /** What a reactive runtime would poll: is there fire within {@link #WATCH_RADIUS} of the cast origin? */
    @Override
    public Optional<Trigger> trigger() {
        return Optional.of((level, spell) -> nearestFire(level, spell.origin()).isPresent());
    }

    /** The per-event action a reactive runtime would run when {@link #trigger()} fires. */
    public void onTrigger(ServerLevel level, Vector3f origin) {
        nearestFire(level, origin).ifPresent(pos -> level.removeBlock(pos, false));
    }

    private static Optional<BlockPos> nearestFire(ServerLevel level, Vector3f origin) {
        BlockPos center = BlockPos.containing(origin.x, origin.y, origin.z);
        for (BlockPos pos : BlockPos.betweenClosed(
                center.offset(-WATCH_RADIUS, -WATCH_RADIUS, -WATCH_RADIUS),
                center.offset(WATCH_RADIUS, WATCH_RADIUS, WATCH_RADIUS))) {
            if (level.getBlockState(pos).is(Blocks.FIRE)) return Optional.of(pos.immutable());
        }
        return Optional.empty();
    }
}
