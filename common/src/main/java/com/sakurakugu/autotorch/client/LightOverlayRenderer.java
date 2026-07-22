package com.sakurakugu.autotorch.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.MultiBufferSource;
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
    private static volatile RenderData renderData;

    private LightOverlayRenderer() {
    }

    public static void extract() {
        List<LightOverlayState.Marker> markers = LightOverlayState.isEnabled()
                ? LightOverlayState.markers() : List.of();
        renderData = new RenderData(markers, LightOverlayState.displayMode());
    }

    public static void submit(Vec3 camera, PoseStack poseStack, SubmitNodeCollector collector) {
        renderGeometry(camera, poseStack, (stack, renderer) -> collector.submitCustomGeometry(
                stack, RenderTypes.linesTranslucent(), renderer::render));
    }

    public static void render(Vec3 camera, PoseStack poseStack, MultiBufferSource.BufferSource buffers) {
        renderGeometry(camera, poseStack,
                (stack, renderer) -> renderer.render(stack.last(), buffers.getBuffer(RenderTypes.linesTranslucent())));
    }

    private static void renderGeometry(Vec3 camera, PoseStack poseStack, GeometrySink sink) {
        RenderData data = renderData;
        if (data == null || data.markers().isEmpty() || Minecraft.getInstance().level == null) {
            return;
        }

        poseStack.pushPose();
        poseStack.translate(-camera.x(), -camera.y(), -camera.z());
        sink.submit(poseStack, (pose, buffer) ->
                submitMarkers(pose, buffer, data.markers(), data.displayMode()));
        poseStack.popPose();
    }

    private static void submitMarkers(
            PoseStack.Pose pose, VertexConsumer buffer, List<LightOverlayState.Marker> markers,
            LightOverlayState.DisplayMode displayMode
    ) {
        for (LightOverlayState.Marker marker : markers) {
            if (displayMode == LightOverlayState.DisplayMode.NUMBERS) {
                submitNumber(pose, buffer, marker);
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
            line(pose, buffer, x0, y, z0, x1, y, z1, color, CROSS_LINE_WIDTH);
            line(pose, buffer, x1, y, z0, x0, y, z1, color, CROSS_LINE_WIDTH);
        }
    }

    private static void submitNumber(PoseStack.Pose pose, VertexConsumer buffer, LightOverlayState.Marker marker) {
        int value = marker.blockLight();
        boolean hasTensDigit = value >= 10;
        int digitCount = hasTensDigit ? 2 : 1;
        double totalWidth = digitCount * DIGIT_WIDTH + (digitCount - 1) * DIGIT_GAP;
        double startX = marker.pos().getX() + (1.0D - totalWidth) / 2.0D;
        double startZ = marker.pos().getZ() + (1.0D - DIGIT_HEIGHT) / 2.0D;
        double y = marker.pos().getY() + SURFACE_OFFSET;
        int color = markerColor(marker);

        if (hasTensDigit) {
            submitDigit(pose, buffer, startX, y, startZ, DIGIT_SEGMENTS[value / 10], color);
            startX += DIGIT_WIDTH + DIGIT_GAP;
        }
        submitDigit(pose, buffer, startX, y, startZ, DIGIT_SEGMENTS[value % 10], color);
    }

    private static int markerColor(LightOverlayState.Marker marker) {
        return switch (marker.riskType()) {
            case SWAMP_SLIME -> SWAMP_SLIME_RISK_COLOR;
            case DROWNED -> DROWNED_RISK_COLOR;
            case NORMAL -> marker.blockLight() > 0 ? SAFE_COLOR
                    : marker.nightOnly() ? NIGHT_RISK_COLOR : ALWAYS_RISK_COLOR;
        };
    }

    private static void submitDigit(
            PoseStack.Pose pose, VertexConsumer buffer, double x, double y, double z, int segments, int color
    ) {
        double middleZ = z + DIGIT_HEIGHT / 2.0D;
        double maxX = x + DIGIT_WIDTH;
        double maxZ = z + DIGIT_HEIGHT;
        segment(pose, buffer, segments, 0, x, y, z, maxX, z, color);
        segment(pose, buffer, segments, 1, maxX, y, z, maxX, middleZ, color);
        segment(pose, buffer, segments, 2, maxX, y, middleZ, maxX, maxZ, color);
        segment(pose, buffer, segments, 3, x, y, maxZ, maxX, maxZ, color);
        segment(pose, buffer, segments, 4, x, y, middleZ, x, maxZ, color);
        segment(pose, buffer, segments, 5, x, y, z, x, middleZ, color);
        segment(pose, buffer, segments, 6, x, y, middleZ, maxX, middleZ, color);
    }

    private static void segment(
            PoseStack.Pose pose, VertexConsumer buffer, int segments, int bit,
            double x1, double y, double z1, double x2, double z2, int color
    ) {
        if ((segments & 1 << bit) != 0) {
            line(pose, buffer, x1, y, z1, x2, y, z2, color, DIGIT_LINE_WIDTH);
        }
    }

    private static void line(
            PoseStack.Pose pose, VertexConsumer buffer,
            double x1, double y1, double z1, double x2, double y2, double z2, int color, float lineWidth
    ) {
        float nx = (float) (x2 - x1);
        float ny = (float) (y2 - y1);
        float nz = (float) (z2 - z1);
        buffer.addVertex(pose, (float) x1, (float) y1, (float) z1)
                .setColor(color).setNormal(pose, nx, ny, nz).setLineWidth(lineWidth);
        buffer.addVertex(pose, (float) x2, (float) y2, (float) z2)
                .setColor(color).setNormal(pose, nx, ny, nz).setLineWidth(lineWidth);
    }

    private record RenderData(
            List<LightOverlayState.Marker> markers, LightOverlayState.DisplayMode displayMode
    ) {
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
