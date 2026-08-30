package com.sakurakugu.autotorch.client;

import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.renderer.Tessellator;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.client.Minecraft;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.opengl.GL11;

/** 在可生成怪物的地面上，将缓存的光照等级绘制为交叉、数字或方框数字。 */
public final class LightOverlayRenderer {
    private static final int ALWAYS_RISK_COLOR = 0xE0FF3030;
    private static final int NIGHT_RISK_COLOR = 0xE0FFD23C;
    private static final int SAFE_COLOR = 0xE050E060;
    private static final int DROWNED_RISK_COLOR = 0xE040D8E8;
    private static final float CROSS_LINE_WIDTH = 2.5F;
    private static final float DIGIT_LINE_WIDTH = 4.0F;
    private static final double SURFACE_OFFSET = 0.0125D;
    private static final double CROSS_MARGIN = 0.14D;
    private static final double DIGIT_WIDTH = 0.24D;
    private static final double DIGIT_HEIGHT = 0.58D;
    private static final double DIGIT_GAP = 0.08D;
    private static final int[] DIGIT_SEGMENTS = {
            0b0111111, 0b0000110, 0b1011011, 0b1001111, 0b1100110,
            0b1101101, 0b1111101, 0b0000111, 0b1111111, 0b1101111
    };
    private static final List<LightOverlayState.Marker> NO_MARKERS = Collections.emptyList();
    private static volatile RenderData renderData;

    private LightOverlayRenderer() {
    }

    public static void extract() {
        List<LightOverlayState.Marker> markers = LightOverlayState.isEnabled()
                ? LightOverlayState.markers() : NO_MARKERS;
        LightOverlayState.DisplayMode displayMode = LightOverlayState.displayMode();
        RenderData current = renderData;
        // 扫描完成时状态会发布新的不可变列表，因此列表身份就是无需遍历的版本标记。
        if (current != null && current.sourceMarkers() == markers && current.displayMode() == displayMode) {
            return;
        }
        renderData = buildRenderData(markers, displayMode);
    }

    public static void render(Vec3d camera) {
        renderGeometry(camera, renderData, false);
    }

    private static void renderFiltered(
            Vec3d camera,
            Predicate<LightOverlayState.Marker> filter
    ) {
        RenderData current = renderData;
        if (current == null) return;
        RenderData filtered = buildRenderData(
                current.sourceMarkers(), current.displayMode(), filter, 8);
        renderGeometry(camera, filtered, true);
    }

    public static void renderWaterVisible(
            Vec3d camera, Predicate<Vec3d> isVisibleTarget
    ) {
        renderFiltered(camera,
                marker -> marker.riskType() == LightOverlayState.RiskType.DROWNED
                        && isVisibleTarget.test(markerTarget(marker)));
    }

    private static Vec3d markerTarget(LightOverlayState.Marker marker) {
        return new Vec3d(
                marker.pos().getX() + 0.5D,
                marker.pos().getY() + SURFACE_OFFSET,
                marker.pos().getZ() + 0.5D
        );
    }

    private static void setupWaterVisibleRenderState() {
        // 世界渲染回调会继承当前状态，纯色线条需要显式关闭纹理。
        GlStateManager.disableTexture();
        GlStateManager.enableBlend();
        GlStateManager.blendFuncSeparate(
                GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA
        );
        GlStateManager.disableDepthTest();
        GlStateManager.depthMask(false);
        GlStateManager.disableCull();

        GlStateManager.pushMatrix();
        GlStateManager.scalef(0.99975586F, 0.99975586F, 0.99975586F);
        GlStateManager.lineWidth(Math.max(
                CROSS_LINE_WIDTH,
                (float) Minecraft.getInstance().mainWindow.getWidth() / 1920.0F * CROSS_LINE_WIDTH
        ));
    }

    private static void clearWaterVisibleRenderState() {
        GlStateManager.lineWidth(1.0F);
        GlStateManager.popMatrix();

        GlStateManager.enableCull();
        GlStateManager.depthMask(true);
        if (ClientConfig.isLightOverlayRenderThrough()) {
            GlStateManager.disableDepthTest();
        } else {
            GlStateManager.enableDepthTest();
        }
        GlStateManager.disableBlend();
        GlStateManager.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        GlStateManager.enableTexture();
    }

    private static void renderGeometry(Vec3d camera, RenderData data, boolean waterVisible) {
        if (data == null || data.lineCount() == 0 || Minecraft.getInstance().world == null) {
            return;
        }
        if (waterVisible) {
            setupWaterVisibleRenderState();
        } else {
            setupLineRenderState(data.displayMode());
        }
        Tessellator tesselator = Tessellator.getInstance();
        BufferBuilder builder = tesselator.getBuffer();
        builder.begin(GL11.GL_LINES, DefaultVertexFormats.POSITION_COLOR);
        builder.setTranslation(-camera.x, -camera.y, -camera.z);
        submitLines(Pose.INSTANCE, new VertexConsumer(builder), data);
        tesselator.draw();
        builder.setTranslation(0.0D, 0.0D, 0.0D);
        if (waterVisible) {
            clearWaterVisibleRenderState();
        } else {
            clearLineRenderState();
        }
    }

    private static void setupLineRenderState(LightOverlayState.DisplayMode displayMode) {
        GlStateManager.disableTexture();
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        GlStateManager.enableDepthTest();
        GlStateManager.depthMask(false);
        GlStateManager.disableCull();
        GlStateManager.lineWidth(displayMode == LightOverlayState.DisplayMode.CROSSES
                ? CROSS_LINE_WIDTH : DIGIT_LINE_WIDTH);
    }

    private static void clearLineRenderState() {
        GlStateManager.lineWidth(1.0F);
        GlStateManager.enableCull();
        GlStateManager.depthMask(true);
        GlStateManager.disableBlend();
        GlStateManager.enableTexture();
    }

    private static RenderData buildRenderData(
            List<LightOverlayState.Marker> markers, LightOverlayState.DisplayMode displayMode
    ) {
        return buildRenderData(markers, displayMode, marker -> true, markers.size());
    }

    private static RenderData buildRenderData(
            List<LightOverlayState.Marker> markers, LightOverlayState.DisplayMode displayMode,
            Predicate<LightOverlayState.Marker> filter, int expectedMarkerCount
    ) {
        GeometryBuilder geometry = new GeometryBuilder(expectedMarkerCount);
        for (LightOverlayState.Marker marker : markers) {
            if (!filter.test(marker)) {
                continue;
            }
            if (displayMode != LightOverlayState.DisplayMode.CROSSES) {
                addNumber(geometry, marker);
                if (displayMode == LightOverlayState.DisplayMode.BOXED_NUMBERS) {
                    addBox(geometry, marker, markerColor(marker));
                }
                continue;
            }
            if (!marker.isRisk()) {
                continue;
            }
            double x0 = marker.pos().getX() + CROSS_MARGIN;
            double x1 = marker.pos().getX() + 1.0D - CROSS_MARGIN;
            double y = marker.pos().getY() + SURFACE_OFFSET;
            double z0 = marker.pos().getZ() + CROSS_MARGIN;
            double z1 = marker.pos().getZ() + 1.0D - CROSS_MARGIN;
            int color = markerColor(marker);
            geometry.add(x0, y, z0, x1, y, z1, color);
            geometry.add(x1, y, z0, x0, y, z1, color);
        }
        return geometry.build(markers, displayMode);
    }

    private static void addNumber(GeometryBuilder geometry, LightOverlayState.Marker marker) {
        int value = marker.blockLight();
        boolean hasTensDigit = value >= 10;
        int digitCount = hasTensDigit ? 2 : 1;
        double totalWidth = digitCount * DIGIT_WIDTH + (digitCount - 1) * DIGIT_GAP;
        double startX = marker.pos().getX() + (1.0D - totalWidth) / 2.0D;
        double startZ = marker.pos().getZ() + (1.0D - DIGIT_HEIGHT) / 2.0D;
        double y = marker.pos().getY() + SURFACE_OFFSET;
        int color = markerColor(marker);

        if (hasTensDigit) {
            addDigit(geometry, startX, y, startZ, DIGIT_SEGMENTS[value / 10], color);
            startX += DIGIT_WIDTH + DIGIT_GAP;
        }
        addDigit(geometry, startX, y, startZ, DIGIT_SEGMENTS[value % 10], color);
    }

    private static void addBox(GeometryBuilder geometry, LightOverlayState.Marker marker, int color) {
        double x0 = marker.pos().getX() + CROSS_MARGIN;
        double x1 = marker.pos().getX() + 1.0D - CROSS_MARGIN;
        double z0 = marker.pos().getZ() + CROSS_MARGIN;
        double z1 = marker.pos().getZ() + 1.0D - CROSS_MARGIN;
        double y = marker.pos().getY() + SURFACE_OFFSET;
        geometry.add(x0, y, z0, x1, y, z0, color);
        geometry.add(x1, y, z0, x1, y, z1, color);
        geometry.add(x1, y, z1, x0, y, z1, color);
        geometry.add(x0, y, z1, x0, y, z0, color);
    }

    private static int markerColor(LightOverlayState.Marker marker) {
        if (marker.riskType() == LightOverlayState.RiskType.DROWNED) {
            return DROWNED_RISK_COLOR;
        }
        return marker.blockLight() > 0 ? SAFE_COLOR
                : marker.nightOnly() ? NIGHT_RISK_COLOR : ALWAYS_RISK_COLOR;
    }

    private static void addDigit(
            GeometryBuilder geometry, double x, double y, double z, int segments, int color
    ) {
        double middleZ = z + DIGIT_HEIGHT / 2.0D;
        double maxX = x + DIGIT_WIDTH;
        double maxZ = z + DIGIT_HEIGHT;
        addSegment(geometry, segments, 0, x, y, z, maxX, z, color);
        addSegment(geometry, segments, 1, maxX, y, z, maxX, middleZ, color);
        addSegment(geometry, segments, 2, maxX, y, middleZ, maxX, maxZ, color);
        addSegment(geometry, segments, 3, x, y, maxZ, maxX, maxZ, color);
        addSegment(geometry, segments, 4, x, y, middleZ, x, maxZ, color);
        addSegment(geometry, segments, 5, x, y, z, x, middleZ, color);
        addSegment(geometry, segments, 6, x, y, middleZ, maxX, middleZ, color);
    }

    private static void addSegment(
            GeometryBuilder geometry, int segments, int bit,
            double x1, double y, double z1, double x2, double z2, int color
    ) {
        if ((segments & 1 << bit) != 0) {
            geometry.add(x1, y, z1, x2, y, z2, color);
        }
    }

    private static void submitLines(Pose pose, VertexConsumer buffer, RenderData data) {
        float[] coordinates = data.coordinates();
        int[] colors = data.colors();
        float lineWidth = data.displayMode() == LightOverlayState.DisplayMode.CROSSES
                ? CROSS_LINE_WIDTH : DIGIT_LINE_WIDTH;
        for (int line = 0, offset = 0; line < data.lineCount(); line++, offset += 6) {
            line(pose, buffer,
                    coordinates[offset], coordinates[offset + 1], coordinates[offset + 2],
                    coordinates[offset + 3], coordinates[offset + 4], coordinates[offset + 5],
                    colors[line], lineWidth);
        }
    }

    private static void line(
            Pose pose, VertexConsumer buffer,
            float x1, float y1, float z1, float x2, float y2, float z2, int color, float lineWidth
    ) {
        applyColor(buffer.vertex(pose.pose(), x1, y1, z1), color)
                .endVertex();
        applyColor(buffer.vertex(pose.pose(), x2, y2, z2), color)
                .endVertex();
    }

    private static VertexConsumer applyColor(VertexConsumer vertex, int color) {
        return vertex.color((color >> 16) & 0xFF, (color >> 8) & 0xFF,
                color & 0xFF, (color >>> 24) & 0xFF);
    }

    private static final class RenderData {
        private final List<LightOverlayState.Marker> sourceMarkers;
        private final LightOverlayState.DisplayMode displayMode;
        private final float[] coordinates;
        private final int[] colors;
        private final int lineCount;

        private RenderData(
                List<LightOverlayState.Marker> sourceMarkers, LightOverlayState.DisplayMode displayMode,
                float[] coordinates, int[] colors, int lineCount
        ) {
            this.sourceMarkers = sourceMarkers;
            this.displayMode = displayMode;
            this.coordinates = coordinates;
            this.colors = colors;
            this.lineCount = lineCount;
        }

        private List<LightOverlayState.Marker> sourceMarkers() { return sourceMarkers; }
        private LightOverlayState.DisplayMode displayMode() { return displayMode; }
        private float[] coordinates() { return coordinates; }
        private int[] colors() { return colors; }
        private int lineCount() { return lineCount; }
    }

    private static final class GeometryBuilder {
        private float[] coordinates;
        private int[] colors;
        private int lineCount;

        private GeometryBuilder(int markerCount) {
            int initialLines = Math.max(16, markerCount * 2);
            coordinates = new float[initialLines * 6];
            colors = new int[initialLines];
        }

        private void add(double x1, double y1, double z1, double x2, double y2, double z2, int color) {
            ensureCapacity(lineCount + 1);
            int offset = lineCount * 6;
            coordinates[offset] = (float) x1;
            coordinates[offset + 1] = (float) y1;
            coordinates[offset + 2] = (float) z1;
            coordinates[offset + 3] = (float) x2;
            coordinates[offset + 4] = (float) y2;
            coordinates[offset + 5] = (float) z2;
            colors[lineCount] = color;
            lineCount++;
        }

        private void ensureCapacity(int requiredLines) {
            if (requiredLines <= colors.length) {
                return;
            }
            int newLength = Math.max(requiredLines, colors.length * 2);
            coordinates = Arrays.copyOf(coordinates, newLength * 6);
            colors = Arrays.copyOf(colors, newLength);
        }

        private RenderData build(
                List<LightOverlayState.Marker> markers, LightOverlayState.DisplayMode displayMode
        ) {
            return new RenderData(
                    markers,
                    displayMode,
                    Arrays.copyOf(coordinates, lineCount * 6),
                    Arrays.copyOf(colors, lineCount),
                    lineCount
            );
        }
    }

    private enum Pose {
        INSTANCE;

        private Object pose() {
            return null;
        }
    }

    private static final class VertexConsumer {
        private final BufferBuilder builder;

        private VertexConsumer(BufferBuilder builder) {
            this.builder = builder;
        }

        private VertexConsumer vertex(Object ignored, float x, float y, float z) {
            builder.pos(x, y, z);
            return this;
        }

        private VertexConsumer color(int red, int green, int blue, int alpha) {
            builder.color(red, green, blue, alpha);
            return this;
        }

        private void endVertex() {
            builder.endVertex();
        }
    }

    /** 隔离固定管线调用，避免旧版跨映射时改写 Mojang 的平台辅助类名。 */
    private static final class GlStateManager {
        private enum SourceFactor { SRC_ALPHA, ONE }
        private enum DestFactor { ONE_MINUS_SRC_ALPHA }

        private static void disableTexture() { GL11.glDisable(GL11.GL_TEXTURE_2D); }
        private static void enableTexture() { GL11.glEnable(GL11.GL_TEXTURE_2D); }
        private static void enableBlend() { GL11.glEnable(GL11.GL_BLEND); }
        private static void disableBlend() { GL11.glDisable(GL11.GL_BLEND); }
        private static void blendFunc(SourceFactor source, DestFactor destination) {
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        }
        private static void blendFuncSeparate(
                SourceFactor sourceRgb, DestFactor destinationRgb,
                SourceFactor sourceAlpha, DestFactor destinationAlpha
        ) {
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        }
        private static void disableDepthTest() { GL11.glDisable(GL11.GL_DEPTH_TEST); }
        private static void enableDepthTest() { GL11.glEnable(GL11.GL_DEPTH_TEST); }
        private static void depthMask(boolean enabled) { GL11.glDepthMask(enabled); }
        private static void disableCull() { GL11.glDisable(GL11.GL_CULL_FACE); }
        private static void enableCull() { GL11.glEnable(GL11.GL_CULL_FACE); }
        private static void lineWidth(float width) { GL11.glLineWidth(width); }
        private static void pushMatrix() { GL11.glPushMatrix(); }
        private static void popMatrix() { GL11.glPopMatrix(); }
        private static void scalef(float x, float y, float z) { GL11.glScalef(x, y, z); }
    }
}
