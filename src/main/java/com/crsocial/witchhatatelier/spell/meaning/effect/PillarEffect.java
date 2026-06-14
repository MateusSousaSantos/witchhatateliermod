package com.crsocial.witchhatatelier.spell.meaning.effect;

import com.crsocial.witchhatatelier.spell.cast.CastContext;
import com.crsocial.witchhatatelier.spell.compiler.SignType;
import com.crsocial.witchhatatelier.spell.meaning.BehaviorOp;
import com.crsocial.witchhatatelier.spell.meaning.ExecutableSpell;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;

/**
 * Shared base for the column-shaped "pillar" effects — one per element (earth,
 * water, fire, air). The base owns the manifestation loop: a pillar either
 * <em>creates</em> (places blocks along the column and/or draws a particle trail
 * and applies a per-element entity effect) or, when a {@code CRUSH} Force sign
 * rides the same ring, <em>destroys</em> via {@link #crush}.
 *
 * <p>Subclasses supply the element flavour by overriding the hooks:</p>
 * <ul>
 *   <li>{@link #defaultBlock()} — the block laid along the column ({@code null} =
 *       blockless, e.g. air's wind column).</li>
 *   <li>{@link #trailParticle()} — the particle the column is drawn from
 *       ({@code null} = no trail).</li>
 *   <li>{@link #affectEntities} — the "additional effect" on entities caught in
 *       the column (air pushes; default is a no-op).</li>
 *   <li>{@link #crush} — the Crush-mode transform (earth carves, water drains;
 *       default is a no-op for elements without one yet).</li>
 * </ul>
 *
 * <p>Geometry stays in {@link PillarEffects}/{@link ColumnPlacer}; this class only
 * decides <em>which</em> path to run for each op of its {@link #key()}.</p>
 */
public abstract class PillarEffect extends AbstractPillarEffect {

    /** Block placed along the column in create-mode; {@code null} for a blockless pillar. */
    protected abstract @Nullable Block defaultBlock();

    /** Particle the column trail is drawn from; {@code null} for no trail. */
    protected abstract @Nullable ParticleOptions trailParticle();

    /**
     * Per-element effect on entities in the column (create-mode only). Default
     * no-op. {@code oneShot} is {@code true} for an instantaneous surface cast and
     * {@code false} for a per-tick channel tick, letting a subclass scale strength.
     * {@code caster} is the player who cast the spell (for damage attribution); it
     * may be {@code null} when the cast has no live caster.
     */
    protected void affectEntities(ServerLevel level, @Nullable Player caster,
                                  ExecutableSpell spell, BehaviorOp op, boolean oneShot) {
        // No-op by default.
    }

    /**
     * Crush-mode transform — the destructive counterpart to create-mode, fired
     * once when {@link #isCrush} holds. Default no-op (elements without a Crush
     * behaviour keep create-mode semantics; the engine only routes CRUSH here, so
     * an unstubbed element simply does nothing rather than placing blocks).
     */
    protected void crush(ServerLevel level, Player caster, ExecutableSpell spell, BehaviorOp op) {
        // No-op by default.
    }

    /** Whether a CRUSH Force sign rides this op, routing it to {@link #crush}. */
    protected final boolean isCrush(BehaviorOp op) {
        return op.hasModifier(SignType.CRUSH);
    }

    @Override
    public void execute(ServerLevel level, Player caster, ExecutableSpell spell) {
        for (BehaviorOp op : spell.ops()) {
            if (!key().equalsIgnoreCase(op.kind())) continue;
            if (isCrush(op)) {
                crush(level, caster, spell, op);
                continue;
            }
            Block block = defaultBlock();
            if (block != null) PillarEffects.executeColumn(level, spell, op, block, key());
            ParticleOptions particle = trailParticle();
            if (particle != null) PillarEffects.emitColumnTrail(level, spell, op, particle);
            affectEntities(level, caster, spell, op, true);
        }
    }

    /**
     * Channel tick: re-draw the trail and re-apply the entity effect so a held
     * hand cast keeps the column alive. Blocks are placed once on {@link #begin}
     * (the default {@link #execute}); crush ops are one-shot and skipped here.
     */
    @Override
    public void tick(CastContext ctx) {
        if (ctx.level() == null) return;
        for (BehaviorOp op : ctx.spell().ops()) {
            if (!key().equalsIgnoreCase(op.kind())) continue;
            if (isCrush(op)) continue; // one-shot on begin
            ParticleOptions particle = trailParticle();
            if (particle != null) PillarEffects.emitColumnTrail(ctx.level(), ctx.spell(), op, particle);
            affectEntities(ctx.level(), ctx.caster(), ctx.spell(), op, false);
        }
    }
}
