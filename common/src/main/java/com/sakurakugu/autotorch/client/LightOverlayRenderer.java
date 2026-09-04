package com.sakurakugu.autotorch.client;

import net.minecraft.client.renderer.Tessellator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Vec3;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

/** 在可生成怪物的地面上，将缓存的光照等级绘制为交叉、纹理数字或方框数字。 */
public final class LightOverlayRenderer {
    private static final ResourceLocation NUMBER_TEXTURE =
            new ResourceLocation("autotorch", "textures/misc/light_level_numbers_large.png");
    private static final ResourceLocation MEDIUM_NUMBER_TEXTURE =
            new ResourceLocation("autotorch", "textures/misc/light_level_numbers_medium.png");
    private static final int FULL_BRIGHT_LIGHT = 0xF0;
    private static final int ALWAYS_RISK_COLOR = 0xE0FF3030;
    private static final int NIGHT_RISK_COLOR = 0xE0FFD23C;
    private static final int SAFE_COLOR = 0xE050E060;
    private static final int DROWNED_RISK_COLOR = 0xE040D8E8;
    private static final float CROSS_LINE_WIDTH = 2.5F;
    private static final double SURFACE_OFFSET = 0.0125D;
    private static final double CROSS_MARGIN = 0.14D;
    // 图集中的字形已在 64x64 单元格内居中。
    private static final double NUMBER_OFFSET_X = 0.0D;
    private static final double NUMBER_OFFSET_Z = 0.25D;
    private static final double BOXED_NUMBER_OFFSET_Z = 0.0D;
    private static final double NUMBER_MARGIN = 0.1D;
    private static final double NUMBER_SIZE = 1.0D;
    private static final float NUMBER_TEXTURE_CELL_SIZE = 0.25F;
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

    public static void render(Vec3 camera) {
        renderData(camera, renderData, false);
    }

    private static void renderFiltered(
            Vec3 camera,
            Predicate<LightOverlayState.Marker> filter
    ) {
        RenderData current = renderData;
        if (current == null) return;
        RenderData filtered = buildRenderData(
                current.sourceMarkers(), current.displayMode(), filter, 8);
        renderData(camera, filtered, true);
    }

    public static void renderWaterVisible(
            Vec3 camera, Predicate<Vec3> isVisibleTarget
    ) {
        renderFiltered(camera,
                marker -> marker.riskType() == LightOverlayState.RiskType.DROWNED
                        && isVisibleTarget.test(markerTarget(marker)));
    }

    private static Vec3 markerTarget(LightOverlayState.Marker marker) {
        return Vec3.createVectorHelper(
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
                (float) Minecraft.getMinecraft().displayWidth / 1920.0F * CROSS_LINE_WIDTH
        ));
    }

    private static void clearWaterVisibleRenderState() {
        GlStateManager.lineWidth(1.0F);
        GlStateManager.popMatrix();

        GlStateManager.enableCull();
        GlStateManager.depthMask(true);
        GlStateManager.enableDepthTest();
        GlStateManager.disableBlend();
        GlStateManager.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        GlStateManager.enableTexture();
    }

    private static void renderData(Vec3 camera, RenderData data, boolean waterVisible) {
        if (data == null || Minecraft.getMinecraft().theWorld == null) {
            return;
        }
        if (data.displayMode() != LightOverlayState.DisplayMode.CROSSES) {
            renderNumbers(camera, data, waterVisible);
            if (data.displayMode() == LightOverlayState.DisplayMode.BOXED_NUMBERS) {
                renderLines(camera, data, waterVisible);
            }
        } else {
            renderLines(camera, data, waterVisible);
        }
    }

    private static void renderLines(Vec3 camera, RenderData data, boolean waterVisible) {
        if (data.lineCount() == 0) {
            return;
        }
        if (waterVisible) {
            setupWaterVisibleRenderState();
        } else {
            setupLineRenderState();
        }
        Tessellator tesselator = Tessellator.instance;
        tesselator.startDrawing(GL11.GL_LINES);
        tesselator.setTranslation(-camera.xCoord, -camera.yCoord, -camera.zCoord);
        submitLines(Pose.INSTANCE, new VertexConsumer(tesselator), data);
        tesselator.draw();
        tesselator.setTranslation(0.0D, 0.0D, 0.0D);
        if (waterVisible) {
            clearWaterVisibleRenderState();
        } else {
            clearLineRenderState();
        }
    }

    private static void renderNumbers(Vec3 camera, RenderData data, boolean waterVisible) {
        if (data.numberQuads().isEmpty()) {
            return;
        }
        setupNumberRenderState(waterVisible, data.displayMode());
        Tessellator tesselator = Tessellator.instance;
        tesselator.startDrawingQuads();
        tesselator.setTranslation(-camera.xCoord, -camera.yCoord, -camera.zCoord);
        submitNumbers(Pose.INSTANCE, new VertexConsumer(tesselator), data);
        tesselator.draw();
        tesselator.setTranslation(0.0D, 0.0D, 0.0D);
        clearNumberRenderState(waterVisible);
    }

    private static void setupLineRenderState() {
        GlStateManager.disableTexture();
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        if (ClientConfig.isLightOverlayRenderThrough()) {
            GlStateManager.disableDepthTest();
        } else {
            GlStateManager.enableDepthTest();
        }
        GlStateManager.depthMask(false);
        GlStateManager.disableCull();
        GlStateManager.lineWidth(CROSS_LINE_WIDTH);
    }

    private static void clearLineRenderState() {
        GlStateManager.lineWidth(1.0F);
        GlStateManager.enableCull();
        GlStateManager.depthMask(true);
        GlStateManager.disableBlend();
        GlStateManager.enableTexture();
        GlStateManager.enableDepthTest();
    }

    private static void setupNumberRenderState(boolean waterVisible, LightOverlayState.DisplayMode displayMode) {
        GlStateManager.enableTexture();
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        if (waterVisible || ClientConfig.isLightOverlayRenderThrough()) {
            GlStateManager.disableDepthTest();
        } else {
            GlStateManager.enableDepthTest();
        }
        GlStateManager.depthMask(false);
        GlStateManager.disableCull();
        GlStateManager.color4f(1.0F, 1.0F, 1.0F, 1.0F);
        Minecraft.getMinecraft().getTextureManager().bindTexture(
                displayMode == LightOverlayState.DisplayMode.BOXED_NUMBERS
                        ? MEDIUM_NUMBER_TEXTURE : NUMBER_TEXTURE);
    }

    private static void clearNumberRenderState(boolean waterVisible) {
        GlStateManager.enableCull();
        GlStateManager.depthMask(true);
        GlStateManager.enableDepthTest();
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
            if (displayMode == LightOverlayState.DisplayMode.NUMBERS) {
                addNumber(geometry, marker, false);
                continue;
            }
            if (displayMode == LightOverlayState.DisplayMode.BOXED_NUMBERS) {
                addNumber(geometry, marker, true);
                if (marker.isRisk()) {
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

    private static void addNumber(
            GeometryBuilder geometry, LightOverlayState.Marker marker, boolean boxed
    ) {
        geometry.addNumber(marker.pos().getX() + NUMBER_OFFSET_X,
                marker.pos().getY() + SURFACE_OFFSET,
                marker.pos().getZ() + (boxed ? BOXED_NUMBER_OFFSET_Z : NUMBER_OFFSET_Z),
                marker.blockLight(), markerColor(marker), (float) NUMBER_SIZE);
    }

    private static void addBox(GeometryBuilder geometry, LightOverlayState.Marker marker, int color) {
        double x0 = marker.pos().getX() + NUMBER_MARGIN;
        double x1 = marker.pos().getX() + 1.0D - NUMBER_MARGIN;
        double z0 = marker.pos().getZ() + NUMBER_MARGIN;
        double z1 = marker.pos().getZ() + 1.0D - NUMBER_MARGIN;
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

    private static void submitLines(Pose pose, VertexConsumer buffer, RenderData data) {
        float[] coordinates = data.coordinates();
        int[] colors = data.colors();
        for (int line = 0, offset = 0; line < data.lineCount(); line++, offset += 6) {
            line(pose, buffer,
                    coordinates[offset], coordinates[offset + 1], coordinates[offset + 2],
                    coordinates[offset + 3], coordinates[offset + 4], coordinates[offset + 5],
                    colors[line], CROSS_LINE_WIDTH);
        }
    }

    private static void submitNumbers(Pose pose, VertexConsumer buffer, RenderData data) {
        for (NumberQuad quad : data.numberQuads()) {
            float u = (quad.value() & 3) * NUMBER_TEXTURE_CELL_SIZE;
            float v = (quad.value() >> 2) * NUMBER_TEXTURE_CELL_SIZE;
            float x = (float) quad.x();
            float y = (float) quad.y();
            float z = (float) quad.z();
            float size = quad.size();
            texturedVertex(buffer, x, y, z, u, v, quad.color());
            texturedVertex(buffer, x, y, z + size, u, v + NUMBER_TEXTURE_CELL_SIZE, quad.color());
            texturedVertex(buffer, x + size, y, z + size,
                    u + NUMBER_TEXTURE_CELL_SIZE, v + NUMBER_TEXTURE_CELL_SIZE, quad.color());
            texturedVertex(buffer, x + size, y, z,
                    u + NUMBER_TEXTURE_CELL_SIZE, v, quad.color());
        }
    }

    private static void texturedVertex(
            VertexConsumer buffer, float x, float y, float z,
            float u, float v, int color
    ) {
        buffer.vertex(null, x, y, z)
                .uv(u, v)
                .uv2(FULL_BRIGHT_LIGHT, FULL_BRIGHT_LIGHT)
                .color((color >> 16) & 0xFF, (color >> 8) & 0xFF,
                        color & 0xFF, (color >>> 24) & 0xFF)
                .endVertex();
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
        private final List<NumberQuad> numberQuads;

        private RenderData(
                List<LightOverlayState.Marker> sourceMarkers, LightOverlayState.DisplayMode displayMode,
                float[] coordinates, int[] colors, int lineCount, List<NumberQuad> numberQuads
        ) {
            this.sourceMarkers = sourceMarkers;
            this.displayMode = displayMode;
            this.coordinates = coordinates;
            this.colors = colors;
            this.lineCount = lineCount;
            this.numberQuads = numberQuads;
        }

        private List<LightOverlayState.Marker> sourceMarkers() { return sourceMarkers; }
        private LightOverlayState.DisplayMode displayMode() { return displayMode; }
        private float[] coordinates() { return coordinates; }
        private int[] colors() { return colors; }
        private int lineCount() { return lineCount; }
        private List<NumberQuad> numberQuads() { return numberQuads; }
    }

    private static final class NumberQuad {
        private final double x;
        private final double y;
        private final double z;
        private final int value;
        private final int color;
        private final float size;

        private NumberQuad(double x, double y, double z, int value, int color, float size) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.value = value;
            this.color = color;
            this.size = size;
        }

        private double x() { return x; }
        private double y() { return y; }
        private double z() { return z; }
        private int value() { return value; }
        private int color() { return color; }
        private float size() { return size; }
    }

    private static final class GeometryBuilder {
        private float[] coordinates;
        private int[] colors;
        private int lineCount;
        private final List<NumberQuad> numberQuads = new ArrayList<>();

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

        private void addNumber(double x, double y, double z, int value, int color, float size) {
            numberQuads.add(new NumberQuad(x, y, z, value, color, size));
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
                    lineCount,
                    Collections.unmodifiableList(new ArrayList<>(numberQuads))
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
        private final Tessellator builder;
        private double x;
        private double y;
        private double z;
        private int red;
        private int green;
        private int blue;
        private int alpha;
        private double u;
        private double v;
        private int brightness;
        private boolean textured;
        private boolean lit;

        private VertexConsumer(Tessellator builder) {
            this.builder = builder;
        }

        private VertexConsumer vertex(Object ignored, float x, float y, float z) {
            this.x = x;
            this.y = y;
            this.z = z;
            return this;
        }

        private VertexConsumer color(int red, int green, int blue, int alpha) {
            this.red = red;
            this.green = green;
            this.blue = blue;
            this.alpha = alpha;
            return this;
        }

        private VertexConsumer uv(float u, float v) {
            this.u = u;
            this.v = v;
            this.textured = true;
            return this;
        }

        private VertexConsumer uv2(int u, int v) {
            this.brightness = u << 16 | v;
            this.lit = true;
            return this;
        }

        private void endVertex() {
            builder.setColorRGBA(red, green, blue, alpha);
            if (textured) {
                builder.setTextureUV(u, v);
            }
            if (lit) {
                builder.setBrightness(brightness);
            }
            builder.addVertex(x, y, z);
        }
    }

    /** 隔离固定管线调用，避免旧版跨映射时改写 Mojang 的平台辅助类名。 */
    private static final class GlStateManager {
        private enum SourceFactor { SRC_ALPHA, ONE }
        private enum DestFactor { ONE_MINUS_SRC_ALPHA }

        private static void disableTexture() { GL11.glDisable(GL11.GL_TEXTURE_2D); }
        private static void enableTexture() { GL11.glEnable(GL11.GL_TEXTURE_2D); }
        private static void color4f(float red, float green, float blue, float alpha) {
            GL11.glColor4f(red, green, blue, alpha);
        }
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
