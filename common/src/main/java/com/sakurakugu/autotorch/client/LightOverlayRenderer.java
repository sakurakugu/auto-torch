package com.sakurakugu.autotorch.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import com.sakurakugu.autotorch.client.AutoTorchRenderTypes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;

/** 在可生成怪物的地面上，将缓存的光照等级绘制为经过深度测试的交叉标记或纹理数字。 */
public final class LightOverlayRenderer {
    private static final Identifier NUMBER_TEXTURE =
            Identifier.fromNamespaceAndPath("autotorch", "textures/misc/light_level_numbers_large.png");
    private static final Identifier MEDIUM_NUMBER_TEXTURE =
            Identifier.fromNamespaceAndPath("autotorch", "textures/misc/light_level_numbers_medium.png");
    private static final int FULL_BRIGHT_LIGHT = 0xF0;
    private static final int ALWAYS_RISK_COLOR = 0xE0FF3030;
    private static final int NIGHT_RISK_COLOR = 0xE0FFD23C;
    private static final int SAFE_COLOR = 0xE050E060;
    private static final int SWAMP_SLIME_RISK_COLOR = 0xE0E050E0;
    private static final int DROWNED_RISK_COLOR = 0xE040D8E8;
    private static final float CROSS_LINE_WIDTH = 2.5F;
    private static final float LINE_WIDTH_REFERENCE_DISTANCE = 8.0F;
    private static final double LINE_WIDTH_REFERENCE_DISTANCE_SQUARED =
            LINE_WIDTH_REFERENCE_DISTANCE * LINE_WIDTH_REFERENCE_DISTANCE;
    private static final float MIN_LINE_WIDTH = 0.75F;
    private static final double SURFACE_OFFSET = 0.0125D;
    private static final double CROSS_MARGIN = 0.14D;
    // 图集中的字形已在 64x64 单元格内居中。
    private static final double NUMBER_OFFSET_X = 0.0D;
    private static final double NUMBER_OFFSET_Z = 0.25D;
    private static final double BOXED_NUMBER_OFFSET_Z = 0.0D;
    private static final double NUMBER_MARGIN = 0.1D;
    private static final double NUMBER_SIZE = 1.0D;
    private static final float NUMBER_TEXTURE_CELL_SIZE = 0.25F;
    private static final int DROWNED_VISIBILITY_CHECKS_PER_FRAME = 64;
    private static final long DROWNED_VISIBILITY_BUDGET_NANOS = 1_000_000L;
    private static final double DROWNED_VISIBILITY_REFRESH_DISTANCE_SQUARED = 16.0D;
    private static final List<LightOverlayState.MarkerColumn> NO_COLUMNS = List.of();
    private static Map<Long, ColumnRenderData> columnGeometry = Map.of();
    private static volatile RenderData renderData;
    private static volatile DrownedSource drownedSource = new DrownedSource(NO_COLUMNS,
            LightOverlayState.DisplayMode.CROSSES);
    private static final Map<BlockPos, Boolean> drownedVisibility = new HashMap<>();
    private static final ArrayDeque<BlockPos> drownedVisibilityQueue = new ArrayDeque<>();
    private static final Set<BlockPos> queuedDrownedPositions = new HashSet<>();
    private static Set<BlockPos> activeDrownedPositions = Set.of();
    private static List<LightOverlayState.MarkerColumn> visibilitySourceColumns = NO_COLUMNS;
    private static Vec3 drownedVisibilityCamera;
    private static RenderData visibleDrownedRenderData;
    private static boolean drownedVisibilityDirty;
    private static final RenderType SEE_THROUGH_LINES = AutoTorchRenderTypes.seeThroughLines();

    private LightOverlayRenderer() {
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
        List<LightOverlayState.MarkerColumn> drownedColumns = columns.stream()
                .map(column -> new LightOverlayState.MarkerColumn(column.key(), column.minY(),
                        column.markers().stream()
                                .filter(marker -> marker.riskType() == LightOverlayState.RiskType.DROWNED)
                                .toList()))
                .filter(column -> !column.markers().isEmpty()).toList();
        drownedSource = new DrownedSource(drownedColumns, displayMode);
    }

    public static void submit(Vec3 camera, PoseStack poseStack, SubmitNodeCollector collector) {
        RenderData data = renderData;
        if (data == null) {
            return;
        }
        Direction numberDirection = ClientConfig.rotatesLightOverlayNumbers()
                ? Direction.fromYRot(Minecraft.getInstance().gameRenderer.mainCamera().yRot())
                : Direction.NORTH;
        if (data.displayMode() != LightOverlayState.DisplayMode.CROSSES) {
            // 方框数字样式：数字平面置于方框内部，方框单独使用线段渲染以保持清晰边界。
            Identifier numberTexture = data.displayMode() == LightOverlayState.DisplayMode.BOXED_NUMBERS
                    ? MEDIUM_NUMBER_TEXTURE : NUMBER_TEXTURE;
            RenderType numberRenderType = ClientConfig.isLightOverlayRenderThrough()
                    ? RenderTypes.textSeeThrough(numberTexture) : RenderTypes.text(numberTexture);
            renderGeometry(data, camera, poseStack, collector, numberRenderType,
                    (pose, buffer) -> submitNumbers(pose, buffer, data, camera, numberDirection));
            if (data.displayMode() == LightOverlayState.DisplayMode.BOXED_NUMBERS) {
                renderGeometry(data, camera, poseStack, collector,
                        ClientConfig.isLightOverlayRenderThrough() ? SEE_THROUGH_LINES : RenderTypes.linesTranslucent(),
                        (pose, buffer) -> submitLines(pose, buffer, data, camera));
            }
        } else {
            renderGeometry(data, camera, poseStack, collector,
                    ClientConfig.isLightOverlayRenderThrough() ? SEE_THROUGH_LINES : RenderTypes.linesTranslucent(),
                    (pose, buffer) -> submitLines(pose, buffer, data, camera));
        }
        if (!ClientConfig.isLightOverlayRenderThrough()) {
            RenderData drowned = visibleDrownedData(camera, data.displayMode());
            if (drowned != null && drowned.renderableCount() > 0) {
                RenderType type = data.displayMode() == LightOverlayState.DisplayMode.CROSSES
                        ? SEE_THROUGH_LINES : RenderTypes.textSeeThrough(
                                data.displayMode() == LightOverlayState.DisplayMode.BOXED_NUMBERS
                                        ? MEDIUM_NUMBER_TEXTURE : NUMBER_TEXTURE);
                renderGeometry(drowned, camera, poseStack, collector, type,
                        (pose, buffer) -> {
                            if (data.displayMode() == LightOverlayState.DisplayMode.CROSSES) {
                                submitLines(pose, buffer, drowned, camera);
                            } else {
                                submitNumbers(pose, buffer, drowned, camera, numberDirection);
                            }
                        });
                if (data.displayMode() == LightOverlayState.DisplayMode.BOXED_NUMBERS) {
                    renderGeometry(drowned, camera, poseStack, collector, SEE_THROUGH_LINES,
                            (pose, buffer) -> submitLines(pose, buffer, drowned, camera));
                }
            }
        }
    }

    private static RenderData visibleDrownedData(Vec3 camera, LightOverlayState.DisplayMode displayMode) {
        DrownedSource source = drownedSource;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            clearDrownedVisibility();
            return null;
        }

        syncDrownedVisibilitySource(source.columns());
        if (drownedVisibilityCamera == null
                || camera.distanceToSqr(drownedVisibilityCamera) >= DROWNED_VISIBILITY_REFRESH_DISTANCE_SQUARED) {
            drownedVisibilityCamera = camera;
            // 保留尚未完成的检查，避免自由相机连续移动时反复丢弃队列进度。
            for (BlockPos pos : activeDrownedPositions) {
                enqueueDrownedVisibility(pos);
            }
            drownedVisibilityDirty = true;
        }

        int checked = 0;
        long deadline = System.nanoTime() + DROWNED_VISIBILITY_BUDGET_NANOS;
        while (checked < DROWNED_VISIBILITY_CHECKS_PER_FRAME && !drownedVisibilityQueue.isEmpty()) {
            BlockPos pos = drownedVisibilityQueue.removeFirst();
            queuedDrownedPositions.remove(pos);
            if (!activeDrownedPositions.contains(pos)) {
                continue;
            }
            boolean visible = minecraft.level.clip(new ClipContext(camera, Vec3.atCenterOf(pos),
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, minecraft.player)).getType()
                    == HitResult.Type.MISS;
            if (!Objects.equals(drownedVisibility.put(pos, visible), visible)) {
                drownedVisibilityDirty = true;
            }
            checked++;
            // 26.2 的方块碰撞射线明显比旧版本昂贵，避免候选点密集时独占渲染线程。
            if (System.nanoTime() >= deadline) {
                break;
            }
        }

        if (drownedVisibilityQueue.isEmpty()
                && (drownedVisibilityDirty || visibleDrownedRenderData == null
                || visibleDrownedRenderData.displayMode() != displayMode)) {
            List<LightOverlayState.MarkerColumn> visibleColumns = source.columns().stream()
                    .map(column -> new LightOverlayState.MarkerColumn(column.key(), column.minY(),
                            column.markers().stream()
                                    .filter(marker -> Boolean.TRUE.equals(drownedVisibility.get(marker.pos())))
                                    .toList()))
                    .filter(column -> !column.markers().isEmpty()).toList();
            // 溺尸临时几何不能写入主覆盖层缓存，否则下一次刷新会重建所有列。
            visibleDrownedRenderData = buildRenderData(visibleColumns, displayMode, false);
            drownedVisibilityDirty = false;
        }
        return visibleDrownedRenderData;
    }

    private static void syncDrownedVisibilitySource(List<LightOverlayState.MarkerColumn> columns) {
        if (visibilitySourceColumns == columns) {
            return;
        }
        visibilitySourceColumns = columns;
        Set<BlockPos> active = new HashSet<>();
        for (LightOverlayState.MarkerColumn column : columns) {
            for (LightOverlayState.Marker marker : column.markers()) {
                active.add(marker.pos());
            }
        }
        activeDrownedPositions = Set.copyOf(active);
        drownedVisibility.keySet().retainAll(active);
        drownedVisibilityQueue.removeIf(pos -> !active.contains(pos));
        queuedDrownedPositions.retainAll(active);
        for (BlockPos pos : active) {
            if (!drownedVisibility.containsKey(pos)) {
                enqueueDrownedVisibility(pos);
            }
        }
        drownedVisibilityDirty = true;
    }

    private static void enqueueDrownedVisibility(BlockPos pos) {
        if (queuedDrownedPositions.add(pos)) {
            drownedVisibilityQueue.addLast(pos);
        }
    }

    private static void clearDrownedVisibility() {
        drownedVisibility.clear();
        drownedVisibilityQueue.clear();
        queuedDrownedPositions.clear();
        activeDrownedPositions = Set.of();
        visibilitySourceColumns = NO_COLUMNS;
        drownedVisibilityCamera = null;
        visibleDrownedRenderData = null;
        drownedVisibilityDirty = false;
    }

    private static void renderGeometry(
            RenderData data, Vec3 camera, PoseStack poseStack, SubmitNodeCollector collector,
            RenderType renderType, GeometryRenderer renderer
    ) {
        if (data == null || data.renderableCount() == 0 || Minecraft.getInstance().level == null) {
            return;
        }

        poseStack.pushPose();
        // 顶点在提交时先转换为相机相对坐标，避免大世界坐标分别转 float 后再相减造成精度损失。
        collector.submitCustomGeometry(poseStack, renderType, renderer::render);
        poseStack.popPose();
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
        Map<Long, ColumnRenderData> previousGeometry = cacheGeometry ? columnGeometry : Map.of();
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
            double y = marker.pos().getY() + surfaceOffset(marker);
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
                marker.pos().getY() + surfaceOffset(marker),
                marker.pos().getZ() + BOXED_NUMBER_OFFSET_Z,
                marker.blockLight(), markerColor(marker), boxed ? (float) NUMBER_SIZE : 1.0F,
                boxed ? 0.0F : (float) NUMBER_OFFSET_Z);
    }

    private static void addNumberBox(GeometryBuilder geometry, LightOverlayState.Marker marker) {
        double x0 = marker.pos().getX() + NUMBER_MARGIN;
        double x1 = marker.pos().getX() + 1.0D - NUMBER_MARGIN;
        double y = marker.pos().getY() + surfaceOffset(marker);
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

    private static double surfaceOffset(LightOverlayState.Marker marker) {
        return SURFACE_OFFSET;
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
                line(pose, buffer,
                        x1, y1, z1, x2, y2, z2,
                        colors[line], scaledLineWidth(CROSS_LINE_WIDTH, x1, y1, z1, x2, y2, z2));
            }
        }
    }

    private static float scaledLineWidth(float baseWidth,
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
        return Math.max(MIN_LINE_WIDTH,
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
                    case SOUTH -> z -= offset;
                    case EAST -> x -= offset;
                    case WEST -> x += offset;
                    default -> z += offset;
                }
                float size = quad.size();
                float u = (quad.value() & 3) * NUMBER_TEXTURE_CELL_SIZE;
                float v = (quad.value() >> 2) * NUMBER_TEXTURE_CELL_SIZE;
                switch (direction) {
                    case SOUTH -> submitNumberQuad(pose, buffer, quad.color(), x + size, y, z + size,
                            x + size, z, x, z, x, z + size, u, v);
                    case EAST -> submitNumberQuad(pose, buffer, quad.color(), x + size, y, z,
                            x, z, x, z + size, x + size, z + size, u, v);
                    case WEST -> submitNumberQuad(pose, buffer, quad.color(), x, y, z + size,
                            x + size, z + size, x + size, z, x, z, u, v);
                    default -> submitNumberQuad(pose, buffer, quad.color(), x, y, z,
                            x, z + size, x + size, z + size, x + size, z, u, v);
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
        buffer.addVertex(pose, topLeftX, y, topLeftZ)
                .setUv(u, v).setUv2(FULL_BRIGHT_LIGHT, FULL_BRIGHT_LIGHT).setColor(color);
        buffer.addVertex(pose, bottomLeftX, y, bottomLeftZ)
                .setUv(u, v + NUMBER_TEXTURE_CELL_SIZE)
                .setUv2(FULL_BRIGHT_LIGHT, FULL_BRIGHT_LIGHT).setColor(color);
        buffer.addVertex(pose, bottomRightX, y, bottomRightZ)
                .setUv(u + NUMBER_TEXTURE_CELL_SIZE, v + NUMBER_TEXTURE_CELL_SIZE)
                .setUv2(FULL_BRIGHT_LIGHT, FULL_BRIGHT_LIGHT).setColor(color);
        buffer.addVertex(pose, topRightX, y, topRightZ)
                .setUv(u + NUMBER_TEXTURE_CELL_SIZE, v)
                .setUv2(FULL_BRIGHT_LIGHT, FULL_BRIGHT_LIGHT).setColor(color);
    }

    private static void line(
            PoseStack.Pose pose, VertexConsumer buffer,
            double x1, double y1, double z1, double x2, double y2, double z2,
            int color, float lineWidth
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
            List<LightOverlayState.MarkerColumn> sourceColumns, LightOverlayState.DisplayMode displayMode,
            List<ColumnRenderData> columns, int lineCount, int quadCount
    ) {
        private int renderableCount() {
            return lineCount + quadCount;
        }
    }

    private record DrownedSource(
            List<LightOverlayState.MarkerColumn> columns, LightOverlayState.DisplayMode displayMode
    ) {
    }

    private record ColumnRenderData(
            List<LightOverlayState.Marker> sourceMarkers, LightOverlayState.DisplayMode displayMode,
            double[] coordinates, int[] colors, int lineCount, List<NumberQuad> numberQuads
    ) {
    }

    private record NumberQuad(
            double x, double y, double z, int value, int color, float size, float directionOffset
    ) {
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
                    List.copyOf(numberQuads)
            );
        }
    }

    @FunctionalInterface
    private interface GeometryRenderer {
        void render(PoseStack.Pose pose, VertexConsumer buffer);
    }
}
