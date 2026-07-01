package com.crsocial.witchhatatelier.spell.recognition;

import com.crsocial.witchhatatelier.Config;
import com.crsocial.witchhatatelier.WitchHatAtelierMod;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Tier-2 cross-validation harness — measures <b>generalization</b>, the missing piece
 * the plan's Definition of Done requires ("train templates from people A–E, test on F").
 *
 * <p>Where {@link CorpusReplay} scores the corpus against the templates as they ship
 * (an in-sample number when the corpus authored those templates), this splits the corpus
 * and never lets a test draw's own group contribute to the templates it is matched
 * against:</p>
 *
 * <ul>
 *   <li><b>≥2 drawers</b> → <b>leave-one-drawer-out</b>: each drawer is held out in turn,
 *       the others' draws are promoted to templates. This is the user-independent split.</li>
 *   <li><b>1 drawer</b> → stratified <b>k-fold</b> by label (k=5). <i>Not</i> user-independent
 *       (same hand draws train and test), but it exercises the whole pipeline so the harness
 *       is ready the moment a second drawer's data lands.</li>
 * </ul>
 *
 * <p>Each fold reports two accuracies on the held-out test draws:</p>
 * <ol>
 *   <li><b>base</b> — matched against only the authored {@link TemplateRegistry} templates.</li>
 *   <li><b>base + train coverage</b> — authored templates plus the fold's train draws promoted
 *       to in-memory {@link Template}s (built exactly as {@link SpellTemplateLoader} does, so a
 *       corpus record's {@code rawStrokes} becomes a variant with no file mutation or
 *       {@code /reload}).</li>
 * </ol>
 *
 * <p>The <b>Δ</b> between them is the marginal value of promoting that coverage — the number
 * that answers "does collecting more drawers actually help?" without the circularity of
 * testing on the same draws you promoted.</p>
 */
public final class CorpusCrossVal {

    private CorpusCrossVal() {}

    private static final int KFOLDS = 5;

    /** Per-fold tallies: held-out accuracy with base templates vs base + promoted train. */
    public record FoldResult(String name, int realTotal, int baseCorrect, int augCorrect,
                             int garbageTotal, int baseGarbageRej, int augGarbageRej,
                             int trainTemplates) {}

    /**
     * Whole-run summary across all folds. Cut classes (labels with no template class in
     * the live registry, e.g. {@code dispersion}/{@code bolt}) are neither scored as real
     * recall nor promoted to train templates — they get their own should-reject tally.
     */
    public record Summary(boolean userIndependent, int drawers, List<FoldResult> folds,
                          int realTotal, int baseCorrect, int augCorrect,
                          int garbageTotal, int baseGarbageRej, int augGarbageRej,
                          int cutTotal, int baseCutRej, int augCutRej,
                          Path output) {}

    private record Rec(String player, String intended, List<List<Point>> strokes) {}

    public static Summary run(Path corpusPath, Path outPath) throws IOException {
        List<Rec> recs = parse(corpusPath);
        if (recs.isEmpty()) throw new IOException("no usable records in " + corpusPath);

        // Group by drawer to decide the split strategy.
        LinkedHashMap<String, List<Rec>> byPlayer = new LinkedHashMap<>();
        for (Rec r : recs) byPlayer.computeIfAbsent(r.player(), k -> new ArrayList<>()).add(r);
        boolean userIndependent = byPlayer.size() >= 2;

        // Build the fold list: (foldName -> test records).
        List<Map.Entry<String, List<Rec>>> folds = new ArrayList<>();
        if (userIndependent) {
            for (var e : byPlayer.entrySet()) folds.add(Map.entry(e.getKey(), e.getValue()));
        } else {
            // Stratified k-fold: sort by (label, order) then round-robin so each fold holds a
            // proportional slice of every class. Deterministic — no RNG.
            List<Rec> sorted = new ArrayList<>(recs);
            sorted.sort(Comparator.comparing(r -> r.intended() == null ? "" : r.intended()));
            List<List<Rec>> buckets = new ArrayList<>();
            for (int i = 0; i < KFOLDS; i++) buckets.add(new ArrayList<>());
            for (int i = 0; i < sorted.size(); i++) buckets.get(i % KFOLDS).add(sorted.get(i));
            for (int i = 0; i < KFOLDS; i++) folds.add(Map.entry("fold" + (i + 1), buckets.get(i)));
        }

        int resampleN = Config.RESAMPLE_N.get();
        float minScore = Config.RECOGNITION_MIN_SCORE.get().floatValue();
        PDollarPlusRecognizer recognizer = new PDollarPlusRecognizer(TemplateRegistry.get(), minScore);
        List<Template> baseTemplates = TemplateRegistry.get().all();

        // Labels with a template class in the live registry. Cut/uncovered labels are
        // excluded from train promotion (a cut class must not be resurrected in-memory)
        // and scored in their own should-reject bucket instead of as recall.
        Set<String> covered = new HashSet<>();
        for (Template t : baseTemplates) covered.add(t.spellName());

        List<FoldResult> foldResults = new ArrayList<>();
        List<RecognitionLog.Entry> logOut = new ArrayList<>();
        int realTotal = 0, baseCorrect = 0, augCorrect = 0;
        int garbageTotal = 0, baseGarbageRej = 0, augGarbageRej = 0;
        int cutTotal = 0, baseCutRej = 0, augCutRej = 0;

        for (var fold : folds) {
            List<Rec> test = fold.getValue();
            // Train = everything not in this fold; promote its non-garbage draws to templates.
            List<Template> trainTemplates = new ArrayList<>();
            int ti = 0;
            for (var other : folds) {
                if (other == fold) continue;
                for (Rec r : other.getValue()) {
                    if (r.intended() == null || "garbage".equals(r.intended())
                            || !covered.contains(r.intended())) continue;
                    Template t = toTemplate(r, "cv_" + ti++, resampleN);
                    if (t != null) trainTemplates.add(t);
                }
            }
            List<Template> augTemplates = new ArrayList<>(baseTemplates.size() + trainTemplates.size());
            augTemplates.addAll(baseTemplates);
            augTemplates.addAll(trainTemplates);

            int fReal = 0, fBase = 0, fAug = 0, fGarb = 0, fBaseG = 0, fAugG = 0;
            for (Rec r : test) {
                List<Point> flat = new ArrayList<>();
                for (List<Point> s : r.strokes()) flat.addAll(s);
                if (flat.isEmpty()) continue;
                PointCloudPreprocessor.Processed proc =
                        PointCloudPreprocessor.process(new PointCloud("cv", flat), resampleN);

                String basePred = recognizer.match(proc, baseTemplates).spellName();
                PDollarPlusRecognizer.Traced augTraced = recognizer.matchTraced(proc, augTemplates);
                String augPred = augTraced.result().spellName();

                if ("garbage".equals(r.intended())) {
                    fGarb++;
                    if (RecognitionResult.UNKNOWN.equals(basePred)) fBaseG++;
                    if (RecognitionResult.UNKNOWN.equals(augPred)) fAugG++;
                } else if (r.intended() != null && !covered.contains(r.intended())) {
                    cutTotal++;
                    if (RecognitionResult.UNKNOWN.equals(basePred)) baseCutRej++;
                    if (RecognitionResult.UNKNOWN.equals(augPred)) augCutRej++;
                } else if (r.intended() != null) {
                    fReal++;
                    if (r.intended().equals(basePred)) fBase++;
                    if (r.intended().equals(augPred)) fAug++;
                }

                // Persist the augmented (realistic generalization) prediction for inspection.
                List<PDollarPlusRecognizer.Scored> ranked = recognizer.matchVerbose(proc, augTemplates);
                logOut.add(new RecognitionLog.Entry(
                        fold.getKey(), "<crossval>", r.intended(), "CROSSVAL", null, false,
                        0, 1, augTemplates.size(), r.strokes(), proc.cloud(), proc.indicativeAngle(),
                        augTraced.result(), ranked, augTraced.trace()));
            }

            foldResults.add(new FoldResult(fold.getKey(), fReal, fBase, fAug,
                    fGarb, fBaseG, fAugG, trainTemplates.size()));
            realTotal += fReal; baseCorrect += fBase; augCorrect += fAug;
            garbageTotal += fGarb; baseGarbageRej += fBaseG; augGarbageRej += fAugG;
        }

        RecognitionLog.writeRecords(outPath, logOut);
        WitchHatAtelierMod.LOGGER.info(
                "[CorpusCrossVal] {} over {} drawer(s): base {}/{} → aug {}/{} correct; wrote {}",
                userIndependent ? "leave-one-drawer-out" : (KFOLDS + "-fold (single drawer)"),
                byPlayer.size(), baseCorrect, realTotal, augCorrect, realTotal, outPath);
        return new Summary(userIndependent, byPlayer.size(), foldResults,
                realTotal, baseCorrect, augCorrect,
                garbageTotal, baseGarbageRej, augGarbageRej,
                cutTotal, baseCutRej, augCutRej, outPath);
    }

    /** Promotes one corpus record to an in-memory template (mirrors {@link SpellTemplateLoader}). */
    private static Template toTemplate(Rec r, String variantName, int resampleN) {
        List<Point> flat = new ArrayList<>();
        for (List<Point> s : r.strokes()) flat.addAll(s);
        if (flat.isEmpty()) return null;
        PointCloud rawCloud = new PointCloud(r.intended() + ":" + variantName, flat);
        PointCloudPreprocessor.Processed p = PointCloudPreprocessor.process(rawCloud, resampleN);
        return new Template(r.intended(), variantName, rawCloud, p.cloud(), resampleN,
                p.indicativeAngle(), false, p.normalizedArcLength(), p.metrics());
    }

    private static List<Rec> parse(Path corpusPath) throws IOException {
        List<Rec> out = new ArrayList<>();
        for (String line : Files.readAllLines(corpusPath, StandardCharsets.UTF_8)) {
            if (line.isBlank()) continue;
            JsonObject rec = JsonParser.parseString(line).getAsJsonObject();
            String intended = (rec.has("intended") && !rec.get("intended").isJsonNull())
                    ? rec.get("intended").getAsString() : null;
            if (intended == null) continue; // unlabeled draws can't be scored
            String player = (rec.has("player") && !rec.get("player").isJsonNull())
                    ? rec.get("player").getAsString() : "<unknown>";
            List<List<Point>> strokes = parseRawStrokes(rec.getAsJsonArray("rawStrokes"));
            if (!strokes.isEmpty()) out.add(new Rec(player, intended, strokes));
        }
        return out;
    }

    /** Parses {@code [[{x,y},…],…]} into per-stroke point lists (strokeID = stroke index). */
    private static List<List<Point>> parseRawStrokes(JsonArray strokesArr) {
        List<List<Point>> strokes = new ArrayList<>();
        if (strokesArr == null) return strokes;
        for (int s = 0; s < strokesArr.size(); s++) {
            JsonArray strokeArr = strokesArr.get(s).getAsJsonArray();
            List<Point> stroke = new ArrayList<>(strokeArr.size());
            for (JsonElement pe : strokeArr) {
                JsonObject p = pe.getAsJsonObject();
                stroke.add(new Point(p.get("x").getAsFloat(), p.get("y").getAsFloat(), s));
            }
            if (!stroke.isEmpty()) strokes.add(stroke);
        }
        return strokes;
    }
}
