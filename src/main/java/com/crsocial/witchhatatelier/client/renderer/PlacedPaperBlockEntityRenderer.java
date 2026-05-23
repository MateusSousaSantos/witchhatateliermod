package com.crsocial.witchhatatelier.client.renderer;

import com.crsocial.witchhatatelier.blocks.PlacedPaper;
import com.crsocial.witchhatatelier.blocks.PlacedPaperBlockEntity;
import com.crsocial.witchhatatelier.client.gesture.CanvasSize;
import com.crsocial.witchhatatelier.items.PaperType;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Renders gesture strokes on the outward face of any {@link PlacedPaper} block variant.
 *
 * <p>Strokes are rasterized onto a {@code gridSize × gridSize} grid where
 * {@code gridSize = canvasSize.width() / 2}, then each filled cell is emitted as one
 * axis-aligned quad ({@link ModRenderTypes#INK_STROKE}).</p>
 *
 * <ul>
 *   <li>Round paper types clip cells to an inscribed circle.</li>
 *   <li>Large paper types (2×2 block footprint) use face transforms that span 2 block
 *       units in both the right and down directions so the drawing fills the full surface.</li>
 * </ul>
 */
@OnlyIn(Dist.CLIENT)
public class PlacedPaperBlockEntityRenderer implements BlockEntityRenderer<PlacedPaperBlockEntity> {

    // ── Visual constants ─────────────────────────────────────────────────────────

    private static final float INK_R = 0.07f;
    private static final float INK_G = 0.04f;
    private static final float INK_B = 0.09f;
    private static final float INK_A = 0.86f;

    /** Inset margin for the smallest configured canvas size (UV space). */
    private static final float MIN_CANVAS_MARGIN = 0.25f;

    /** Inset margin for the largest configured canvas size (UV space). */
    private static final float MAX_CANVAS_MARGIN = 0.1f;

    /**
     * Block-local distance from the attachment wall to the paper's visible face.
     * Derived from the model element: {@code y = 0.5} pixels in model space (0–16)
     * → {@code 0.5 / 16 = 0.03125} in block-local space.
     * All six face-transform depth positions are expressed relative to this value.
     */
    private static final float PAPER_FACE = 0.5f / 16f;   // = 0.03125

    /** Tiny forward-offset so ink sits just in front of the paper surface without z-fighting. */
    private static final float Z_FIGHT_OFFSET = 0.002f;

    // ── Constructor ───────────────────────────────────────────────────────────────

    @SuppressWarnings("unused")
    public PlacedPaperBlockEntityRenderer(BlockEntityRendererProvider.Context ctx) {}

    // ── Render ────────────────────────────────────────────────────────────────────
    public static int log2(int N) {
        return (int) (Math.log(N) / Math.log(2));
    }
    @Override
    public void render(PlacedPaperBlockEntity be, float partialTick, @NotNull PoseStack poseStack,
                       @NotNull MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        if (be.getLevel() == null) return;

        CompoundTag data = be.getGestureData();
        if (data.isEmpty()) return;

        ListTag pointsList = data.getList("points", Tag.TAG_COMPOUND);
        if (pointsList.isEmpty()) return;

        PaperType paperType = be.getPaperType();

        // gridSize = canvasSize.width() / 2  (per-profile resolution)
        int   gridSize = paperType.getCanvasSize().width() / log2(paperType.getCanvasSize().width());
        float margin   = marginFor(paperType.getCanvasSize());
        float cellUV   = (1f - 2f * margin) / gridSize;
        float dotHalf  = cellUV * 0.5f;
        boolean isRound = paperType.isRound();
        boolean isLarge = paperType.isLarge();

        float circleR2 = 0f;
        if (isRound) { float r = 0.5f - margin; circleR2 = r * r; }

        // ── Group points by strokeID (preserves draw order) ──────────────────────
        Map<Integer, List<float[]>> strokes = new LinkedHashMap<>();
        for (int i = 0; i < pointsList.size(); i++) {
            CompoundTag pt = pointsList.getCompound(i);
            strokes.computeIfAbsent(pt.getInt("strokeID"), k -> new ArrayList<>())
                   .add(new float[]{ pt.getFloat("x"), pt.getFloat("y") });
        }

        // ── Rasterize all strokes onto a shared boolean grid ─────────────────────
        boolean[][] grid = new boolean[gridSize][gridSize];
        for (List<float[]> stroke : strokes.values()) {
            for (int i = 0; i < stroke.size(); i++) {
                int cx = uvToCell(stroke.get(i)[0], gridSize);
                int cy = uvToCell(stroke.get(i)[1], gridSize);
                markCell(grid, cx, cy, gridSize);
                if (i > 0) {
                    int px = uvToCell(stroke.get(i - 1)[0], gridSize);
                    int py = uvToCell(stroke.get(i - 1)[1], gridSize);
                    bresenham(grid, px, py, cx, cy, gridSize);
                }
            }
        }

        // ── Emit one axis-aligned quad per filled grid cell ───────────────────────
        BlockState state = be.getBlockState();
        Direction facing = state.getValue(PlacedPaper.FACING);
        FaceTransform ft = FaceTransform.forFacing(facing, isLarge);
        Matrix4f mat = poseStack.last().pose();
        VertexConsumer vc = bufferSource.getBuffer(ModRenderTypes.INK_STROKE);

        final float fr2 = circleR2;
        for (int cy = 0; cy < gridSize; cy++) {
            for (int cx = 0; cx < gridSize; cx++) {
                if (!grid[cy][cx]) continue;

                float cu = margin + (cx + 0.5f) * cellUV;
                float cv = margin + (cy + 0.5f) * cellUV;

                if (isRound) {
                    float du = cu - 0.5f, dv = cv - 0.5f;
                    if (du * du + dv * dv > fr2) continue;
                }

                float uMin = cu - dotHalf, uMax = cu + dotHalf;
                float vMin = cv - dotHalf, vMax = cv + dotHalf;

                // CCW winding seen from the outward face normal: TL, BL, BR, TR
                emitVertex(vc, mat, ft.project(uMin, vMin));
                emitVertex(vc, mat, ft.project(uMin, vMax));
                emitVertex(vc, mat, ft.project(uMax, vMax));
                emitVertex(vc, mat, ft.project(uMax, vMin));
            }
        }
    }

    // Large papers extend 2 blocks beyond the renderer's own block; disable frustum culling
    // so the drawing stays visible even when this block's own AABB is off-screen.
    @Override
    public boolean shouldRenderOffScreen(PlacedPaperBlockEntity be) {
        return be.getPaperType().isLarge();
    }

    // ── Private helpers ───────────────────────────────────────────────────────────

    // Small papers have a proportionally larger visible border in their texture,
    // so they need a bigger margin to keep ink inside the paper outline.
    // Interpolate from 0.25 on the smallest configured canvas to 0.10 on the largest.
    private static float marginFor(CanvasSize size) {
        float canvasEdge = Math.max(size.width(), size.height());
        float minCanvasEdge = Float.MAX_VALUE;
        float maxCanvasEdge = Float.MIN_VALUE;

        for (PaperType paperType : PaperType.values()) {
            float edge = Math.max(paperType.getCanvasSize().width(), paperType.getCanvasSize().height());
            minCanvasEdge = Math.min(minCanvasEdge, edge);
            maxCanvasEdge = Math.max(maxCanvasEdge, edge);
        }

        if (maxCanvasEdge <= minCanvasEdge) {
            return MIN_CANVAS_MARGIN;
        }

        float t = Math.clamp((canvasEdge - minCanvasEdge) / (maxCanvasEdge - minCanvasEdge), 0f, 1f);
        return MIN_CANVAS_MARGIN + (MAX_CANVAS_MARGIN - MIN_CANVAS_MARGIN) * t;
    }

    // Scales the full canvas UV [0,1] onto [0, gridSize) so the whole drawing fits
    // inside the margin-inset area on the block face instead of being clipped at the edges.
    private static int uvToCell(float uv, int gridSize) {
        return Math.clamp((int) (uv * gridSize), 0, gridSize - 1);
    }

    private static void markCell(boolean[][] grid, int cx, int cy, int size) {
        if (cx >= 0 && cx < size && cy >= 0 && cy < size) grid[cy][cx] = true;
    }

    /**
     * Thick Bresenham connecting {@code (x0,y0)} to {@code (x1,y1)}.
     * On diagonal steps both axis-adjacent neighbours are also marked so strokes stay
     * 4-connected and free of corner gaps at 1-cell line width.
     */
    private static void bresenham(boolean[][] grid, int x0, int y0, int x1, int y1, int size) {
        int dx = Math.abs(x1 - x0), dy = Math.abs(y1 - y0);
        int sx = x0 < x1 ? 1 : -1, sy = y0 < y1 ? 1 : -1;
        int err = dx - dy;
        while (true) {
            markCell(grid, x0, y0, size);
            if (x0 == x1 && y0 == y1) break;
            int e2 = 2 * err;
            boolean stepX = e2 > -dy, stepY = e2 < dx;
            if (stepX && stepY) {
                markCell(grid, x0 + sx, y0, size);
                markCell(grid, x0, y0 + sy, size);
            }
            if (stepX) { err -= dy; x0 += sx; }
            if (stepY) { err += dx; y0 += sy; }
        }
    }

    private static void emitVertex(VertexConsumer vc, Matrix4f mat, float[] pos) {
        vc.addVertex(mat, pos[0], pos[1], pos[2]).setColor(INK_R, INK_G, INK_B, INK_A);
    }

    // ── Face transform ────────────────────────────────────────────────────────────

    /**
     * Maps normalised canvas UV (u,v ∈ [0,1]) to block-local 3-D coordinates on the
     * outward face for each {@link Direction}.
     *
     * <p>Convention: u=0 is left, u=1 is right when facing the paper;
     * v=0 is top, v=1 is bottom.</p>
     *
     * <p>For large (2×2) papers {@code s=2} scales the right and down vectors so the
     * drawing fills a 2×2 block area in the face plane.</p>
     */
    private record FaceTransform(
            float bx, float by, float bz,   // top-left corner in block-local space
            float rx, float ry, float rz,   // right vector (+u direction)
            float dx, float dy, float dz    // down  vector (+v direction)
    ) {
        float[] project(float u, float v) {
            return new float[]{
                bx + rx * u + dx * v,
                by + ry * u + dy * v,
                bz + rz * u + dz * v
            };
        }

        static FaceTransform forFacing(Direction d, boolean large) {
            float s = large ? 2f : 1f;
            // The paper model element sits 0.5 px (PAPER_FACE = 0.5/16 block-units) from
            // the attachment wall. Ink quads are placed Z_FIGHT_OFFSET in front of that face
            // so they sit on the paper surface without z-fighting.
            float near = PAPER_FACE + Z_FIGHT_OFFSET;  // SOUTH / EAST / UP faces
            float far  = 1f - PAPER_FACE - Z_FIGHT_OFFSET; // NORTH / WEST / DOWN faces
            return switch (d) {
                // NORTH – paper at z = 1-PAPER_FACE; ink in front at z = far
                case NORTH -> new FaceTransform(0f, 1f, far,
                         s,  0f,  0f,
                         0f, -s,  0f);
                // SOUTH – paper at z = PAPER_FACE; ink in front at z = near (right mirrors to –X)
                case SOUTH -> new FaceTransform(s,  1f, near,
                        -s,  0f,  0f,
                         0f, -s,  0f);
                // EAST – paper at x = PAPER_FACE; ink in front at x = near
                case EAST -> new FaceTransform(near, 1f, s,
                         0f,  0f, -s,
                         0f, -s,  0f);
                // WEST – paper at x = 1-PAPER_FACE; ink in front at x = far
                case WEST -> new FaceTransform(far, 1f, 0f,
                         0f,  0f,  s,
                         0f, -s,  0f);
                // UP – paper at y = PAPER_FACE; ink in front at y = near
                case UP -> new FaceTransform(0f, near, 0f,
                         s,  0f,  0f,
                         0f,  0f,  s);
                // DOWN – paper at y = 1-PAPER_FACE; ink in front at y = far
                case DOWN -> new FaceTransform(0f, far, s,
                         s,  0f,  0f,
                         0f,  0f, -s);
            };
        }
    }
}
