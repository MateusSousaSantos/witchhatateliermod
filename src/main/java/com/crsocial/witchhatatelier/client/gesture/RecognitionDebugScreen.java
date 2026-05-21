package com.crsocial.witchhatatelier.client.gesture;

import com.crsocial.witchhatatelier.spell.recognition.PDollarPlusRecognizer;
import com.crsocial.witchhatatelier.spell.recognition.Point;
import com.crsocial.witchhatatelier.spell.recognition.PointCloud;
import com.crsocial.witchhatatelier.spell.recognition.RecognitionResult;
import com.crsocial.witchhatatelier.spell.recognition.Template;
import com.crsocial.witchhatatelier.spell.recognition.TemplateRegistry;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * F9 debug screen showing the full recognition pipeline state for the most recently
 * drawn sigil. Displays strokes color-coded by cluster, per-cluster ranked template
 * scores, and a candidate vs template overlay.
 */
@OnlyIn(Dist.CLIENT)
public final class RecognitionDebugScreen extends Screen {

    private static final int[] CLUSTER_COLORS = {
            0xFFFFCC33, 0xFF33CCFF, 0xFFFF66CC, 0xFF66FF99,
            0xFFCC66FF, 0xFFFFAA66, 0xFF66FFCC, 0xFFFF6666
    };
    private static final int RING_COLOR       = 0xFF888888;
    private static final int CANDIDATE_COLOR  = 0xFFFFEE44;
    private static final int TEMPLATE_COLOR   = 0xFFFF44CC;
    private static final int PANEL_BG         = 0xFF1A1A22;
    private static final int PANEL_BORDER     = 0xFF555566;

    private final LastRecognitionDebug.Snapshot snap;
    private final Map<Integer, Integer> selectedRank = new HashMap<>();
    private int scrollY = 0;

    public RecognitionDebugScreen() {
        super(Component.literal("Recognition Debug"));
        this.snap = LastRecognitionDebug.get();
    }

    @Override
    protected void init() {
        super.init();
        if (snap == null) return;

        // Rebuild template-select buttons for each cluster
        int contentTop = 110;
        int rowHeight = 110;
        for (int i = 0; i < snap.clusters().size(); i++) {
            LastRecognitionDebug.ClusterSnapshot cs = snap.clusters().get(i);
            selectedRank.putIfAbsent(i, 0);
            int rowY = contentTop + i * rowHeight - scrollY;
            int btnX = 10;
            int btnY = rowY + 16;
            int topK = Math.min(3, cs.ranked().size());
            final int clusterIdx = i;
            for (int k = 0; k < topK; k++) {
                PDollarPlusRecognizer.Scored s = cs.ranked().get(k);
                String label = String.format(Locale.ROOT, "#%d %s %.2f", k + 1, s.spellName(), s.score());
                int w = font.width(label) + 10;
                final int rank = k;
                addRenderableWidget(Button.builder(
                        Component.literal(label),
                        b -> selectedRank.put(clusterIdx, rank)
                ).bounds(btnX, btnY, w, 16).build());
                btnX += w + 4;
            }
        }
    }

    @Override
    public void renderBackground(@NotNull GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
        gui.fill(0, 0, width, height, 0x0000000);
    }

    @Override
    public void render(@NotNull GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
        renderBackground(gui, mouseX, mouseY, partialTick);

        if (snap == null) {
            gui.drawCenteredString(font,
                    Component.literal("No recognition captured yet — draw something first."),
                    width / 2, height / 2, 0xFFFFFFFF);
            return;
        }

        // ── Header ───────────────────────────────────────────────────────
        long ageMs = System.currentTimeMillis() - snap.timestampMs();
        String header = String.format(Locale.ROOT,
                "Recognition Debug — captured %.1fs ago", ageMs / 1000f);
        gui.drawString(font, header, 10, 6, 0xFFFFFFFF);
        String stats = String.format(Locale.ROOT,
                "%d strokes · %d clusters · %d ring stroke(s) · resample N=%d",
                snap.totalStrokes(),
                snap.clusters().size(),
                snap.ringStrokeIds().size(),
                snap.clusters().isEmpty() ? 0 : snap.clusters().getFirst().processedCloud().points().size());
        gui.drawString(font, stats, 10, 18, 0xFFCCCCCC);

        // ── Drawing overview (clusters color-coded) ─────────────────────
        int overviewSize = 80;
        int overviewX = 10;
        int overviewY = 32;
        drawPanel(gui, overviewX, overviewY, overviewSize, overviewSize);
        drawSnapshotByCluster(gui, overviewX, overviewY, overviewSize);

        // Legend
        int legX = overviewX + overviewSize + 10;
        int legY = overviewY;
        gui.drawString(font, "Legend:", legX, legY, 0xFFAAAAAA);
        legY += 12;
        for (int i = 0; i < snap.clusters().size() && i < CLUSTER_COLORS.length; i++) {
            int color = CLUSTER_COLORS[i];
            gui.fill(legX, legY + 2, legX + 8, legY + 10, color);
            String txt = String.format(Locale.ROOT, "Cluster %d (%d strokes)",
                    i + 1, snap.clusters().get(i).originalStrokeIds().size());
            gui.drawString(font, txt, legX + 12, legY, 0xFFCCCCCC);
            legY += 12;
        }
        if (!snap.ringStrokeIds().isEmpty()) {
            gui.fill(legX, legY + 2, legX + 8, legY + 10, RING_COLOR);
            String txt = String.format(Locale.ROOT, "Ring stroke(s): %s",
                    snap.ringStrokeIds());
            gui.drawString(font, txt, legX + 12, legY, 0xFFCCCCCC);
        }

        // ── Per-cluster details ─────────────────────────────────────────
        int contentTop = 110;
        int rowHeight = 110;
        for (int i = 0; i < snap.clusters().size(); i++) {
            LastRecognitionDebug.ClusterSnapshot cs = snap.clusters().get(i);
            int overlayY = contentTop + i * rowHeight - scrollY;
            if (overlayY + rowHeight < 0 || overlayY > height) continue;

            // Header
            String clusterHeader = String.format(Locale.ROOT,
                    "── Cluster %d ── strokes %s — angle=%.2frad",
                    i + 1, cs.originalStrokeIds(), cs.indicativeAngle());
            int clusterColor = CLUSTER_COLORS[i % CLUSTER_COLORS.length];
            gui.drawString(font, clusterHeader, 10, overlayY, clusterColor);

            // Result line
            String resultLine;
            int resultColor;
            if (RecognitionResult.UNKNOWN.equals(cs.result().spellName())) {
                resultLine = String.format(Locale.ROOT,
                        "Result: UNKNOWN (best score=%.3f)", cs.result().confidenceScore());
                resultColor = 0xFFFF8888;
            } else {
                resultLine = String.format(Locale.ROOT,
                        "Result: %s (%.3f)", cs.result().spellName(), cs.result().confidenceScore());
                resultColor = 0xFF88FF88;
            }
            gui.drawString(font, resultLine, 10, overlayY + 36, resultColor);

            // Overlay panel
            int overlaySize = 60;
            int overlayX = width - overlaySize - 12;
            drawPanel(gui, overlayX, overlayY, overlaySize, overlaySize);

            int rank = selectedRank.getOrDefault(i, 0);
            if (rank < cs.ranked().size()) {
                PDollarPlusRecognizer.Scored selected = cs.ranked().get(rank);
                Template tmpl = findTemplate(selected.spellName(), selected.variantName());
                if (tmpl != null) {
                    drawProcessedCloud(gui, overlayX, overlayY, overlaySize,
                            cs.processedCloud(), CANDIDATE_COLOR);
                    drawProcessedCloud(gui, overlayX, overlayY, overlaySize,
                            tmpl.processedCloud(), TEMPLATE_COLOR);
                }
                String overlayLabel = String.format(Locale.ROOT, "vs %s:%s",
                        selected.spellName(), selected.variantName());
                gui.drawString(font, overlayLabel,
                        overlayX - 4 - font.width(overlayLabel), overlayY + 4, 0xFFCCCCCC);
                gui.drawString(font, "yel=cand  mag=tmpl",
                        overlayX - 4 - font.width("yel=cand  mag=tmpl"),
                        overlayY + overlaySize - 9, 0xFFAAAAAA);
            }
        }

        super.render(gui, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        this.scrollY = Math.max(0, this.scrollY - (int)(scrollY * 20));
        this.clearWidgets();
        this.init();
        return true;
    }

    @Override
    public boolean isPauseScreen() { return false; }

    // ── Rendering primitives ────────────────────────────────────────────

    private void drawPanel(GuiGraphics gui, int x, int y, int w, int h) {
        gui.fill(x, y, x + w, y + h, PANEL_BG);
        gui.fill(x - 1, y - 1, x + w + 1, y, PANEL_BORDER);
        gui.fill(x - 1, y + h, x + w + 1, y + h + 1, PANEL_BORDER);
        gui.fill(x - 1, y, x, y + h, PANEL_BORDER);
        gui.fill(x + w, y, x + w + 1, y + h, PANEL_BORDER);
    }

    /** Renders the snapshot's raw strokes color-coded by cluster (ring strokes in gray). */
    private void drawSnapshotByCluster(GuiGraphics gui, int x, int y, int size) {
        if (snap == null || snap.rawPoints().isEmpty()) return;

        // Build a map: original strokeID → color
        Map<Integer, Integer> strokeColor = new HashMap<>();
        for (Integer ringId : snap.ringStrokeIds()) strokeColor.put(ringId, RING_COLOR);
        for (int i = 0; i < snap.clusters().size(); i++) {
            int color = CLUSTER_COLORS[i % CLUSTER_COLORS.length];
            for (Integer sid : snap.clusters().get(i).originalStrokeIds()) {
                strokeColor.put(sid, color);
            }
        }

        // Group raw points by stroke ID
        Map<Integer, List<GesturePoint>> byStroke = new HashMap<>();
        for (GesturePoint p : snap.rawPoints()) {
            byStroke.computeIfAbsent(p.strokeID(), k -> new ArrayList<>()).add(p);
        }

        for (var e : byStroke.entrySet()) {
            int color = strokeColor.getOrDefault(e.getKey(), 0xFFFFFFFF);
            List<GesturePoint> pts = e.getValue();
            for (int i = 1; i < pts.size(); i++) {
                GesturePoint a = pts.get(i - 1);
                GesturePoint b = pts.get(i);
                drawLine(gui,
                        x + (int)(a.x() * size), y + (int)(a.y() * size),
                        x + (int)(b.x() * size), y + (int)(b.y() * size),
                        color);
            }
        }
    }

    /** Renders a processed (centered, scaled, rotated) cloud into a unit-square viewport. */
    private void drawProcessedCloud(GuiGraphics gui, int x, int y, int size,
                                    PointCloud cloud, int color) {
        if (cloud.points().isEmpty()) return;

        // Processed clouds live around [-0.5, 0.5]. Map to [0, size].
        Map<Integer, List<Point>> byStroke = new HashMap<>();
        for (Point p : cloud.points()) {
            byStroke.computeIfAbsent(p.strokeID(), k -> new ArrayList<>()).add(p);
        }
        for (List<Point> stroke : byStroke.values()) {
            for (int i = 1; i < stroke.size(); i++) {
                Point a = stroke.get(i - 1);
                Point b = stroke.get(i);
                int ax = x + (int)((a.x() + 0.5f) * size);
                int ay = y + (int)((a.y() + 0.5f) * size);
                int bx = x + (int)((b.x() + 0.5f) * size);
                int by = y + (int)((b.y() + 0.5f) * size);
                drawLine(gui, ax, ay, bx, by, color);
            }
        }
    }

    /** Bresenham-style line for GUI rendering. */
    private static void drawLine(GuiGraphics gui, int x0, int y0, int x1, int y1, int color) {
        int dx = Math.abs(x1 - x0), dy = -Math.abs(y1 - y0);
        int sx = x0 < x1 ? 1 : -1, sy = y0 < y1 ? 1 : -1;
        int err = dx + dy;
        while (true) {
            gui.fill(x0, y0, x0 + 1, y0 + 1, color);
            if (x0 == x1 && y0 == y1) break;
            int e2 = 2 * err;
            if (e2 >= dy) { err += dy; x0 += sx; }
            if (e2 <= dx) { err += dx; y0 += sy; }
        }
    }

    private static Template findTemplate(String spellName, String variantName) {
        for (Template t : TemplateRegistry.get().all()) {
            if (t.spellName().equals(spellName) && t.variantName().equals(variantName)) return t;
        }
        for (Template t : TemplateRegistry.get().allRing()) {
            if (t.spellName().equals(spellName) && t.variantName().equals(variantName)) return t;
        }
        return null;
    }
}
