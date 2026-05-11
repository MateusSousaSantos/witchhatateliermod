package com.crsocial.witchhatatelier.client.gesture;

import com.crsocial.witchhatatelier.WitchHatAtelierMod;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector2f;

import java.util.List;
import java.util.function.Consumer;

/**
 * Drawing canvas screen for gameplay spell papers.
 *
 * <p>The player right-clicks the wand while holding Paper in the off-hand to open
 * this screen. They draw multi-stroke gestures by clicking and dragging with the
 * mouse. When the screen is closed the strokes are flattened into a normalized
 * {@link GesturePoint} cloud and passed to an injected save callback.</p>
 */
@OnlyIn(Dist.CLIENT)
public class GestureCanvasScreen extends AbstractCanvasScreen {

    // ── State ──────────────────────────────────────────────────────────────────

    private final boolean readOnly;
    private final Consumer<List<GesturePoint>> saveHandler;

    // ── Spell Activation ────────────────────────────────────────────────────────
    private int clearAnimTimer = 0;

    // ── Construction ───────────────────────────────────────────────────────────

    public GestureCanvasScreen(GestureCanvasProfile profile,
                               List<GesturePoint> preloadedPoints,
                               boolean editable,
                               Consumer<List<GesturePoint>> saveHandler) {
        this(profile, preloadedPoints, editable, saveHandler, null);
    }

    public GestureCanvasScreen(GestureCanvasProfile profile,
                               List<GesturePoint> preloadedPoints,
                               boolean editable,
                               Consumer<List<GesturePoint>> saveHandler,
                               @Nullable BlockPos sourceBlock) {
        super(Component.translatable(profile.titleKey()), profile, preloadedPoints);
        this.readOnly = !editable;
        this.saveHandler = saveHandler;
    }

    // ── Overrides from AbstractCanvasScreen ─────────────────────────────────────

    @Override
    protected boolean isReadOnly() {
        return readOnly;
    }

    @Override
    protected ResourceLocation getCanvasCursorSprite() {
        // The texture file should be at: src/main/resources/assets/witchhatatelier/textures/gui/cursor.png
        return ResourceLocation.fromNamespaceAndPath(WitchHatAtelierMod.MODID, "textures/gui/cursor.png");
    }

    @Override
    protected void onStrokeFinished(List<Vector2f> finishedStroke) {
        if (!readOnly) {
        }
    }

    @Override
    public void onClose() {
        if (!readOnly && !strokes.isEmpty()) {
            saveHandler.accept(buildNormalizedPointCloud());
        }
        super.onClose();
    }


    // ── Rendering ──────────────────────────────────────────────────────────────

    @Override
    public void render(@NotNull GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
        renderBackground(gui, mouseX, mouseY, partialTick);
        renderCanvas(gui);
        // Title
        gui.drawCenteredString(font,
                Component.translatable(profile.titleKey()),
                canvasX + canvasW / 2,
                canvasY - font.lineHeight - 4,
                0xFFFFFFFF);

        // Read-only indicator
        if (readOnly) {
            gui.drawCenteredString(font,
                    Component.translatable(profile.readOnlyKey()),
                    canvasX + canvasW / 2,
                    canvasY + canvasH + 4,
                    0xFFAAAAAA);
        }

        super.render(gui, mouseX, mouseY, partialTick);
    }

    @Override
    public void tick() {
        super.tick();
        if (clearAnimTimer > 0) {
            clearAnimTimer--;
        }
    }
}
