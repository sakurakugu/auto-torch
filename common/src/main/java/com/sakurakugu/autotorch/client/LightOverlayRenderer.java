package com.sakurakugu.autotorch.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.Arrays;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.world.phys.Vec3;

/** 在可生成怪物的地面上，将缓存的光照等级绘制为经过深度测试的交叉标记或七段数字。 */
public final class LightOverlayRenderer {
    private static final int ALWAYS_RISK_COLOR = 0xE0FF3030;
    private static final int NIGHT_RISK_COLOR = 0xE0FFD23C;
    private static final int SAFE_COLOR = 0xE050E060;
    private static final int SWAMP_SLIME_RISK_COLOR = 0xE0E050E0;
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
    private static final List<LightOverlayState.Marker> NO_MARKERS = List.of();
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

    public static void submit(Vec3 camera, PoseStack poseStack, SubmitNodeCollector collector) {
        renderGeometry(camera, poseStack, (stack, renderer) -> collector.submitCustomGeometry(
                stack, RenderTypes.linesTranslucent(), renderer::render));
    }

    private static void renderGeometry(Vec3 camera, PoseStack poseStack, GeometrySink sink) {
        RenderData data = renderData;
        if (data == null || data.lineCount() == 0 || Minecraft.getInstance().level == null) {
            return;
        }

        poseStack.pushPose();
        poseStack.translate(-camera.x(), -camera.y(), -camera.z());
        sink.submit(poseStack, (pose, buffer) -> submitLines(pose, buffer, data));
        poseStack.popPose();
    }

    private static RenderData buildRenderData(
            List<LightOverlayState.Marker> markers, LightOverlayState.DisplayMode displayMode
    ) {
        GeometryBuilder geometry = new GeometryBuilder(markers.size());
        for (LightOverlayState.Marker marker : markers) {
            if (displayMode == LightOverlayState.DisplayMode.NUMBERS) {
                addNumber(geometry, marker);
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

    private static int markerColor(LightOverlayState.Marker marker) {
        return switch (marker.riskType()) {
            case SWAMP_SLIME -> SWAMP_SLIME_RISK_COLOR;
            case DROWNED -> DROWNED_RISK_COLOR;
            case NORMAL -> marker.blockLight() > 0 ? SAFE_COLOR
                    : marker.nightOnly() ? NIGHT_RISK_COLOR : ALWAYS_RISK_COLOR;
        };
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

    private static void submitLines(PoseStack.Pose pose, VertexConsumer buffer, RenderData data) {
        float[] coordinates = data.coordinates();
        int[] colors = data.colors();
        float lineWidth = data.displayMode() == LightOverlayState.DisplayMode.NUMBERS
                ? DIGIT_LINE_WIDTH : CROSS_LINE_WIDTH;
        for (int line = 0, offset = 0; line < data.lineCount(); line++, offset += 6) {
            line(pose, buffer,
                    coordinates[offset], coordinates[offset + 1], coordinates[offset + 2],
                    coordinates[offset + 3], coordinates[offset + 4], coordinates[offset + 5],
                    colors[line], lineWidth);
        }
    }

    private static void line(
            PoseStack.Pose pose, VertexConsumer buffer,
            float x1, float y1, float z1, float x2, float y2, float z2, int color, float lineWidth
    ) {
        float nx = x2 - x1;
        float ny = y2 - y1;
        float nz = z2 - z1;
        buffer.addVertex(pose, x1, y1, z1)
                .setColor(color).setNormal(pose, nx, ny, nz).setLineWidth(lineWidth);
        buffer.addVertex(pose, x2, y2, z2)
                .setColor(color).setNormal(pose, nx, ny, nz).setLineWidth(lineWidth);
    }

    private record RenderData(
            List<LightOverlayState.Marker> sourceMarkers, LightOverlayState.DisplayMode displayMode,
            float[] coordinates, int[] colors, int lineCount
    ) {
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

    @FunctionalInterface
    private interface GeometrySink {
        void submit(PoseStack poseStack, GeometryRenderer renderer);
    }

    @FunctionalInterface
    private interface GeometryRenderer {
        void render(PoseStack.Pose pose, VertexConsumer buffer);
    }
}
