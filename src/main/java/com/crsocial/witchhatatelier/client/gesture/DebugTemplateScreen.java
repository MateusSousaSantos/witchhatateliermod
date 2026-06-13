package com.crsocial.witchhatatelier.client.gesture;

import com.crsocial.witchhatatelier.Config;
import com.crsocial.witchhatatelier.spell.recognition.PDollarPlusRecognizer;
import com.crsocial.witchhatatelier.spell.recognition.Point;
import com.crsocial.witchhatatelier.spell.recognition.PointCloud;
import com.crsocial.witchhatatelier.spell.recognition.PointCloudPreprocessor;
import com.crsocial.witchhatatelier.spell.recognition.RecognitionResult;
import com.crsocial.witchhatatelier.spell.recognition.TemplateRegistry;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.fml.loading.FMLPaths;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Debug-only screen for recording new spell template variants.
 * Opens via F8 keybind. Saves directly to the source spell_templates folder when
 * running in dev (detected by presence of src/main/resources/); falls back to
 * config/witchhatateliermod/spell_templates/ otherwise.
 */
@OnlyIn(Dist.CLIENT)
public final class DebugTemplateScreen extends CanvasScreen {

    private static final int ROW_H = 24;

    private EditBox spellNameBox;
    private String statusMessage = "";
    private int statusColor = 0xFFFFFFFF;
    private String recognitionText = "";

    public DebugTemplateScreen() {
        super(new CanvasProfile.DebugProfile(), List.of(), true, (pts, ids) -> {}, null);
    }

    @Override
    protected boolean isTriggerPhaseEnabled() { return false; }

    // ════════════════════════════════════════════════════════════════════════════
    // Lifecycle
    // ════════════════════════════════════════════════════════════════════════════

    @Override
    protected void init() {
        super.init();
        // Window resize rebuilds widgets; carry the typed sigil name over.
        String preservedName = spellNameBox != null ? spellNameBox.getValue() : "";

        int boxW = Math.min(200, displayW);
        int boxX = displayX + (displayW - boxW) / 2;
        int boxY = displayY - ROW_H - 6;

        spellNameBox = new EditBox(font, boxX, boxY, boxW, 20,
                Component.literal("Sigil Name"));
        spellNameBox.setHint(Component.literal("sigil_name..."));
        spellNameBox.setMaxLength(64);
        spellNameBox.setValue(preservedName);
        addRenderableWidget(spellNameBox);
        setFocused(spellNameBox);

        int clearW = 60, saveW = 120, gap = 6;
        int btnY = displayY + displayH + 6;
        int btnX = displayX + (displayW - clearW - gap - saveW) / 2;
        addRenderableWidget(Button.builder(
                Component.literal("Clear"),
                btn -> { clearStrokes(); recognitionText = ""; statusMessage = ""; }
        ).bounds(btnX, btnY, clearW, 20).build());

        addRenderableWidget(Button.builder(
                Component.literal("Save Template"),
                btn -> saveTemplate()
        ).bounds(btnX + clearW + gap, btnY, saveW, 20).build());
    }

    // ════════════════════════════════════════════════════════════════════════════
    // Rendering
    // ════════════════════════════════════════════════════════════════════════════

    @Override
    public void render(@NotNull GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
        super.render(gui, mouseX, mouseY, partialTick);

        int centerX = displayX + displayW / 2;
        int infoY   = displayY + displayH + 30;

        if (!recognitionText.isEmpty()) {
            gui.drawCenteredString(font, recognitionText, centerX, infoY, 0xFF88FF88);
            infoY += font.lineHeight + 2;
        }
        if (!statusMessage.isEmpty()) {
            gui.drawCenteredString(font, statusMessage, centerX, infoY, statusColor);
        }
    }

    // ════════════════════════════════════════════════════════════════════════════
    // Input routing
    // ════════════════════════════════════════════════════════════════════════════

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        boolean result = super.mouseReleased(mouseX, mouseY, button);
        if (button == 0) updateRecognition();
        return result;
    }

    // ════════════════════════════════════════════════════════════════════════════
    // Recognition preview
    // ════════════════════════════════════════════════════════════════════════════

    private void updateRecognition() {
        List<GesturePoint> gesturePoints = savePoints();
        if (gesturePoints.isEmpty()) {
            recognitionText = "";
            return;
        }

        try {
            List<Point> raw = gesturePoints.stream()
                    .map(gp -> new Point(gp.x(), gp.y(), gp.strokeID()))
                    .toList();
            PointCloud rawCloud = new PointCloud("debug_candidate", raw);
            PointCloudPreprocessor.Processed proc =
                    PointCloudPreprocessor.process(rawCloud, Config.RESAMPLE_N.get());

            PDollarPlusRecognizer recognizer = new PDollarPlusRecognizer(
                    TemplateRegistry.get(),
                    Config.RECOGNITION_MIN_SCORE.get().floatValue());
            RecognitionResult result = recognizer.match(proc);

            if (RecognitionResult.UNKNOWN.equals(result.spellName())) {
                recognitionText = String.format("No match (best: %.0f%%)",
                        result.confidenceScore() * 100f);
            } else {
                recognitionText = String.format("Match: %s (%.0f%%)",
                        result.spellName(), result.confidenceScore() * 100f);
            }
        } catch (Exception e) {
            recognitionText = "Recognition error";
        }
    }

    // ════════════════════════════════════════════════════════════════════════════
    // Save logic
    // ════════════════════════════════════════════════════════════════════════════

    private void saveTemplate() {
        String name = spellNameBox.getValue().trim().toLowerCase();
        if (name.isEmpty()) {
            setStatus("Enter a sigil name!", 0xFFFF5555);
            return;
        }

        List<GesturePoint> pts = savePoints();
        if (pts.isEmpty()) {
            setStatus("Draw something first!", 0xFFFF5555);
            return;
        }

        try {
            Path dir = resolveTemplateDir();
            Files.createDirectories(dir);
            Path file = dir.resolve(name + ".json");

            JsonObject root;
            JsonArray variants;
            int existingCount;

            if (Files.exists(file)) {
                root = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
                variants = root.getAsJsonArray("variants");
                if (variants == null) {
                    variants = new JsonArray();
                    root.add("variants", variants);
                }
                existingCount = variants.size();
            } else {
                root = new JsonObject();
                root.addProperty("spell_name", name);
                variants = new JsonArray();
                root.add("variants", variants);
                existingCount = 0;
            }

            String variantName = "variant_" + (existingCount + 1);
            JsonObject variant = getJsonObject(variantName, pts);
            variants.add(variant);

            String json = new GsonBuilder().setPrettyPrinting().create().toJson(root);
            Files.writeString(file, json);

            setStatus("Saved '" + name + "' as " + variantName, 0xFF55FF55);
            clearStrokes();
            recognitionText = "";

        } catch (IOException e) {
            setStatus("Error: " + e.getMessage(), 0xFFFF5555);
        }
    }

    private static @NotNull JsonObject getJsonObject(String variantName, List<GesturePoint> pts) {
        JsonObject variant = new JsonObject();
        variant.addProperty("name", variantName);

        JsonArray pointsArr = new JsonArray();
        for (GesturePoint p : pts) {
            JsonObject pObj = new JsonObject();
            pObj.addProperty("x", Math.round(p.x() * 1000f) / 1000f);
            pObj.addProperty("y", Math.round(p.y() * 1000f) / 1000f);
            pObj.addProperty("stroke_id", p.strokeID());
            pointsArr.add(pObj);
        }
        variant.add("points", pointsArr);
        return variant;
    }

    private void setStatus(String message, int color) {
        statusMessage = message;
        statusColor   = color;
    }

    /**
     * Returns the spell_templates directory. In dev (running from Gradle), the source
     * resource tree is reachable one level above the game dir. Falls back to the
     * config folder so saves are never silently lost in production.
     */
    private static Path resolveTemplateDir() {
        Path devPath = FMLPaths.GAMEDIR.get()
                .resolve("..")
                .resolve("src/main/resources/data/witchhatateliermod/spell_templates")
                .normalize();
        if (Files.isDirectory(devPath)) return devPath;
        return FMLPaths.CONFIGDIR.get()
                .resolve("witchhatateliermod")
                .resolve("spell_templates");
    }
}
