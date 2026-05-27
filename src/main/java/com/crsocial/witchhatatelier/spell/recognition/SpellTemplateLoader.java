package com.crsocial.witchhatatelier.spell.recognition;

import com.crsocial.witchhatatelier.Config;
import com.crsocial.witchhatatelier.WitchHatAtelierMod;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Loads gesture templates from {@code data/<namespace>/spell_templates/*.json}.
 *
 * <h2>JSON format</h2>
 * <pre>
 * {
 *   "spell_name": "fireball",
 *   "variants": [
 *     {
 *       "name": "clean",
 *       "points": [
 *         { "x": 0.12, "y": 0.30, "stroke_id": 0 },
 *         ...
 *       ]
 *     }
 *   ]
 * }
 * </pre>
 *
 * <p>Raw points are stored verbatim. Each variant is also pushed through
 * {@link PointCloudPreprocessor#process} so the recognizer can compare against
 * pre-normalized clouds without redoing the work on every match.</p>
 */
public final class SpellTemplateLoader extends SimpleJsonResourceReloadListener {

    private static final Gson GSON = new Gson();
    private static final String DIRECTORY = "spell_templates";

    public SpellTemplateLoader() {
        super(GSON, DIRECTORY);
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> map, @NotNull ResourceManager mgr, @NotNull ProfilerFiller profiler) {
        TemplateRegistry registry = TemplateRegistry.get();
        registry.clear();

        int resampleN = Config.RESAMPLE_N.get();
        int spellCount = 0;
        int variantCount = 0;

        for (var entry : map.entrySet()) {
            ResourceLocation id = entry.getKey();
            try {
                JsonObject root = entry.getValue().getAsJsonObject();
                String spellName = root.get("spell_name").getAsString();
                boolean isRing = root.has("is_ring") && root.get("is_ring").getAsBoolean();
                JsonArray variants = root.getAsJsonArray("variants");
                if (variants == null) continue;

                for (JsonElement vEl : variants) {
                    JsonObject v = vEl.getAsJsonObject();
                    String variantName = v.has("name") ? v.get("name").getAsString() : "default";
                    List<Point> raw = parsePoints(v.getAsJsonArray("points"));
                    if (raw.isEmpty()) {
                        WitchHatAtelierMod.LOGGER.warn(
                                "[SpellTemplateLoader] {} variant '{}' has zero points — skipped.",
                                id, variantName);
                        continue;
                    }
                    PointCloud rawCloud = new PointCloud(spellName + ":" + variantName, raw);
                    PointCloudPreprocessor.Processed processed =
                            PointCloudPreprocessor.process(rawCloud, resampleN);
                    registry.register(new Template(
                            spellName, variantName, rawCloud,
                            processed.cloud(), resampleN, processed.indicativeAngle(), isRing,
                            processed.normalizedArcLength(), processed.metrics()));
                    variantCount++;
                }
                spellCount++;
            } catch (Exception e) {
                WitchHatAtelierMod.LOGGER.error(
                        "[SpellTemplateLoader] Failed to load template '{}': {}", id, e.getMessage());
            }
        }

        WitchHatAtelierMod.LOGGER.info(
                "[SpellTemplateLoader] Loaded {} spell(s), {} variant(s).", spellCount, variantCount);
    }

    private static List<Point> parsePoints(JsonArray arr) {
        List<Point> out = new ArrayList<>(arr.size());
        for (JsonElement el : arr) {
            JsonObject p = el.getAsJsonObject();
            float x = p.get("x").getAsFloat();
            float y = p.get("y").getAsFloat();
            int strokeID = p.has("stroke_id") ? p.get("stroke_id").getAsInt() : 0;
            out.add(new Point(x, y, strokeID));
        }
        return out;
    }
}
