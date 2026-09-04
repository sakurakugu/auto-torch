package com.sakurakugu.autotorch.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.sakurakugu.autotorch.client.AutoTorchRenderTypes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/** 在可生成怪物的地面上，将缓存的光照等级绘制为经过深度测试的交叉标记或纹理数字。 */
public final class LightOverlayRenderer {
    private static final ResourceLocation NUMBER_TEXTURE =
            new ResourceLocation("autotorch", "textures/misc/light_level_numbers_large.png");
    private static final ResourceLocation MEDIUM_NUMBER_TEXTURE =
            new ResourceLocation("autotorch", "textures/misc/light_level_numbers_medium.png");
    private static final int FULL_BRIGHT_LIGHT = 0xF0;
    private static final int ALWAYS_RISK_COLOR = 0xE0FF3030;
    private static final int NIGHT_RISK_COLOR = 0xE0FFD23C;
    private static final int SAFE_COLOR = 0xE050E060;
    private static final int SWAMP_SLIME_RISK_COLOR = 0xE0E050E0;
    private static final int DROWNED_RISK_COLOR = 0xE040D8E8;
    private static final double SURFACE_OFFSET = 0.0125D;
    private static final double CROSS_MARGIN = 0.14D;
    // 图集中的字形已在 64x64 单元格内居中。
    private static final double NUMBER_OFFSET_X = 0.0D;
    private static final double NUMBER_OFFSET_Z = 0.25D;
    private static final double BOXED_NUMBER_OFFSET_Z = 0.0D;
    private static final double NUMBER_MARGIN = 0.1D;
    private static final double NUMBER_SIZE = 1.0D;
    private static final float NUMBER_TEXTURE_CELL_SIZE = 0.25F;
    private static final List<LightOverlayState.MarkerColumn> NO_COLUMNS = List.of();
    private static Map<Long, ColumnRenderData> columnGeometry = Map.of();
    private static volatile RenderData renderData;
    private static volatile RenderData drownedRenderData;
    private static DrownedMarkerVisibility drownedMarkerVisibility = (level, camera, marker) -> false;
    private static final RenderType SEE_THROUGH_LINES = AutoTorchRenderTypes.seeThroughLines();

    private LightOverlayRenderer() {
    }

    public static void setDrownedMarkerVisibility(DrownedMarkerVisibility visibility) {
        drownedMarkerVisibility = visibility == null
                ? (level, camera, marker) -> false : visibility;
    }

    public static void extract() {
        List<LightOverlayState.MarkerColumn> columns = LightOverlayState.isEnabled()
                ? LightOverlayState.markerColumns() : NO_COLUMNS;
        LightOverlayState.DisplayMode displayMode = LightOverlayState.displayMode();
        RenderData current = renderData;
        // 状态发布新的不可变列列表，因此列表身份就是无需遍历的版本标记。
        if (current != null && current.sourceColumns() == columns && current.displayMode() == displayMode) {
            return;
        }
        renderData = buildRenderData(columns, displayMode);
        drownedRenderData = buildDrownedRenderData(columns, displayMode);
    }

    public static void render(Vec3 camera, PoseStack poseStack, MultiBufferSource buffers) {
        RenderData data = renderData;
        if (data == null) {
            return;
        }
        if (data.displayMode() != LightOverlayState.DisplayMode.CROSSES) {
            // 方框数字样式：数字平面置于方框内部，方框单独使用线段渲染以保持清晰边界。
            ResourceLocation numberTexture = data.displayMode() == LightOverlayState.DisplayMode.BOXED_NUMBERS
                    ? MEDIUM_NUMBER_TEXTURE : NUMBER_TEXTURE;
            RenderType numberRenderType = ClientConfig.isLightOverlayRenderThrough()
                    ? AutoTorchRenderTypes.seeThroughNumbers(numberTexture) : RenderType.text(numberTexture);
            renderGeometry(camera, poseStack, buffers.getBuffer(numberRenderType),
                    (pose, buffer) -> submitNumbers(pose, buffer, data, camera));
            if (data.displayMode() == LightOverlayState.DisplayMode.BOXED_NUMBERS) {
                renderGeometry(camera, poseStack, buffers.getBuffer(
                        ClientConfig.isLightOverlayRenderThrough() ? SEE_THROUGH_LINES : RenderType.lines()),
                        (pose, buffer) -> submitLines(pose, buffer, data, camera));
            }
        } else {
            renderGeometry(camera, poseStack, buffers.getBuffer(
                    ClientConfig.isLightOverlayRenderThrough() ? SEE_THROUGH_LINES : RenderType.lines()),
                    (pose, buffer) -> submitLines(pose, buffer, data, camera));
        }

        if (!ClientConfig.isLightOverlayRenderThrough()) {
            RenderData drowned = visibleDrownedData(camera, data.displayMode());
            if (drowned != null && drowned.renderableCount() > 0) {
                RenderType type = data.displayMode() == LightOverlayState.DisplayMode.CROSSES
                        ? SEE_THROUGH_LINES
                        : AutoTorchRenderTypes.seeThroughNumbers(
                                data.displayMode() == LightOverlayState.DisplayMode.BOXED_NUMBERS
                                        ? MEDIUM_NUMBER_TEXTURE : NUMBER_TEXTURE);
                renderGeometry(drowned, camera, poseStack, buffers.getBuffer(type),
                        (pose, buffer) -> {
                            if (data.displayMode() == LightOverlayState.DisplayMode.CROSSES) {
                                submitLines(pose, buffer, drowned, camera);
                            } else {
                                submitNumbers(pose, buffer, drowned, camera);
                            }
                        });
                if (data.displayMode() == LightOverlayState.DisplayMode.BOXED_NUMBERS) {
                    renderGeometry(drowned, camera, poseStack, buffers.getBuffer(SEE_THROUGH_LINES),
                            (pose, buffer) -> submitLines(pose, buffer, drowned, camera));
                }
            }
        }
    }

    /** 在当前世界渲染阶段结束数字批次，避免共享缓冲区延迟提交到错误的相机矩阵。 */
    public static void endBatches(MultiBufferSource.BufferSource buffers) {
        buffers.endBatch(RenderType.text(NUMBER_TEXTURE));
        buffers.endBatch(RenderType.text(MEDIUM_NUMBER_TEXTURE));
        AutoTorchRenderTypes.endSeeThroughNumberBatches(buffers);
    }

    private static void renderGeometry(
            Vec3 camera, PoseStack poseStack, VertexConsumer buffer, GeometryRenderer renderer
    ) {
        RenderData data = renderData;
        renderGeometry(data, camera, poseStack, buffer, renderer);
    }

    private static void renderGeometry(
            RenderData data, Vec3 camera, PoseStack poseStack, VertexConsumer buffer, GeometryRenderer renderer
    ) {
        if (data == null || data.renderableCount() == 0 || Minecraft.getInstance().level == null) {
            return;
        }

        poseStack.pushPose();
        // 顶点在提交时先转换为相机相对坐标，避免大世界坐标分别转 float 后再相减造成精度损失。
        renderer.render(poseStack.last(), buffer);
        poseStack.popPose();
    }

    private static RenderData buildDrownedRenderData(
            List<LightOverlayState.MarkerColumn> columns, LightOverlayState.DisplayMode displayMode
    ) {
        List<LightOverlayState.MarkerColumn> drownedColumns = new ArrayList<>();
        for (LightOverlayState.MarkerColumn column : columns) {
            List<LightOverlayState.Marker> drownedMarkers = new ArrayList<>();
            for (LightOverlayState.Marker marker : column.markers()) {
                if (marker.riskType() == LightOverlayState.RiskType.DROWNED) {
                    drownedMarkers.add(marker);
                }
            }
            if (!drownedMarkers.isEmpty()) {
                drownedColumns.add(new LightOverlayState.MarkerColumn(
                        column.key(), column.minY(),
                        Collections.unmodifiableList(new ArrayList<LightOverlayState.Marker>(drownedMarkers))));
            }
        }
        return buildRenderData(
                Collections.unmodifiableList(new ArrayList<LightOverlayState.MarkerColumn>(drownedColumns)),
                displayMode, false);
    }

    private static RenderData visibleDrownedData(Vec3 camera, LightOverlayState.DisplayMode displayMode) {
        RenderData source = drownedRenderData;
        Minecraft minecraft = Minecraft.getInstance();
        if (source == null || minecraft.level == null || minecraft.player == null) {
            return null;
        }

        List<LightOverlayState.MarkerColumn> columns = new ArrayList<>();
        for (LightOverlayState.MarkerColumn column : source.sourceColumns()) {
            List<LightOverlayState.Marker> visibleMarkers = new ArrayList<>();
            for (LightOverlayState.Marker marker : column.markers()) {
                // 忽略流体进行射线检测，使水下标记可以穿过水面，但实体方块仍会遮挡标记。
                if (drownedMarkerVisibility.isVisible(minecraft.level, camera, marker)) {
                    visibleMarkers.add(marker);
                }
            }
            if (!visibleMarkers.isEmpty()) {
                columns.add(new LightOverlayState.MarkerColumn(
                        column.key(), column.minY(),
                        Collections.unmodifiableList(new ArrayList<LightOverlayState.Marker>(visibleMarkers))));
            }
        }
        return buildRenderData(
                Collections.unmodifiableList(new ArrayList<LightOverlayState.MarkerColumn>(columns)),
                displayMode, false);
    }

    private static RenderData buildRenderData(
            List<LightOverlayState.MarkerColumn> columns, LightOverlayState.DisplayMode displayMode
    ) {
        return buildRenderData(columns, displayMode, true);
    }

    private static RenderData buildRenderData(
            List<LightOverlayState.MarkerColumn> columns, LightOverlayState.DisplayMode displayMode,
            boolean cacheGeometry
    ) {
        Map<Long, ColumnRenderData> previousGeometry = cacheGeometry
                ? columnGeometry : Collections.<Long, ColumnRenderData>emptyMap();
        Map<Long, ColumnRenderData> nextGeometry = new HashMap<>(columns.size());
        List<ColumnRenderData> visibleGeometry = new ArrayList<>(columns.size());
        int totalLines = 0;
        int totalQuads = 0;
        for (LightOverlayState.MarkerColumn column : columns) {
            ColumnRenderData geometry = previousGeometry.get(column.key());
            if (geometry == null || geometry.sourceMarkers() != column.markers()
                    || geometry.displayMode() != displayMode) {
                geometry = buildColumnRenderData(column.markers(), displayMode);
            }
            nextGeometry.put(column.key(), geometry);
            visibleGeometry.add(geometry);
            totalLines += geometry.lineCount();
            totalQuads += geometry.numberQuads().size();
        }
        if (cacheGeometry) {
            columnGeometry = nextGeometry;
        }
        return new RenderData(columns, displayMode, List.copyOf(visibleGeometry), totalLines, totalQuads);
    }

    private static ColumnRenderData buildColumnRenderData(
            List<LightOverlayState.Marker> markers, LightOverlayState.DisplayMode displayMode
    ) {
        GeometryBuilder geometry = new GeometryBuilder(markers.size());
        for (LightOverlayState.Marker marker : markers) {
            if (displayMode == LightOverlayState.DisplayMode.NUMBERS) {
                addNumber(geometry, marker, false);
                continue;
            }
            if (displayMode == LightOverlayState.DisplayMode.BOXED_NUMBERS) {
                addNumber(geometry, marker, true);
                // 绿色标记表示光照已高于刷怪阈值，只显示数字，不再绘制外围边框。
                if (marker.isRisk()) {
                    addNumberBox(geometry, marker);
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

    private static void addNumber(GeometryBuilder geometry, LightOverlayState.Marker marker, boolean boxed) {
        geometry.addNumber(marker.pos().getX() + NUMBER_OFFSET_X,
                marker.pos().getY() + SURFACE_OFFSET,
                marker.pos().getZ() + (boxed ? BOXED_NUMBER_OFFSET_Z : NUMBER_OFFSET_Z),
                marker.blockLight(), markerColor(marker), boxed ? (float) NUMBER_SIZE : 1.0F);
    }

    private static void addNumberBox(GeometryBuilder geometry, LightOverlayState.Marker marker) {
        double x0 = marker.pos().getX() + NUMBER_MARGIN;
        double x1 = marker.pos().getX() + 1.0D - NUMBER_MARGIN;
        double y = marker.pos().getY() + SURFACE_OFFSET;
        double z0 = marker.pos().getZ() + NUMBER_MARGIN;
        double z1 = marker.pos().getZ() + 1.0D - NUMBER_MARGIN;
        int color = markerColor(marker);
        geometry.add(x0, y, z0, x0, y, z1, color);
        geometry.add(x0, y, z1, x1, y, z1, color);
        geometry.add(x1, y, z1, x1, y, z0, color);
        geometry.add(x1, y, z0, x0, y, z0, color);
    }

    private static int markerColor(LightOverlayState.Marker marker) {
        return switch (marker.riskType()) {
            case SWAMP_SLIME -> SWAMP_SLIME_RISK_COLOR;
            case DROWNED -> DROWNED_RISK_COLOR;
            case NORMAL -> marker.blockLight() > 0 ? SAFE_COLOR
                    : marker.nightOnly() ? NIGHT_RISK_COLOR : ALWAYS_RISK_COLOR;
        };
    }

    private static void submitLines(PoseStack.Pose pose, VertexConsumer buffer, RenderData data, Vec3 camera) {
        for (ColumnRenderData column : data.columns()) {
            double[] coordinates = column.coordinates();
            int[] colors = column.colors();
            for (int line = 0, offset = 0; line < column.lineCount(); line++, offset += 6) {
                double x1 = coordinates[offset] - camera.x();
                double y1 = coordinates[offset + 1] - camera.y();
                double z1 = coordinates[offset + 2] - camera.z();
                double x2 = coordinates[offset + 3] - camera.x();
                double y2 = coordinates[offset + 4] - camera.y();
                double z2 = coordinates[offset + 5] - camera.z();
                line(pose, buffer, x1, y1, z1, x2, y2, z2, colors[line]);
            }
        }
    }

    private static void submitNumbers(
            PoseStack.Pose pose, VertexConsumer buffer, RenderData data, Vec3 camera
    ) {
        for (ColumnRenderData column : data.columns()) {
            for (NumberQuad quad : column.numberQuads()) {
                float x = (float) (quad.x() - camera.x());
                float y = (float) (quad.y() - camera.y());
                float z = (float) (quad.z() - camera.z());
                float size = quad.size();
                float u = (quad.value() & 3) * NUMBER_TEXTURE_CELL_SIZE;
                float v = (quad.value() >> 2) * NUMBER_TEXTURE_CELL_SIZE;
                buffer.vertex(pose.pose(), x, y, z)
                        .color((quad.color() >> 16) & 0xFF, (quad.color() >> 8) & 0xFF,
                                quad.color() & 0xFF, quad.color() >>> 24).uv(u, v)
                        .uv2(FULL_BRIGHT_LIGHT, FULL_BRIGHT_LIGHT).endVertex();
                buffer.vertex(pose.pose(), x, y, z + size)
                        .color((quad.color() >> 16) & 0xFF, (quad.color() >> 8) & 0xFF,
                                quad.color() & 0xFF, quad.color() >>> 24)
                        .uv(u, v + NUMBER_TEXTURE_CELL_SIZE)
                        .uv2(FULL_BRIGHT_LIGHT, FULL_BRIGHT_LIGHT).endVertex();
                buffer.vertex(pose.pose(), x + size, y, z + size)
                        .color((quad.color() >> 16) & 0xFF, (quad.color() >> 8) & 0xFF,
                                quad.color() & 0xFF, quad.color() >>> 24)
                        .uv(u + NUMBER_TEXTURE_CELL_SIZE, v + NUMBER_TEXTURE_CELL_SIZE)
                        .uv2(FULL_BRIGHT_LIGHT, FULL_BRIGHT_LIGHT).endVertex();
                buffer.vertex(pose.pose(), x + size, y, z)
                        .color((quad.color() >> 16) & 0xFF, (quad.color() >> 8) & 0xFF,
                                quad.color() & 0xFF, quad.color() >>> 24)
                        .uv(u + NUMBER_TEXTURE_CELL_SIZE, v)
                        .uv2(FULL_BRIGHT_LIGHT, FULL_BRIGHT_LIGHT).endVertex();
            }
        }
    }

    private static void line(
            PoseStack.Pose pose, VertexConsumer buffer,
            double x1, double y1, double z1, double x2, double y2, double z2,
            int color
    ) {
        float nx = (float) (x2 - x1);
        float ny = (float) (y2 - y1);
        float nz = (float) (z2 - z1);
        buffer.vertex(pose.pose(), (float) x1, (float) y1, (float) z1)
                .color((color >> 16) & 0xFF, (color >> 8) & 0xFF, color & 0xFF, color >>> 24).normal(pose.normal(), nx, ny, nz).endVertex();
        buffer.vertex(pose.pose(), (float) x2, (float) y2, (float) z2)
                .color((color >> 16) & 0xFF, (color >> 8) & 0xFF, color & 0xFF, color >>> 24).normal(pose.normal(), nx, ny, nz).endVertex();
    }

    private record RenderData(
            List<LightOverlayState.MarkerColumn> sourceColumns, LightOverlayState.DisplayMode displayMode,
            List<ColumnRenderData> columns, int lineCount, int quadCount
    ) {
        private int renderableCount() {
            return lineCount + quadCount;
        }
    }

    private record ColumnRenderData(
            List<LightOverlayState.Marker> sourceMarkers, LightOverlayState.DisplayMode displayMode,
            double[] coordinates, int[] colors, int lineCount, List<NumberQuad> numberQuads
    ) {
    }

    private record NumberQuad(double x, double y, double z, int value, int color, float size) {
    }

    private static final class GeometryBuilder {
        private double[] coordinates;
        private int[] colors;
        private int lineCount;
        private final List<NumberQuad> numberQuads = new ArrayList<>();

        private GeometryBuilder(int markerCount) {
            int initialLines = Math.max(16, markerCount * 2);
            coordinates = new double[initialLines * 6];
            colors = new int[initialLines];
        }

        private void add(double x1, double y1, double z1, double x2, double y2, double z2, int color) {
            ensureCapacity(lineCount + 1);
            int offset = lineCount * 6;
            coordinates[offset] = x1;
            coordinates[offset + 1] = y1;
            coordinates[offset + 2] = z1;
            coordinates[offset + 3] = x2;
            coordinates[offset + 4] = y2;
            coordinates[offset + 5] = z2;
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

        private ColumnRenderData build(
                List<LightOverlayState.Marker> markers, LightOverlayState.DisplayMode displayMode
        ) {
            return new ColumnRenderData(
                    markers,
                    displayMode,
                    Arrays.copyOf(coordinates, lineCount * 6),
                    Arrays.copyOf(colors, lineCount),
                    lineCount,
                    List.copyOf(numberQuads)
            );
        }
    }

    @FunctionalInterface
    private interface GeometryRenderer {
        void render(PoseStack.Pose pose, VertexConsumer buffer);
    }
}

