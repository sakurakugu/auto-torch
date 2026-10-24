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
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/** 在可生成怪物的地面上，将缓存的光照等级绘制为经过深度测试的交叉标记或纹理数字。 */
public final class LightOverlayRenderer {
    // 该构造器在 1.21 起标记为待删除，但 common 源码还要给 fabric 编译
    // （原版映射没有 fromNamespaceAndPath/parse 等工厂方法），只能保留构造器写法；
    // forge/neoforge 复用同一份源码时会产生 [removal] 告警，这里按最小范围抑制。
    @SuppressWarnings("removal")
    private static final ResourceLocation NUMBER_TEXTURE =
            new ResourceLocation("autotorch", "textures/misc/light_level_numbers_large.png");
    // 该构造器在 1.21 起标记为待删除，但 common 源码还要给 fabric 编译
    // （原版映射没有 fromNamespaceAndPath/parse 等工厂方法），只能保留构造器写法；
    // forge/neoforge 复用同一份源码时会产生 [removal] 告警，这里按最小范围抑制。
    @SuppressWarnings("removal")
    private static final ResourceLocation MEDIUM_NUMBER_TEXTURE =
            new ResourceLocation("autotorch", "textures/misc/light_level_numbers_medium.png");
    private static final int FULL_BRIGHT_LIGHT = 0xF0;
    private static final float LINE_WIDTH_REFERENCE_DISTANCE = 8.0F;
    private static final double LINE_WIDTH_REFERENCE_DISTANCE_SQUARED =
            LINE_WIDTH_REFERENCE_DISTANCE * LINE_WIDTH_REFERENCE_DISTANCE;
    private static final double SURFACE_OFFSET = 0.0125D;
    private static final double CROSS_MARGIN = 0.14D;
    // 图集中的字形已在 64x64 单元格内居中。
    private static final double NUMBER_OFFSET_X = 0.0D;
    private static final double NUMBER_OFFSET_Z = 0.25D;
    private static final double BOXED_NUMBER_OFFSET_Z = 0.0D;
    private static final double NUMBER_MARGIN = 0.1D;
    private static final double NUMBER_SIZE = 1.0D;
    private static final float NUMBER_TEXTURE_CELL_SIZE = 0.25F;
    private static final List<LightOverlayState.MarkerColumn> NO_COLUMNS = java.util.Arrays.asList();
    private static Map<Long, ColumnRenderData> columnGeometry = java.util.Collections.emptyMap();
    private static volatile RenderData renderData;
    // 记录几何缓存对应的配置快照，配置变化时丢弃缓存以便按新颜色和线宽重建。
    private static ClientConfigSnapshot geometryConfig;
    private static volatile RenderData drownedRenderData;
    private static DrownedMarkerVisibility drownedMarkerVisibility = (level, camera, marker) -> false;
    private LightOverlayRenderer() {
    }

    public static void setDrownedMarkerVisibility(DrownedMarkerVisibility visibility) {
        drownedMarkerVisibility = visibility == null
                ? (level, camera, marker) -> false : visibility;
    }

    public static void extract() {
        ClientConfigSnapshot config = ClientConfig.snapshot();
        boolean configChanged = geometryConfig != config;
        geometryConfig = config;
        if (configChanged) {
            columnGeometry = java.util.Collections.emptyMap();
        }

        List<LightOverlayState.MarkerColumn> columns = LightOverlayState.isEnabled()
                ? LightOverlayState.markerColumns() : NO_COLUMNS;
        LightOverlayState.DisplayMode displayMode = LightOverlayState.displayMode();
        RenderData current = renderData;
        // 状态发布新的不可变列列表，因此列表身份就是无需遍历的版本标记。
        if (!configChanged && current != null && current.sourceColumns() == columns
                && current.displayMode() == displayMode) {
            return;
        }
        renderData = buildRenderData(config, columns, displayMode);
        drownedRenderData = buildDrownedRenderData(config, columns, displayMode);
    }

    public static void render(Vec3 camera, PoseStack poseStack, MultiBufferSource buffers) {
        ClientConfigSnapshot config = ClientConfig.snapshot();
        RenderData data = renderData;
        if (data == null) {
            return;
        }
        Direction numberDirection = config.lightOverlayNumberRotation
                ? Direction.fromYRot(Minecraft.getInstance().gameRenderer.getMainCamera().getYRot())
                : Direction.NORTH;
        if (data.displayMode() != LightOverlayState.DisplayMode.CROSSES) {
            // 方框数字样式：数字平面置于方框内部，方框单独使用线段渲染以保持清晰边界。
            ResourceLocation numberTexture = data.displayMode() == LightOverlayState.DisplayMode.BOXED_NUMBERS
                    ? MEDIUM_NUMBER_TEXTURE : NUMBER_TEXTURE;
            RenderType numberRenderType = AutoTorchRenderTypes.numbers(
                    numberTexture, config.lightOverlayRenderThrough);
            renderGeometry(camera, poseStack, buffers.getBuffer(numberRenderType),
                    (pose, buffer) -> submitNumbers(pose, buffer, data, camera, numberDirection));
            if (data.displayMode() == LightOverlayState.DisplayMode.BOXED_NUMBERS) {
                renderLines(config, camera, poseStack, buffers, data);
            }
        } else {
            renderLines(config, camera, poseStack, buffers, data);
        }

        if (!config.lightOverlayRenderThrough) {
            RenderData drowned = visibleDrownedData(config, camera, data.displayMode());
            if (drowned != null && drowned.renderableCount() > 0) {
                if (data.displayMode() == LightOverlayState.DisplayMode.CROSSES) {
                    renderLines(config, drowned, camera, poseStack, buffers, true);
                } else {
                    ResourceLocation numberTexture = data.displayMode() == LightOverlayState.DisplayMode.BOXED_NUMBERS
                            ? MEDIUM_NUMBER_TEXTURE : NUMBER_TEXTURE;
                    RenderType type = AutoTorchRenderTypes.numbers(numberTexture, true);
                    renderGeometry(drowned, camera, poseStack, buffers.getBuffer(type),
                            (pose, buffer) -> submitNumbers(pose, buffer, drowned, camera, numberDirection));
                    if (data.displayMode() == LightOverlayState.DisplayMode.BOXED_NUMBERS) {
                        renderLines(config, drowned, camera, poseStack, buffers, true);
                    }
                }
            }
        }
    }

    /** 在当前世界渲染阶段结束数字批次，避免共享缓冲区延迟提交到错误的相机矩阵。 */
    public static void endBatches(MultiBufferSource.BufferSource buffers) {
        AutoTorchRenderTypes.endNumberBatches(buffers);
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
            ClientConfigSnapshot config,
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
                config,
                Collections.unmodifiableList(new ArrayList<LightOverlayState.MarkerColumn>(drownedColumns)),
                displayMode, false);
    }

    private static RenderData visibleDrownedData(
            ClientConfigSnapshot config, Vec3 camera, LightOverlayState.DisplayMode displayMode
    ) {
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
                config,
                Collections.unmodifiableList(new ArrayList<LightOverlayState.MarkerColumn>(columns)),
                displayMode, false);
    }

    private static RenderData buildRenderData(
            ClientConfigSnapshot config,
            List<LightOverlayState.MarkerColumn> columns, LightOverlayState.DisplayMode displayMode
    ) {
        return buildRenderData(config, columns, displayMode, true);
    }

    private static RenderData buildRenderData(
            ClientConfigSnapshot config,
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
                geometry = buildColumnRenderData(config, column.markers(), displayMode);
            }
            nextGeometry.put(column.key(), geometry);
            visibleGeometry.add(geometry);
            totalLines += geometry.lineCount();
            totalQuads += geometry.numberQuads().size();
        }
        if (cacheGeometry) {
            columnGeometry = nextGeometry;
        }
        return new RenderData(columns, displayMode, com.google.common.collect.ImmutableList.copyOf(visibleGeometry), totalLines, totalQuads);
    }

    private static ColumnRenderData buildColumnRenderData(
            ClientConfigSnapshot config,
            List<LightOverlayState.Marker> markers, LightOverlayState.DisplayMode displayMode
    ) {
        GeometryBuilder geometry = new GeometryBuilder(markers.size());
        for (LightOverlayState.Marker marker : markers) {
            if (displayMode == LightOverlayState.DisplayMode.NUMBERS) {
                addNumber(config, geometry, marker, false);
                continue;
            }
            if (displayMode == LightOverlayState.DisplayMode.BOXED_NUMBERS) {
                addNumber(config, geometry, marker, true);
                // 绿色标记表示光照已高于刷怪阈值，只显示数字，不再绘制外围边框。
                if (marker.isRisk()) {
                    addNumberBox(config, geometry, marker);
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
            int color = markerColor(config, marker);
            geometry.add(x0, y, z0, x1, y, z1, color);
            geometry.add(x1, y, z0, x0, y, z1, color);
        }
        return geometry.build(markers, displayMode);
    }

    private static void addNumber(
            ClientConfigSnapshot config, GeometryBuilder geometry, LightOverlayState.Marker marker, boolean boxed
    ) {
        geometry.addNumber(marker.pos().getX() + NUMBER_OFFSET_X,
                marker.pos().getY() + SURFACE_OFFSET,
                marker.pos().getZ() + BOXED_NUMBER_OFFSET_Z,
                marker.blockLight(), markerColor(config, marker), boxed ? (float) NUMBER_SIZE : 1.0F,
                boxed ? 0.0F : (float) NUMBER_OFFSET_Z);
    }

    private static void addNumberBox(
            ClientConfigSnapshot config, GeometryBuilder geometry, LightOverlayState.Marker marker
    ) {
        double x0 = marker.pos().getX() + NUMBER_MARGIN;
        double x1 = marker.pos().getX() + 1.0D - NUMBER_MARGIN;
        double y = marker.pos().getY() + SURFACE_OFFSET;
        double z0 = marker.pos().getZ() + NUMBER_MARGIN;
        double z1 = marker.pos().getZ() + 1.0D - NUMBER_MARGIN;
        int color = markerColor(config, marker);
        geometry.add(x0, y, z0, x0, y, z1, color);
        geometry.add(x0, y, z1, x1, y, z1, color);
        geometry.add(x1, y, z1, x1, y, z0, color);
        geometry.add(x1, y, z0, x0, y, z0, color);
    }

    private static int markerColor(ClientConfigSnapshot config, LightOverlayState.Marker marker) {
        switch (marker.riskType()) {
            case SLIME: return ((config.lightOverlayMarkerAlpha << 24) | config.lightOverlaySlimeColorRgb);
            case DROWNED: return ((config.lightOverlayMarkerAlpha << 24) | config.lightOverlayDrownedColorRgb);
            default: return marker.blockLight() > 0
                    ? ((config.lightOverlayMarkerAlpha << 24) | config.lightOverlaySafeColorRgb)
                    : marker.nightOnly()
                            ? ((config.lightOverlayMarkerAlpha << 24) | config.lightOverlayNightRiskColorRgb)
                            : ((config.lightOverlayMarkerAlpha << 24) | config.lightOverlayAlwaysRiskColorRgb);
        }
    }

    private static void renderLines(
            ClientConfigSnapshot config, Vec3 camera, PoseStack poseStack, MultiBufferSource buffers, RenderData data
    ) {
        renderLines(config, data, camera, poseStack, buffers, config.lightOverlayRenderThrough);
    }

    private static void renderLines(
            ClientConfigSnapshot config, RenderData data, Vec3 camera, PoseStack poseStack,
            MultiBufferSource buffers, boolean seeThrough
    ) {
        if (data.renderableCount() == 0 || Minecraft.getInstance().level == null) {
            return;
        }
        poseStack.pushPose();
        PoseStack.Pose pose = poseStack.last();
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
                float width = scaledLineWidth(config, (float) config.lightOverlayNearLineWidth,
                        x1, y1, z1, x2, y2, z2);
                VertexConsumer buffer = buffers.getBuffer(AutoTorchRenderTypes.lines(width, seeThrough));
                line(pose, buffer, x1, y1, z1, x2, y2, z2, colors[line]);
            }
        }
        poseStack.popPose();
    }

    private static float scaledLineWidth(ClientConfigSnapshot config, float baseWidth,
                                         double x1, double y1, double z1,
                                         double x2, double y2, double z2) {
        double x = (x1 + x2) * 0.5D;
        double y = (y1 + y2) * 0.5D;
        double z = (z1 + z2) * 0.5D;
        double distanceSquared = x * x + y * y + z * z;
        if (distanceSquared <= LINE_WIDTH_REFERENCE_DISTANCE_SQUARED) {
            return baseWidth;
        }
        double distance = Math.sqrt(distanceSquared);
        return Math.max((float) config.lightOverlayFarLineWidth,
                (float) (baseWidth * LINE_WIDTH_REFERENCE_DISTANCE / distance));
    }

    private static void submitNumbers(
            PoseStack.Pose pose, VertexConsumer buffer, RenderData data, Vec3 camera, Direction direction
    ) {
        for (ColumnRenderData column : data.columns()) {
            for (NumberQuad quad : column.numberQuads()) {
                float x = (float) (quad.x() - camera.x());
                float y = (float) (quad.y() - camera.y());
                float z = (float) (quad.z() - camera.z());
                float offset = quad.directionOffset();
                switch (direction) {
                    case SOUTH: z -= offset; break;
                    case EAST: x -= offset; break;
                    case WEST: x += offset; break;
                    default: z += offset; break;
                }
                float size = quad.size();
                float u = (quad.value() & 3) * NUMBER_TEXTURE_CELL_SIZE;
                float v = (quad.value() >> 2) * NUMBER_TEXTURE_CELL_SIZE;
                switch (direction) {
                    case SOUTH: submitNumberQuad(pose, buffer, quad.color(), x + size, y, z + size,
                            x + size, z, x, z, x, z + size, u, v); break;
                    case EAST: submitNumberQuad(pose, buffer, quad.color(), x + size, y, z,
                            x, z, x, z + size, x + size, z + size, u, v); break;
                    case WEST: submitNumberQuad(pose, buffer, quad.color(), x, y, z + size,
                            x + size, z + size, x + size, z, x, z, u, v); break;
                    default: submitNumberQuad(pose, buffer, quad.color(), x, y, z,
                            x, z + size, x + size, z + size, x + size, z, u, v); break;
                }
            }
        }
    }

    /** 按左上、左下、右下、右上的顺序提交数字四边形。 */
    private static void submitNumberQuad(
            PoseStack.Pose pose, VertexConsumer buffer, int color,
            float topLeftX, float y, float topLeftZ,
            float bottomLeftX, float bottomLeftZ,
            float bottomRightX, float bottomRightZ,
            float topRightX, float topRightZ,
            float u, float v
    ) {
        // 1.19.2 的 POSITION_COLOR_TEX_LIGHTMAP 格式要求按颜色、UV、光照 UV 的顺序提交属性。
        buffer.vertex(pose.pose(), topLeftX, y, topLeftZ).color((color >> 16) & 255, (color >> 8) & 255, color & 255, (color >>> 24) & 255).uv(u, v)
                .uv2(FULL_BRIGHT_LIGHT, FULL_BRIGHT_LIGHT).endVertex();
        buffer.vertex(pose.pose(), bottomLeftX, y, bottomLeftZ).color((color >> 16) & 255, (color >> 8) & 255, color & 255, (color >>> 24) & 255)
                .uv(u, v + NUMBER_TEXTURE_CELL_SIZE)
                .uv2(FULL_BRIGHT_LIGHT, FULL_BRIGHT_LIGHT).endVertex();
        buffer.vertex(pose.pose(), bottomRightX, y, bottomRightZ).color((color >> 16) & 255, (color >> 8) & 255, color & 255, (color >>> 24) & 255)
                .uv(u + NUMBER_TEXTURE_CELL_SIZE, v + NUMBER_TEXTURE_CELL_SIZE)
                .uv2(FULL_BRIGHT_LIGHT, FULL_BRIGHT_LIGHT).endVertex();
        buffer.vertex(pose.pose(), topRightX, y, topRightZ).color((color >> 16) & 255, (color >> 8) & 255, color & 255, (color >>> 24) & 255)
                .uv(u + NUMBER_TEXTURE_CELL_SIZE, v)
                .uv2(FULL_BRIGHT_LIGHT, FULL_BRIGHT_LIGHT).endVertex();
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
                .color((color >> 16) & 255, (color >> 8) & 255, color & 255, (color >>> 24) & 255).normal(pose.normal(), nx, ny, nz).endVertex();
        buffer.vertex(pose.pose(), (float) x2, (float) y2, (float) z2)
                .color((color >> 16) & 255, (color >> 8) & 255, color & 255, (color >>> 24) & 255).normal(pose.normal(), nx, ny, nz).endVertex();
    }

    private static final class RenderData {
        private final List<LightOverlayState.MarkerColumn> sourceColumns;
        private final LightOverlayState.DisplayMode displayMode;
        private final List<ColumnRenderData> columns;
        private final int lineCount;
        private final int quadCount;
        public RenderData(List<LightOverlayState.MarkerColumn> sourceColumns, LightOverlayState.DisplayMode displayMode, List<ColumnRenderData> columns, int lineCount, int quadCount) {
            this.sourceColumns = sourceColumns;
            this.displayMode = displayMode;
            this.columns = columns;
            this.lineCount = lineCount;
            this.quadCount = quadCount;
        }
        public List<LightOverlayState.MarkerColumn> sourceColumns() { return sourceColumns; }
        public LightOverlayState.DisplayMode displayMode() { return displayMode; }
        public List<ColumnRenderData> columns() { return columns; }
        public int lineCount() { return lineCount; }
        public int quadCount() { return quadCount; }
        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof RenderData)) return false;
            RenderData that = (RenderData) other;
            return java.util.Objects.equals(sourceColumns, that.sourceColumns) && java.util.Objects.equals(displayMode, that.displayMode) && java.util.Objects.equals(columns, that.columns) && java.util.Objects.equals(lineCount, that.lineCount) && java.util.Objects.equals(quadCount, that.quadCount);
        }
        @Override public int hashCode() { return java.util.Objects.hash(sourceColumns, displayMode, columns, lineCount, quadCount); }

        private int renderableCount() {
            return lineCount + quadCount;
        }
    }

    private static final class ColumnRenderData {
        private final List<LightOverlayState.Marker> sourceMarkers;
        private final LightOverlayState.DisplayMode displayMode;
        private final double[] coordinates;
        private final int[] colors;
        private final int lineCount;
        private final List<NumberQuad> numberQuads;
        public ColumnRenderData(List<LightOverlayState.Marker> sourceMarkers, LightOverlayState.DisplayMode displayMode, double[] coordinates, int[] colors, int lineCount, List<NumberQuad> numberQuads) {
            this.sourceMarkers = sourceMarkers;
            this.displayMode = displayMode;
            this.coordinates = coordinates;
            this.colors = colors;
            this.lineCount = lineCount;
            this.numberQuads = numberQuads;
        }
        public List<LightOverlayState.Marker> sourceMarkers() { return sourceMarkers; }
        public LightOverlayState.DisplayMode displayMode() { return displayMode; }
        public double[] coordinates() { return coordinates; }
        public int[] colors() { return colors; }
        public int lineCount() { return lineCount; }
        public List<NumberQuad> numberQuads() { return numberQuads; }
        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof ColumnRenderData)) return false;
            ColumnRenderData that = (ColumnRenderData) other;
            return java.util.Objects.equals(sourceMarkers, that.sourceMarkers) && java.util.Objects.equals(displayMode, that.displayMode) && java.util.Objects.equals(coordinates, that.coordinates) && java.util.Objects.equals(colors, that.colors) && java.util.Objects.equals(lineCount, that.lineCount) && java.util.Objects.equals(numberQuads, that.numberQuads);
        }
        @Override public int hashCode() { return java.util.Objects.hash(sourceMarkers, displayMode, coordinates, colors, lineCount, numberQuads); }

    }

    private static final class NumberQuad {
        private final double x;
        private final double y;
        private final double z;
        private final int value;
        private final int color;
        private final float size;
        private final float directionOffset;
        public NumberQuad(double x, double y, double z, int value, int color, float size, float directionOffset) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.value = value;
            this.color = color;
            this.size = size;
            this.directionOffset = directionOffset;
        }
        public double x() { return x; }
        public double y() { return y; }
        public double z() { return z; }
        public int value() { return value; }
        public int color() { return color; }
        public float size() { return size; }
        public float directionOffset() { return directionOffset; }
        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof NumberQuad)) return false;
            NumberQuad that = (NumberQuad) other;
            return java.util.Objects.equals(x, that.x) && java.util.Objects.equals(y, that.y) && java.util.Objects.equals(z, that.z) && java.util.Objects.equals(value, that.value) && java.util.Objects.equals(color, that.color) && java.util.Objects.equals(size, that.size) && java.util.Objects.equals(directionOffset, that.directionOffset);
        }
        @Override public int hashCode() { return java.util.Objects.hash(x, y, z, value, color, size, directionOffset); }

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

        private void addNumber(
                double x, double y, double z, int value, int color, float size, float directionOffset
        ) {
            numberQuads.add(new NumberQuad(x, y, z, value, color, size, directionOffset));
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
                    com.google.common.collect.ImmutableList.copyOf(numberQuads)
            );
        }
    }

    @FunctionalInterface
    private interface GeometryRenderer {
        void render(PoseStack.Pose pose, VertexConsumer buffer);
    }
}
