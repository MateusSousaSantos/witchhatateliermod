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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Tier-2 offline harness. Replays every record in a labeled corpus
 * ({@code dataset/spell_corpus.jsonl}) through the <b>live</b> recognition pipeline —
 * the same preprocessing and chamfer the server runs — against the
 * <b>currently-loaded</b> {@link TemplateRegistry}, and re-emits the results in the
 * {@link RecognitionLog} schema.
 *
 * <p>This closes the gap that the offline {@code tune_corpus.py} replay cannot: that
 * tool replays the chamfer geometry baked into each record <em>at draw time</em>, so it
 * is blind to template variants added since. By re-running the real pipeline on each
 * record's preserved {@code rawStrokes}, this harness measures the templates as they
 * stand right now — and because the output schema is identical to the live log, the
 * existing Python tools ({@code analyze_recognition_log.py}, {@code tune_corpus.py})
 * consume it unchanged.</p>
 *
 * <p>Each corpus record is already a single sigil cluster (it was logged from one
 * {@code SigilCluster}), so the strokes are fed straight to
 * {@link PointCloudPreprocessor} without re-running {@code SigilClusterer}.</p>
 */
public final class CorpusReplay {

    private CorpusReplay() {}

    /**
     * Headline counts from one replay, for the command's chat summary.
     *
     * <p>Cut classes: corpus labels with no template class in the live registry
     * (e.g. {@code dispersion} after its roster cut, {@code bolt} which never shipped).
     * They are tallied apart from real recall — ideally rejected; a cut draw that still
     * matches some other class ({@code cutTotal - cutRejected}) is a live
     * misrecognition risk worth watching.</p>
     */
    public record Summary(int total, int realTotal, int correct, int unknown,
                          int garbageTotal, int garbageRejected,
                          int cutTotal, int cutRejected, Path output) {}

    /**
     * Replays {@code corpusPath} through the live pipeline and writes the resulting
     * records to {@code outPath} (truncating it). Returns headline accuracy counts.
     *
     * @throws IOException if the corpus cannot be read or the output cannot be written
     */
    public static Summary replay(Path corpusPath, Path outPath) throws IOException {
        List<String> lines = Files.readAllLines(corpusPath, StandardCharsets.UTF_8);
        TemplateRegistry registry = TemplateRegistry.get();
        List<Template> contentTemplates = registry.all();
        int resampleN = Config.RESAMPLE_N.get();
        float minScore = Config.RECOGNITION_MIN_SCORE.get().floatValue();
        PDollarPlusRecognizer recognizer = new PDollarPlusRecognizer(registry, minScore);

        // Labels with a REAL (castable) template class in the live registry. Negative
        // templates (is_rejection) are excluded — their label (e.g. dispersion) must stay
        // a cut/should-reject class, not become a recall class. Anything else labeled in the
        // corpus is a cut/uncovered class — scored in its own bucket, never as a recall miss.
        Set<String> covered = new HashSet<>();
        for (Template t : contentTemplates) if (!t.isRejection()) covered.add(t.spellName());

        List<RecognitionLog.Entry> out = new ArrayList<>(lines.size());
        int total = 0, realTotal = 0, correct = 0, unknown = 0, garbageTotal = 0, garbageRejected = 0;
        int cutTotal = 0, cutRejected = 0;

        for (String line : lines) {
            if (line.isBlank()) continue;
            JsonObject rec = JsonParser.parseString(line).getAsJsonObject();

            String intended = (rec.has("intended") && !rec.get("intended").isJsonNull())
                    ? rec.get("intended").getAsString() : null;
            // Carry the drawer id through so per-drawer analysis works directly on the
            // replay log (the corpus stamps `player` via build_corpus.normalize_drawer).
            String player = (rec.has("player") && !rec.get("player").isJsonNull())
                    ? rec.get("player").getAsString() : "replay";

            List<List<Point>> rawStrokes = parseRawStrokes(rec.getAsJsonArray("rawStrokes"));
            if (rawStrokes.isEmpty()) continue;

            List<Point> flat = new ArrayList<>();
            for (List<Point> stroke : rawStrokes) flat.addAll(stroke);
            PointCloud raw = new PointCloud("corpus", flat);

            PointCloudPreprocessor.Processed processed =
                    PointCloudPreprocessor.process(raw, resampleN);
            PDollarPlusRecognizer.Traced traced = recognizer.matchTraced(processed, contentTemplates);
            RecognitionResult result = traced.result();
            List<PDollarPlusRecognizer.Scored> ranked =
                    recognizer.matchVerbose(processed, contentTemplates);

            out.add(new RecognitionLog.Entry(
                    player, "<replay>", intended, "REPLAY", null, false, true,
                    0, 1, registry.size(),
                    rawStrokes, processed.cloud(), processed.indicativeAngle(),
                    result, ranked, traced.trace()));

            total++;
            boolean isUnknown = RecognitionResult.UNKNOWN.equals(result.spellName());
            boolean isGarbage = "garbage".equals(intended);
            if (isGarbage) {
                garbageTotal++;
                if (isUnknown) garbageRejected++;
            } else if (intended != null && !covered.contains(intended)) {
                cutTotal++;
                if (isUnknown) cutRejected++;
            } else if (intended != null) {
                realTotal++;
                if (intended.equals(result.spellName())) correct++;
                else if (isUnknown) unknown++;
            }
        }

        RecognitionLog.writeRecords(outPath, out);
        WitchHatAtelierMod.LOGGER.info(
                "[CorpusReplay] Replayed {} record(s) from {} against {} template(s) ({} cut-class) → {}",
                total, corpusPath, registry.size(), cutTotal, outPath);
        return new Summary(total, realTotal, correct, unknown, garbageTotal, garbageRejected,
                cutTotal, cutRejected, outPath);
    }

    /** Parses {@code [[{x,y},…],…]} into per-stroke point lists (strokeID = stroke index). */
    private static List<List<Point>> parseRawStrokes(JsonArray strokesArr) {
        List<List<Point>> strokes = new ArrayList<>();
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
