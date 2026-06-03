package com.crsocial.witchhatatelier.spell.meaning.effect;

import com.crsocial.witchhatatelier.WitchHatAtelierMod;
import com.crsocial.witchhatatelier.spell.cast.CastContext;
import com.crsocial.witchhatatelier.spell.meaning.BehaviorOp;
import com.crsocial.witchhatatelier.spell.meaning.ExecutableSpell;
import com.google.gson.JsonObject;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * {@code behavior_kind: "particles"} — an element-agnostic particle emitter.
 * Reads every {@link SpawnParticlesInstruction} on its ops and emits the
 * particles server-side via {@link ServerLevel#sendParticles}, scaling the count
 * by the spell's size and sign-stack count.
 *
 * <p>On a surface (placed-paper) cast {@link #execute} fires a single burst. On a
 * channeled hand cast the default {@code begin} fires one burst at the start and
 * {@link #tick} streams a fresh emission every server tick, following the
 * caster's live aim (origin is recomputed per tick by the cast manager).</p>
 */
public final class ParticleEffect implements EffectKind {

    public static final String KEY = "particles";

    /** Hard ceiling per emission so a heavily-stacked cast can't flood the network. */
    private static final int MAX_PARTICLES_PER_EMISSION = 256;

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public List<EffectInstruction> parsePayload(List<JsonObject> effects) {
        return EffectInstruction.parseAll(effects);
    }

    /** One-shot burst — instantaneous surface casts. */
    @Override
    public void execute(ServerLevel level, Player caster, ExecutableSpell spell) {
        emit(level, spell);
    }

    /** Channeled hand cast — stream a fresh emission each tick at the live origin. */
    @Override
    public void tick(CastContext ctx) {
        if (ctx.level() == null) return;
        emit(ctx.level(), ctx.spell());
    }

    @SuppressWarnings("unchecked")
    private void emit(ServerLevel level, ExecutableSpell spell) {
        var o = spell.originWorld();
        float size = Math.max(0.1f, spell.magnitude().sizeNormalized());

        for (BehaviorOp op : spell.ops()) {
            if (!KEY.equalsIgnoreCase(op.kind())) continue;
            if (!(op.payload() instanceof List<?> raw)) continue;

            for (EffectInstruction ins : (List<EffectInstruction>) raw) {
                if (!(ins instanceof SpawnParticlesInstruction sp)) continue;
                ParticleOptions particle = resolveParticle(sp.particleId());
                if (particle == null) continue;

                int count = Math.min(MAX_PARTICLES_PER_EMISSION,
                        Math.max(1, Math.round(sp.count() * size * Math.max(1, op.count()))));
                level.sendParticles(particle,
                        o.x, o.y + sp.verticalOffset(), o.z,
                        count, sp.spread(), sp.spread(), sp.spread(), sp.speed());
            }
        }
    }

    /**
     * Resolves a particle id to {@link ParticleOptions}. Vanilla
     * {@code SimpleParticleType}s are themselves {@code ParticleOptions}; particles
     * that need extra config (dust, block) aren't supported yet and are skipped.
     */
    @Nullable
    private static ParticleOptions resolveParticle(@Nullable ResourceLocation id) {
        if (id == null) return null;
        ParticleType<?> type = BuiltInRegistries.PARTICLE_TYPE.getOptional(id).orElse(null);
        if (type instanceof ParticleOptions opts) return opts;
        if (type != null) {
            WitchHatAtelierMod.LOGGER.warn(
                    "[{}] Particle '{}' needs extra options not supported yet; skipping.", KEY, id);
        } else {
            WitchHatAtelierMod.LOGGER.warn("[{}] Unknown particle id '{}'; skipping.", KEY, id);
        }
        return null;
    }
}
