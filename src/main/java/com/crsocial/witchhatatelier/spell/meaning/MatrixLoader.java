package com.crsocial.witchhatatelier.spell.meaning;

import com.crsocial.witchhatatelier.WitchHatAtelierMod;
import com.crsocial.witchhatatelier.spell.compiler.SigilType;
import com.crsocial.witchhatatelier.spell.compiler.SignType;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Loads {@code (sigil, sign)} matrix cells from
 * {@code data/<namespace>/spell_matrix/<sigil>/<sign>.json}.
 *
 * <p>The path itself carries the key: a file at {@code .../spell_matrix/earth/column.json}
 * registers the {@code (EARTH, COLUMN)} cell. The {@code sigil} / {@code sign}
 * fields inside the JSON are validated against the path for sanity.</p>
 *
 * <p>Missing cells are not an error — the meaning engine simply returns empty
 * for any pair without a registered cell. See
 * {@code docs/magic_system/03_meaning_engine.md}.</p>
 */
public final class MatrixLoader extends SimpleJsonResourceReloadListener {

    private static final Gson GSON = new Gson();
    private static final String DIRECTORY = "spell_matrix";

    public MatrixLoader() {
        super(GSON, DIRECTORY);
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> map,
                         @NotNull ResourceManager mgr,
                         @NotNull ProfilerFiller profiler) {
        MatrixRegistry registry = MatrixRegistry.get();
        registry.clear();

        int loaded = 0;
        for (var entry : map.entrySet()) {
            ResourceLocation id = entry.getKey();
            try {
                Optional<MatrixEntry> parsed = parseEntry(id, entry.getValue().getAsJsonObject());
                if (parsed.isPresent()) {
                    registry.register(parsed.get());
                    loaded++;
                }
            } catch (Exception e) {
                WitchHatAtelierMod.LOGGER.error(
                        "[MatrixLoader] Failed to load matrix cell '{}': {}", id, e.getMessage());
            }
        }

        WitchHatAtelierMod.LOGGER.info(
                "[MatrixLoader] Loaded {} spell matrix cell(s).", loaded);
    }

    private static Optional<MatrixEntry> parseEntry(ResourceLocation id, JsonObject root) {
        // The path /<sigil>/<sign>.json carries the key.
        String[] parts = id.getPath().split("/");
        if (parts.length < 2) {
            WitchHatAtelierMod.LOGGER.warn(
                    "[MatrixLoader] '{}' is not at the expected '<sigil>/<sign>.json' depth — skipped.", id);
            return Optional.empty();
        }
        String sigilSlug = parts[parts.length - 2];
        String signSlug = parts[parts.length - 1];

        Optional<SigilType> sigil = SigilType.fromSpellName(sigilSlug);
        Optional<SignType> sign = SignType.fromSpellName(signSlug);
        if (sigil.isEmpty() || sign.isEmpty()) {
            WitchHatAtelierMod.LOGGER.warn(
                    "[MatrixLoader] '{}' does not name a known (sigil, sign) pair — skipped.", id);
            return Optional.empty();
        }

        if (root.has("sigil") && !root.get("sigil").getAsString().equalsIgnoreCase(sigilSlug)) {
            WitchHatAtelierMod.LOGGER.warn(
                    "[MatrixLoader] '{}' JSON sigil='{}' disagrees with path sigil='{}'; trusting path.",
                    id, root.get("sigil").getAsString(), sigilSlug);
        }
        if (root.has("sign") && !root.get("sign").getAsString().equalsIgnoreCase(signSlug)) {
            WitchHatAtelierMod.LOGGER.warn(
                    "[MatrixLoader] '{}' JSON sign='{}' disagrees with path sign='{}'; trusting path.",
                    id, root.get("sign").getAsString(), signSlug);
        }

        String behaviorKind = root.get("behavior_kind").getAsString().toLowerCase(Locale.ROOT);

        JsonObject base = root.has("base") ? root.getAsJsonObject("base") : new JsonObject();
        float power = base.has("power") ? base.get("power").getAsFloat() : 1.0f;
        float aoe = base.has("aoe") ? base.get("aoe").getAsFloat() : 1.0f;

        List<JsonObject> effects = new ArrayList<>();
        if (root.has("effects")) {
            JsonArray arr = root.getAsJsonArray("effects");
            for (JsonElement el : arr) effects.add(el.getAsJsonObject());
        }

        StackingCurve curve = StackingCurve.parse(
                root.has("stacking_curve") ? root.get("stacking_curve").getAsString() : null);

        JsonObject costObj = root.has("cost") ? root.getAsJsonObject("cost") : new JsonObject();
        float costPerTick = costObj.has("per_tick") ? costObj.get("per_tick").getAsFloat() : 0.0f;
        float costPerUse = costObj.has("per_use") ? costObj.get("per_use").getAsFloat() : 0.0f;

        return Optional.of(new MatrixEntry(
                sigil.get(), sign.get(), behaviorKind,
                power, aoe,
                List.copyOf(effects), curve, costPerTick, costPerUse));
    }
}
