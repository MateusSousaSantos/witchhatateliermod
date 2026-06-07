package com.crsocial.witchhatatelier.spell.recognition;

import com.crsocial.witchhatatelier.Config;
import com.crsocial.witchhatatelier.WitchHatAtelierMod;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Phase 0 instrumentation. Appends one structured JSONL record per recognized
 * sigil to {@code <gamedir>/logs/spell_recognition.jsonl} — the persistent data
 * source the remediation plan calls for before any threshold can be calibrated.
 *
 * <p>Each record captures everything needed to reconstruct two distributions —
 * valid matches vs non-matches — per sigil: the raw strokes, the processed point
 * cloud, the full per-template chamfer <em>distance</em> and score (not just the
 * top-3), the derived best-per-spell, the final {@link RecognitionResult}, and a
 * snapshot of the active thresholds. JSONL (one JSON object per line) is chosen
 * so the file can be tailed live and streamed into a plotting script without
 * parsing a growing array.</p>
 *
 * <p>Gated behind {@link Config#RECOGNITION_LOGGING_ENABLED} (off by default);
 * enable it for data-collection sessions. Writes are best-effort: an I/O failure
 * is warned once and never interrupts a cast.</p>
 */
public final class RecognitionLog {

    private RecognitionLog() {}

    private static final Gson GSON = new Gson();
    private static final Object LOCK = new Object();
    private static volatile boolean writeFailureLogged = false;

    /** One recognition event for a single sigil cluster. */
    public record Entry(
            String player,
            String playerUuid,
            String intended,
            String medium,
            int[] blockOrigin,
            boolean debugMode,
            int sigilIndex,
            int sigilCount,
            int templateCount,
            List<List<Point>> rawStrokes,
            PointCloud processedCloud,
            float indicativeAngle,
            RecognitionResult result,
            List<PDollarPlusRecognizer.Scored> ranked,
            PDollarPlusRecognizer.MatchTrace decisionTrace) {}

    public static boolean isEnabled() {
        return Config.RECOGNITION_LOGGING_ENABLED.get();
    }

    /**
     * Writes {@code entries} as JSONL to {@code out}, truncating any existing file —
     * the Tier-2 corpus-replay path. Unlike {@link #log}, this is <b>not</b> gated on
     * {@link Config#RECOGNITION_LOGGING_ENABLED} (the caller is an explicit dev tool,
     * not live play) and targets an arbitrary path rather than the fixed live log.
     * Reuses {@link #toJson} so the output schema is identical to the live log and the
     * offline Python tools consume it unchanged.
     *
     * @throws IOException if the file cannot be written (surfaced to the command caller)
     */
    public static void writeRecords(Path out, List<Entry> entries) throws IOException {
        StringBuilder sb = new StringBuilder();
        for (Entry e : entries) {
            sb.append(GSON.toJson(toJson(e))).append(System.lineSeparator());
        }
        synchronized (LOCK) {
            Files.createDirectories(out.getParent());
            Files.writeString(out, sb.toString(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        }
    }

    /** Appends one JSONL record. No-op (apart from the enabled check) when disabled. */
    public static void log(Entry e) {
        if (!isEnabled()) return;
        try {
            String line = GSON.toJson(toJson(e)) + System.lineSeparator();
            Path file = logFile();
            synchronized (LOCK) {
                Files.createDirectories(file.getParent());
                Files.writeString(file, line, StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            }
        } catch (IOException | RuntimeException ex) {
            if (!writeFailureLogged) {
                writeFailureLogged = true;
                WitchHatAtelierMod.LOGGER.warn(
                        "[RecognitionLog] Failed to write recognition log (further failures suppressed): {}",
                        ex.toString());
            }
        }
    }

    private static JsonObject toJson(Entry e) {
        JsonObject root = new JsonObject();
        root.addProperty("ts", System.currentTimeMillis());
        root.addProperty("player", e.player());
        root.addProperty("playerUuid", e.playerUuid());
        if (e.intended() != null) {
            root.addProperty("intended", e.intended());
        } else {
            root.add("intended", com.google.gson.JsonNull.INSTANCE);
        }
        root.addProperty("medium", e.medium());
        if (e.blockOrigin() != null) {
            JsonArray bo = new JsonArray();
            for (int v : e.blockOrigin()) bo.add(v);
            root.add("blockOrigin", bo);
        } else {
            root.add("blockOrigin", com.google.gson.JsonNull.INSTANCE);
        }
        root.addProperty("debugMode", e.debugMode());
        root.addProperty("sigilIndex", e.sigilIndex());
        root.addProperty("sigilCount", e.sigilCount());
        root.addProperty("templateCount", e.templateCount());
        root.addProperty("indicativeAngle", round(e.indicativeAngle()));

        // Active thresholds — every later phase re-derives these, so pin what was live.
        JsonObject thr = new JsonObject();
        thr.addProperty("minScore", Config.RECOGNITION_MIN_SCORE.get());
        thr.addProperty("distAtFullScore", Config.RECOGNITION_DIST_AT_FULL_SCORE.get());
        thr.addProperty("distAtZeroScore", Config.RECOGNITION_DIST_AT_ZERO_SCORE.get());
        thr.addProperty("ambiguityMargin", Config.RECOGNITION_AMBIGUITY_MARGIN.get());
        thr.addProperty("consensusBonus", Config.RECOGNITION_CONSENSUS_BONUS.get());
        thr.addProperty("consensusTopN", Config.RECOGNITION_CONSENSUS_TOP_N.get());
        thr.addProperty("resampleN", Config.RESAMPLE_N.get());
        thr.addProperty("microMergeRadius", Config.MICRO_MERGE_RADIUS.get());
        thr.addProperty("macroMergeRadius", Config.MACRO_MERGE_RADIUS.get());
        thr.addProperty("gridCheckScoreThreshold", Config.GRID_CHECK_SCORE_THRESHOLD.get());
        thr.addProperty("gridMinSimilarity", Config.GRID_MIN_SIMILARITY.get());
        thr.addProperty("worstPairFreeAllowance", Config.WORST_PAIR_FREE_ALLOWANCE.get());
        thr.addProperty("worstPairWeight", Config.WORST_PAIR_WEIGHT.get());
        root.add("thresholds", thr);

        // Final result.
        JsonObject res = new JsonObject();
        res.addProperty("spell", e.result().spellName());
        res.addProperty("score", round(e.result().confidenceScore()));
        res.addProperty("angle", round(e.result().indicativeAngle()));
        root.add("result", res);

        // Raw strokes (per stroke) — the un-preprocessed ink, for re-deriving later.
        JsonArray strokesArr = new JsonArray();
        for (List<Point> stroke : e.rawStrokes()) {
            JsonArray sArr = new JsonArray();
            for (Point p : stroke) {
                JsonObject pObj = new JsonObject();
                pObj.addProperty("x", round(p.x()));
                pObj.addProperty("y", round(p.y()));
                sArr.add(pObj);
            }
            strokesArr.add(sArr);
        }
        root.add("rawStrokes", strokesArr);

        // Processed cloud (x, y, turning angle, stroke id) — what the chamfer actually saw.
        JsonArray procArr = new JsonArray();
        for (Point p : e.processedCloud().points()) {
            JsonObject pObj = new JsonObject();
            pObj.addProperty("x", round(p.x()));
            pObj.addProperty("y", round(p.y()));
            pObj.addProperty("a", round(p.turningAngle()));
            pObj.addProperty("s", p.strokeID());
            procArr.add(pObj);
        }
        root.add("processedCloud", procArr);

        // Full per-template ranking: raw chamfer distance AND score, every template.
        // This is the unfiltered diagnostic view (matchVerbose), the raw material for
        // the valid-vs-garbage distance/score distributions.
        JsonArray surv = new JsonArray();
        Map<String, Float> bestPerSpell = new TreeMap<>();
        for (PDollarPlusRecognizer.Scored s : e.ranked()) {
            JsonObject o = new JsonObject();
            o.addProperty("spell", s.spellName());
            o.addProperty("variant", s.variantName());
            o.addProperty("score", round(s.score()));
            o.addProperty("dist", round(s.rawDistance()));
            o.addProperty("worst", round(s.worstPairDistance()));
            o.addProperty("p90", round(s.p90PairDistance()));
            surv.add(o);
            bestPerSpell.merge(s.spellName(), s.score(), Math::max);
        }
        root.add("survivors", surv);

        JsonObject bps = new JsonObject();
        for (Map.Entry<String, Float> en : bestPerSpell.entrySet()) {
            bps.addProperty(en.getKey(), round(en.getValue()));
        }
        root.add("bestPerSpell", bps);

        // What match() ACTUALLY did: per-template prefilter verdict, raw vs grid-adjusted
        // score, and the meta-gates. Unlike `survivors` (the unfiltered chamfer ranking),
        // this is the real decision trail — it explains why the winner beat the leaders.
        if (e.decisionTrace() != null) {
            root.add("decision", decisionJson(e.decisionTrace()));
        }

        return root;
    }

    private static JsonObject decisionJson(PDollarPlusRecognizer.MatchTrace t) {
        JsonObject d = new JsonObject();
        addStr(d, "winnerSpell", t.winnerSpell());
        addStr(d, "winnerVariant", t.winnerVariant());
        d.addProperty("bestScore", round(t.bestScore()));
        d.addProperty("bestWorstPair", round(t.bestWorstPair()));
        addStr(d, "runnerUpSpell", t.runnerUpSpell());
        d.addProperty("bestOfOtherSpell", round(t.bestOfOtherSpell()));
        d.addProperty("margin", round(t.margin()));
        d.addProperty("gap", round(t.gap()));
        d.addProperty("consensusAgree", t.consensusAgree());
        d.addProperty("consensusBonus", round(t.consensusBonus()));
        d.addProperty("effectiveScore", round(t.effectiveScore()));
        d.addProperty("worstPairFree", round(t.worstPairFree()));
        d.addProperty("worstPairWeight", round(t.worstPairWeight()));
        addStr(d, "rejectionStage", t.rejectionStage());

        JsonArray temps = new JsonArray();
        for (PDollarPlusRecognizer.TemplateTrace tt : t.templates()) {
            JsonObject o = new JsonObject();
            o.addProperty("spell", tt.spellName());
            o.addProperty("variant", tt.variantName());
            o.addProperty("pre", tt.prefilterPassed());
            if (tt.rejectedBy() != null) o.addProperty("rejectedBy", tt.rejectedBy());
            if (tt.prefilterPassed()) {
                o.addProperty("raw", round(tt.rawScore()));
                addNum(o, tt.gridSim());
                o.addProperty("gridMult", round(tt.gridMultiplier()));
                o.addProperty("final", round(tt.finalScore()));
                o.addProperty("dist", round(tt.dist()));
                o.addProperty("worst", round(tt.worstPair()));
                o.addProperty("p90", round(tt.p90Pair()));
            }
            temps.add(o);
        }
        d.add("templates", temps);
        return d;
    }

    private static void addStr(JsonObject o, String key, String value) {
        if (value != null) o.addProperty(key, value);
        else o.add(key, com.google.gson.JsonNull.INSTANCE);
    }

    /** Adds a float property, emitting JSON null for NaN (e.g. grid not computed). */
    private static void addNum(JsonObject o, float value) {
        if (Float.isNaN(value)) o.add("gridSim", com.google.gson.JsonNull.INSTANCE);
        else o.addProperty("gridSim", round(value));
    }

    private static float round(float v) {
        return Math.round(v * 1000f) / 1000f;
    }

    private static Path logFile() {
        return FMLPaths.GAMEDIR.get().resolve("logs").resolve("spell_recognition.jsonl");
    }
}
