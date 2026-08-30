package com.sakurakugu.autotorch.client;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import com.sakurakugu.autotorch.network.AreaShape;
import com.sakurakugu.autotorch.network.AreaZone;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** 在世界中持续绘制选区草稿、照明范围和所有排除范围。 */
public final class SelectionRenderer {
    private static final int DRAFT_LINE_COLOR = 0xD070A0FF;
    private static final int SELECTION_LINE_COLOR = 0xD050FF70;
    private static final int EXCLUSION_LINE_COLOR = 0xD0FF5050;
    private static final int DRAFT_FACE_COLOR = 0x2870A0FF;
    private static final int SELECTION_FACE_COLOR = 0x2850FF70;
    private static final int EXCLUSION_FACE_COLOR = 0x30FF5050;
    /** 方框面向外偏移，避免与方块面共面导致深度冲突。 */
    private static final double FACE_OFFSET = 1.0D / 1024.0D;
    private static final float LINE_WIDTH_REFERENCE_DISTANCE = 8.0F;
    private static final double LINE_WIDTH_REFERENCE_DISTANCE_SQUARED =
            LINE_WIDTH_REFERENCE_DISTANCE * LINE_WIDTH_REFERENCE_DISTANCE;
    private static final float MIN_LINE_WIDTH = 0.75F;
    private static final int SPHERE_LONGITUDE_SEGMENTS = 24;
    /** 平滑球体线框中相邻圆环的目标间距（方块）。 */
    private static final double SMOOTH_SPHERE_LINE_SPACING = 8.0D;
    private static final int MAX_SMOOTH_SPHERE_RINGS = 41;
    private static final int SPHERE_LATITUDE_SEGMENTS = 12;
    private static final double[] SPHERE_LONGITUDE_COS = new double[SPHERE_LONGITUDE_SEGMENTS + 1];
    private static final double[] SPHERE_LONGITUDE_SIN = new double[SPHERE_LONGITUDE_SEGMENTS + 1];
    private static final double[] SPHERE_LATITUDE_COS = new double[SPHERE_LATITUDE_SEGMENTS + 1];
    private static final double[] SPHERE_LATITUDE_SIN = new double[SPHERE_LATITUDE_SEGMENTS + 1];
    private static final int BLOCK_OFFSET_BITS = 9;
    private static final int BLOCK_OFFSET_MASK = (1 << BLOCK_OFFSET_BITS) - 1;
    private static final int BLOCK_OFFSET_BIAS = AreaZone.MAX_SPHERE_RADIUS;
    private static final int BLOCK_FACE_DIRECTION_SHIFT = BLOCK_OFFSET_BITS * 3;
    private static final int BLOCK_EDGE_AXIS_SHIFT = BLOCK_OFFSET_BITS * 3;
    private static final long MAX_SPHERE_RADIUS_SQUARED =
            (long) AreaZone.MAX_SPHERE_RADIUS * AreaZone.MAX_SPHERE_RADIUS;
    private static final int MAX_CACHED_BLOCKY_SPHERES = 4;
    private static final Map<Long, BlockySphereMesh> BLOCKY_SPHERE_CACHE = new LinkedHashMap<>(8, 0.75F, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Long, BlockySphereMesh> eldest) {
            return size() > MAX_CACHED_BLOCKY_SPHERES;
        }
    };
    private static volatile RenderData renderData;
    private static long renderRevision = Long.MIN_VALUE;

    static {
        for (int longitude = 0; longitude <= SPHERE_LONGITUDE_SEGMENTS; longitude++) {
            double angle = Math.PI * 2.0 * longitude / SPHERE_LONGITUDE_SEGMENTS;
            SPHERE_LONGITUDE_COS[longitude] = Math.cos(angle);
            SPHERE_LONGITUDE_SIN[longitude] = Math.sin(angle);
        }
        for (int latitude = 0; latitude <= SPHERE_LATITUDE_SEGMENTS; latitude++) {
            double angle = -Math.PI / 2.0 + Math.PI * latitude / SPHERE_LATITUDE_SEGMENTS;
            SPHERE_LATITUDE_COS[latitude] = Math.cos(angle);
            SPHERE_LATITUDE_SIN[latitude] = Math.sin(angle);
        }
    }

    private SelectionRenderer() {
    }

    public static void extract(BlockPos fallback) {
        long revision = SelectionState.renderRevision();
        if (renderData != null && renderRevision == revision) {
            return;
        }
        if (!SelectionState.isOverlayEnabled()) {
            renderData = new RenderData(
                    null, null, List.of(), SelectionState.displayMode(), SelectionState.sphereDisplayMode(), Map.of()
            );
            renderRevision = SelectionState.renderRevision();
            return;
        }
        AreaZone draft = SelectionState.drafting() ? SelectionState.draft(fallback) : null;
        AreaZone lightingZone = SelectionState.lightingZone();
        List<AreaZone> exclusions = SelectionState.exclusions();
        SelectionState.SphereDisplayMode sphereDisplayMode = SelectionState.sphereDisplayMode();
        renderData = new RenderData(
                draft,
                lightingZone,
                exclusions,
                SelectionState.displayMode(),
                sphereDisplayMode,
                prepareBlockySphereMeshes(draft, lightingZone, exclusions, sphereDisplayMode)
        );
        renderRevision = SelectionState.renderRevision();
    }

    public static void render(Vec3 camera, PoseStack poseStack, MultiBufferSource buffers) {
        renderGeometry(camera, poseStack, (stack, renderType, renderer) ->
                renderer.render(stack.last(), buffers.getBuffer(renderType)));
    }

    private static void renderGeometry(Vec3 camera, PoseStack poseStack, GeometrySink sink) {
        RenderData data = renderData;
        if (data == null || data.draft() == null && data.lightingZone() == null && data.exclusions().isEmpty()) {
            return;
        }
        poseStack.pushPose();
        // 顶点在提交时转换为相机相对坐标，避免大世界坐标分别转 float 后再相减造成精度损失。
        RenderType renderType = data.displayMode() == SelectionState.DisplayMode.LINES
                ? RenderType.lines() : RenderType.debugStructureQuads();
        sink.submit(poseStack, renderType, (pose, buffer) -> renderZones(pose, buffer, data, camera));
        poseStack.popPose();
    }

    private static void renderZones(PoseStack.Pose pose, VertexConsumer buffer, RenderData data, Vec3 camera) {
        if (data.draft() != null) {
            renderZone(pose, buffer, data, data.draft(), DRAFT_LINE_COLOR, DRAFT_FACE_COLOR, 3.0F, camera);
        }
        if (data.lightingZone() != null) {
            renderZone(pose, buffer, data, data.lightingZone(),
                    SELECTION_LINE_COLOR, SELECTION_FACE_COLOR, 3.0F, camera);
        }
        for (AreaZone exclusion : data.exclusions()) {
            renderZone(pose, buffer, data, exclusion, EXCLUSION_LINE_COLOR, EXCLUSION_FACE_COLOR, 2.0F, camera);
        }
    }

    private static void renderZone(
            PoseStack.Pose pose,
            VertexConsumer buffer,
            RenderData data,
            AreaZone zone,
            int lineColor,
            int faceColor,
            float width,
            Vec3 camera
    ) {
        if (zone.shape() == AreaShape.SPHERE && zone.radiusSquared() > MAX_SPHERE_RADIUS_SQUARED) {
            return;
        }
        if (data.displayMode() == SelectionState.DisplayMode.LINES) {
            if (zone.shape() == AreaShape.SPHERE) {
                if (data.sphereDisplayMode() == SelectionState.SphereDisplayMode.BLOCKY) {
                    renderBlockySphereLines(pose, buffer, zone,
                            data.blockySphereMeshes().get(zone.radiusSquared()), lineColor, width, camera);
                } else {
                    renderSphereLines(pose, buffer, zone, lineColor, width, camera);
                }
            } else {
                renderBoxLines(pose, buffer,
                        AABB.encapsulatingFullBlocks(zone.min(), zone.max()), lineColor, width, camera);
            }
        } else if (zone.shape() == AreaShape.SPHERE) {
            if (data.sphereDisplayMode() == SelectionState.SphereDisplayMode.BLOCKY) {
                renderBlockySphereFaces(pose, buffer, zone,
                        data.blockySphereMeshes().get(zone.radiusSquared()), faceColor, camera);
            } else {
                renderSphereFaces(pose, buffer, zone, faceColor, camera);
            }
        } else {
            renderBoxFaces(pose, buffer,
                    AABB.encapsulatingFullBlocks(zone.min(), zone.max()).inflate(FACE_OFFSET), faceColor, camera);
        }
    }

    private static void renderBoxLines(PoseStack.Pose pose, VertexConsumer buffer, AABB box, int color, float width,
                                       Vec3 camera) {
        line(pose, buffer, box.minX, box.minY, box.minZ, box.maxX, box.minY, box.minZ, color, width, camera);
        line(pose, buffer, box.maxX, box.minY, box.minZ, box.maxX, box.minY, box.maxZ, color, width, camera);
        line(pose, buffer, box.maxX, box.minY, box.maxZ, box.minX, box.minY, box.maxZ, color, width, camera);
        line(pose, buffer, box.minX, box.minY, box.maxZ, box.minX, box.minY, box.minZ, color, width, camera);
        line(pose, buffer, box.minX, box.maxY, box.minZ, box.maxX, box.maxY, box.minZ, color, width, camera);
        line(pose, buffer, box.maxX, box.maxY, box.minZ, box.maxX, box.maxY, box.maxZ, color, width, camera);
        line(pose, buffer, box.maxX, box.maxY, box.maxZ, box.minX, box.maxY, box.maxZ, color, width, camera);
        line(pose, buffer, box.minX, box.maxY, box.maxZ, box.minX, box.maxY, box.minZ, color, width, camera);
        line(pose, buffer, box.minX, box.minY, box.minZ, box.minX, box.maxY, box.minZ, color, width, camera);
        line(pose, buffer, box.maxX, box.minY, box.minZ, box.maxX, box.maxY, box.minZ, color, width, camera);
        line(pose, buffer, box.maxX, box.minY, box.maxZ, box.maxX, box.maxY, box.maxZ, color, width, camera);
        line(pose, buffer, box.minX, box.minY, box.maxZ, box.minX, box.maxY, box.maxZ, color, width, camera);
    }

    private static void renderBoxFaces(PoseStack.Pose pose, VertexConsumer buffer, AABB box, int color, Vec3 camera) {
        quad(pose, buffer, box.minX, box.minY, box.minZ, box.maxX, box.minY, box.minZ,
                box.maxX, box.minY, box.maxZ, box.minX, box.minY, box.maxZ, color, camera);
        quad(pose, buffer, box.minX, box.maxY, box.maxZ, box.maxX, box.maxY, box.maxZ,
                box.maxX, box.maxY, box.minZ, box.minX, box.maxY, box.minZ, color, camera);
        quad(pose, buffer, box.minX, box.minY, box.maxZ, box.maxX, box.minY, box.maxZ,
                box.maxX, box.maxY, box.maxZ, box.minX, box.maxY, box.maxZ, color, camera);
        quad(pose, buffer, box.maxX, box.minY, box.minZ, box.minX, box.minY, box.minZ,
                box.minX, box.maxY, box.minZ, box.maxX, box.maxY, box.minZ, color, camera);
        quad(pose, buffer, box.minX, box.minY, box.minZ, box.minX, box.minY, box.maxZ,
                box.minX, box.maxY, box.maxZ, box.minX, box.maxY, box.minZ, color, camera);
        quad(pose, buffer, box.maxX, box.minY, box.maxZ, box.maxX, box.minY, box.minZ,
                box.maxX, box.maxY, box.minZ, box.maxX, box.maxY, box.maxZ, color, camera);
    }

    private static void renderSphereLines(
            PoseStack.Pose pose, VertexConsumer buffer, AreaZone zone, int color, float width, Vec3 camera
    ) {
        double cx = zone.first().getX() + 0.5;
        double cy = zone.first().getY() + 0.5;
        double cz = zone.first().getZ() + 0.5;
        double radius = Math.sqrt(zone.radiusSquared()) + 0.5;
        int rings = smoothSphereRingCount(radius);
        double ringStep = radius / (rings / 2 + 1.0D);
        int middle = rings / 2;
        for (int plane = 0; plane < 3; plane++) {
            for (int ring = 0; ring < rings; ring++) {
                double offset = (ring - middle) * ringStep;
                double circleRadius = Math.sqrt(Math.max(0.0D, radius * radius - offset * offset));
                for (int segment = 0; segment < SPHERE_LONGITUDE_SEGMENTS; segment++) {
                    double a1 = SPHERE_LONGITUDE_COS[segment] * circleRadius;
                    double b1 = SPHERE_LONGITUDE_SIN[segment] * circleRadius;
                    double a2 = SPHERE_LONGITUDE_COS[segment + 1] * circleRadius;
                    double b2 = SPHERE_LONGITUDE_SIN[segment + 1] * circleRadius;
                    if (plane == 0) {
                        line(pose, buffer, cx + a1, cy + b1, cz + offset,
                                cx + a2, cy + b2, cz + offset, color, width, camera);
                    } else if (plane == 1) {
                        line(pose, buffer, cx + a1, cy + offset, cz + b1,
                                cx + a2, cy + offset, cz + b2, color, width, camera);
                    } else {
                        line(pose, buffer, cx + offset, cy + a1, cz + b1,
                                cx + offset, cy + a2, cz + b2, color, width, camera);
                    }
                }
            }
        }
    }

    private static int smoothSphereRingCount(double radius) {
        int additionalRings = (int) (radius / SMOOTH_SPHERE_LINE_SPACING);
        return Math.min(MAX_SMOOTH_SPHERE_RINGS, additionalRings * 2 + 1);
    }

    private static void renderSphereFaces(PoseStack.Pose pose, VertexConsumer buffer, AreaZone zone, int color,
                                          Vec3 camera) {
        double cx = zone.first().getX() + 0.5;
        double cy = zone.first().getY() + 0.5;
        double cz = zone.first().getZ() + 0.5;
        double radius = Math.sqrt(zone.radiusSquared()) + 0.5;
        for (int latitude = 0; latitude < SPHERE_LATITUDE_SEGMENTS; latitude++) {
            for (int longitude = 0; longitude < SPHERE_LONGITUDE_SEGMENTS; longitude++) {
                sphereQuad(pose, buffer, cx, cy, cz, radius, latitude, longitude, color, camera);
            }
        }
    }

    private static void renderBlockySphereFaces(
            PoseStack.Pose pose, VertexConsumer buffer, AreaZone zone, BlockySphereMesh mesh, int color, Vec3 camera
    ) {
        BlockPos center = zone.first();
        for (int index = 0; index < mesh.faceStrips().length; index += 2) {
            blockFaceStrip(pose, buffer, center, mesh.faceStrips()[index], mesh.faceStrips()[index + 1], color, camera);
        }
    }

    private static void renderBlockySphereLines(
            PoseStack.Pose pose, VertexConsumer buffer, AreaZone zone, BlockySphereMesh mesh, int color, float width,
            Vec3 camera
    ) {
        BlockPos center = zone.first();
        for (int edge : mesh.edges()) {
            blockEdge(pose, buffer, center, edge, color, width, camera);
        }
    }

    private static Map<Long, BlockySphereMesh> prepareBlockySphereMeshes(
            AreaZone draft, AreaZone lightingZone, List<AreaZone> exclusions,
            SelectionState.SphereDisplayMode sphereDisplayMode
    ) {
        if (sphereDisplayMode != SelectionState.SphereDisplayMode.BLOCKY) {
            return Map.of();
        }
        Map<Long, BlockySphereMesh> meshes = new HashMap<>();
        addBlockySphereMesh(meshes, draft);
        addBlockySphereMesh(meshes, lightingZone);
        for (AreaZone exclusion : exclusions) {
            addBlockySphereMesh(meshes, exclusion);
        }
        return Map.copyOf(meshes);
    }

    private static void addBlockySphereMesh(Map<Long, BlockySphereMesh> meshes, AreaZone zone) {
        if (zone != null && zone.shape() == AreaShape.SPHERE && zone.radiusSquared() <= MAX_SPHERE_RADIUS_SQUARED) {
            meshes.computeIfAbsent(zone.radiusSquared(), SelectionRenderer::blockySphereMesh);
        }
    }

    private static BlockySphereMesh blockySphereMesh(long radiusSquared) {
        synchronized (BLOCKY_SPHERE_CACHE) {
            return BLOCKY_SPHERE_CACHE.computeIfAbsent(radiusSquared, SelectionRenderer::buildBlockySphereMesh);
        }
    }

    private static BlockySphereMesh buildBlockySphereMesh(long radiusSquared) {
        int radius = (int) Math.sqrt(radiusSquared);
        IntOpenHashSet edges = new IntOpenHashSet(Math.max(16, radius * radius * 12));
        IntArrayBuilder faceStrips = new IntArrayBuilder(Math.max(16, radius * radius * 12));
        for (int first = -radius; first <= radius; first++) {
            long firstSquared = (long) first * first;
            int runStart = 0;
            int previousEdge = -1;
            for (int second = -radius; second <= radius + 1; second++) {
                long remaining = radiusSquared - firstSquared - (long) second * second;
                int edge = remaining >= 0 && second <= radius ? (int) Math.sqrt(remaining) : -1;
                if (previousEdge >= 0 && edge != previousEdge) {
                    addBlockFaceStrips(faceStrips, first, runStart, previousEdge, second - runStart);
                }
                if (edge < 0) {
                    previousEdge = -1;
                    continue;
                }
                if (edge != previousEdge) {
                    runStart = second;
                    previousEdge = edge;
                }
                addBlockFaceEdges(edges, edge, first, second, 0);
                addBlockFaceEdges(edges, -edge, first, second, 1);
                addBlockFaceEdges(edges, first, edge, second, 2);
                addBlockFaceEdges(edges, first, -edge, second, 3);
                addBlockFaceEdges(edges, first, second, edge, 4);
                addBlockFaceEdges(edges, first, second, -edge, 5);
            }
        }
        return new BlockySphereMesh(edges.toIntArray(), faceStrips.toArray());
    }

    private static void addBlockFaceEdges(IntOpenHashSet edges, int x, int y, int z, int direction) {
        switch (direction) {
            case 0 -> addXFaceEdges(edges, x + 1, y, z);
            case 1 -> addXFaceEdges(edges, x, y, z);
            case 2 -> addYFaceEdges(edges, x, y + 1, z);
            case 3 -> addYFaceEdges(edges, x, y, z);
            case 4 -> addZFaceEdges(edges, x, y, z + 1);
            default -> addZFaceEdges(edges, x, y, z);
        }
    }

    private static void addXFaceEdges(IntOpenHashSet edges, int x, int y, int z) {
        edges.add(encodeBlockEdge(x, y, z, 1));
        edges.add(encodeBlockEdge(x, y, z + 1, 1));
        edges.add(encodeBlockEdge(x, y, z, 2));
        edges.add(encodeBlockEdge(x, y + 1, z, 2));
    }

    private static void addYFaceEdges(IntOpenHashSet edges, int x, int y, int z) {
        edges.add(encodeBlockEdge(x, y, z, 0));
        edges.add(encodeBlockEdge(x, y, z + 1, 0));
        edges.add(encodeBlockEdge(x, y, z, 2));
        edges.add(encodeBlockEdge(x + 1, y, z, 2));
    }

    private static void addZFaceEdges(IntOpenHashSet edges, int x, int y, int z) {
        edges.add(encodeBlockEdge(x, y, z, 0));
        edges.add(encodeBlockEdge(x, y + 1, z, 0));
        edges.add(encodeBlockEdge(x, y, z, 1));
        edges.add(encodeBlockEdge(x + 1, y, z, 1));
    }

    private static void addBlockFaceStrips(
            IntArrayBuilder strips, int first, int secondStart, int edge, int length
    ) {
        strips.add(encodeBlockFace(edge, first, secondStart, 0));
        strips.add(length);
        strips.add(encodeBlockFace(-edge, first, secondStart, 1));
        strips.add(length);
        strips.add(encodeBlockFace(first, edge, secondStart, 2));
        strips.add(length);
        strips.add(encodeBlockFace(first, -edge, secondStart, 3));
        strips.add(length);
        strips.add(encodeBlockFace(first, secondStart, edge, 4));
        strips.add(length);
        strips.add(encodeBlockFace(first, secondStart, -edge, 5));
        strips.add(length);
    }

    private static int encodeBlockFace(int x, int y, int z, int direction) {
        return x + BLOCK_OFFSET_BIAS
                | (y + BLOCK_OFFSET_BIAS) << BLOCK_OFFSET_BITS
                | (z + BLOCK_OFFSET_BIAS) << (BLOCK_OFFSET_BITS * 2)
                | direction << BLOCK_FACE_DIRECTION_SHIFT;
    }

    private static int encodeBlockEdge(int x, int y, int z, int axis) {
        return x + BLOCK_OFFSET_BIAS
                | (y + BLOCK_OFFSET_BIAS) << BLOCK_OFFSET_BITS
                | (z + BLOCK_OFFSET_BIAS) << (BLOCK_OFFSET_BITS * 2)
                | axis << BLOCK_EDGE_AXIS_SHIFT;
    }

    private static void blockEdge(
            PoseStack.Pose pose, VertexConsumer buffer, BlockPos center, int encodedEdge, int color, float width,
            Vec3 camera
    ) {
        int x = center.getX() + (encodedEdge & BLOCK_OFFSET_MASK) - BLOCK_OFFSET_BIAS;
        int y = center.getY() + ((encodedEdge >> BLOCK_OFFSET_BITS) & BLOCK_OFFSET_MASK) - BLOCK_OFFSET_BIAS;
        int z = center.getZ() + ((encodedEdge >> (BLOCK_OFFSET_BITS * 2)) & BLOCK_OFFSET_MASK) - BLOCK_OFFSET_BIAS;
        int axis = encodedEdge >>> BLOCK_EDGE_AXIS_SHIFT;
        line(pose, buffer, x, y, z,
                x + (axis == 0 ? 1 : 0), y + (axis == 1 ? 1 : 0), z + (axis == 2 ? 1 : 0),
                color, width, camera);
    }

    private static void blockFaceStrip(
            PoseStack.Pose pose, VertexConsumer buffer, BlockPos center, int encodedFace, int length, int color,
            Vec3 camera
    ) {
        int x = center.getX() + (encodedFace & BLOCK_OFFSET_MASK) - BLOCK_OFFSET_BIAS;
        int y = center.getY() + ((encodedFace >> BLOCK_OFFSET_BITS) & BLOCK_OFFSET_MASK) - BLOCK_OFFSET_BIAS;
        int z = center.getZ() + ((encodedFace >> (BLOCK_OFFSET_BITS * 2)) & BLOCK_OFFSET_MASK) - BLOCK_OFFSET_BIAS;
        int direction = encodedFace >>> BLOCK_FACE_DIRECTION_SHIFT;
        switch (direction) {
            case 0 -> quad(pose, buffer,
                    x + 1, y, z, x + 1, y + 1, z, x + 1, y + 1, z + length, x + 1, y, z + length, color, camera);
            case 1 -> quad(pose, buffer,
                    x, y, z + length, x, y + 1, z + length, x, y + 1, z, x, y, z, color, camera);
            case 2 -> quad(pose, buffer,
                    x, y + 1, z + length, x + 1, y + 1, z + length, x + 1, y + 1, z, x, y + 1, z, color, camera);
            case 3 -> quad(pose, buffer,
                    x, y, z, x + 1, y, z, x + 1, y, z + length, x, y, z + length, color, camera);
            case 4 -> quad(pose, buffer,
                    x + 1, y, z + 1, x + 1, y + length, z + 1, x, y + length, z + 1, x, y, z + 1, color, camera);
            default -> quad(pose, buffer,
                    x, y, z, x, y + length, z, x + 1, y + length, z, x + 1, y, z, color, camera);
        }
    }

    private static void sphereQuad(
            PoseStack.Pose pose, VertexConsumer buffer, double cx, double cy, double cz, double radius,
            int latitude, int longitude, int color, Vec3 camera
    ) {
        sphereVertex(pose, buffer, cx, cy, cz, radius, latitude, longitude, color, camera);
        sphereVertex(pose, buffer, cx, cy, cz, radius, latitude, longitude + 1, color, camera);
        sphereVertex(pose, buffer, cx, cy, cz, radius, latitude + 1, longitude + 1, color, camera);
        sphereVertex(pose, buffer, cx, cy, cz, radius, latitude + 1, longitude, color, camera);
    }

    private static void sphereVertex(
            PoseStack.Pose pose, VertexConsumer buffer, double cx, double cy, double cz, double radius,
            int latitude, int longitude, int color, Vec3 camera
    ) {
        double horizontal = SPHERE_LATITUDE_COS[latitude] * radius;
        buffer.addVertex(pose,
                (float) (cx + SPHERE_LONGITUDE_COS[longitude] * horizontal - camera.x()),
                (float) (cy + SPHERE_LATITUDE_SIN[latitude] * radius - camera.y()),
                (float) (cz + SPHERE_LONGITUDE_SIN[longitude] * horizontal - camera.z())).setColor(color);
    }

    private static void quad(
            PoseStack.Pose pose, VertexConsumer buffer,
            double x1, double y1, double z1, double x2, double y2, double z2,
            double x3, double y3, double z3, double x4, double y4, double z4, int color, Vec3 camera
    ) {
        // 使用相机相对坐标，避免远离世界原点时的精度损失。
        buffer.addVertex(pose, (float) (x1 - camera.x()), (float) (y1 - camera.y()), (float) (z1 - camera.z())).setColor(color);
        buffer.addVertex(pose, (float) (x2 - camera.x()), (float) (y2 - camera.y()), (float) (z2 - camera.z())).setColor(color);
        buffer.addVertex(pose, (float) (x3 - camera.x()), (float) (y3 - camera.y()), (float) (z3 - camera.z())).setColor(color);
        buffer.addVertex(pose, (float) (x4 - camera.x()), (float) (y4 - camera.y()), (float) (z4 - camera.z())).setColor(color);
        buffer.addVertex(pose, (float) (x4 - camera.x()), (float) (y4 - camera.y()), (float) (z4 - camera.z())).setColor(color);
        buffer.addVertex(pose, (float) (x3 - camera.x()), (float) (y3 - camera.y()), (float) (z3 - camera.z())).setColor(color);
        buffer.addVertex(pose, (float) (x2 - camera.x()), (float) (y2 - camera.y()), (float) (z2 - camera.z())).setColor(color);
        buffer.addVertex(pose, (float) (x1 - camera.x()), (float) (y1 - camera.y()), (float) (z1 - camera.z())).setColor(color);
    }

    private static void line(
            PoseStack.Pose pose, VertexConsumer buffer,
            double x1, double y1, double z1, double x2, double y2, double z2,
            int color, float width, Vec3 camera
    ) {
        // 先转换为相机相对坐标，再转为 float，避免远离世界原点时的精度损失。
        x1 -= camera.x();
        y1 -= camera.y();
        z1 -= camera.z();
        x2 -= camera.x();
        y2 -= camera.y();
        z2 -= camera.z();
        float nx = (float) (x2 - x1);
        float ny = (float) (y2 - y1);
        float nz = (float) (z2 - z1);
        if (ny == 0.0F && nz == 0.0F && nx != 0.0F) {
            nx = Math.copySign(1.0F, nx);
        } else if (nx == 0.0F && nz == 0.0F && ny != 0.0F) {
            ny = Math.copySign(1.0F, ny);
        } else if (nx == 0.0F && ny == 0.0F && nz != 0.0F) {
            nz = Math.copySign(1.0F, nz);
        } else {
            float length = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
            nx /= length;
            ny /= length;
            nz /= length;
        }
        float lineWidth = scaledLineWidth(width, x1, y1, z1, x2, y2, z2);
        buffer.addVertex(pose, (float) x1, (float) y1, (float) z1)
                .setColor(color).setNormal(pose, nx, ny, nz);
        buffer.addVertex(pose, (float) x2, (float) y2, (float) z2)
                .setColor(color).setNormal(pose, nx, ny, nz);
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

    private record RenderData(
            AreaZone draft,
            AreaZone lightingZone,
            List<AreaZone> exclusions,
            SelectionState.DisplayMode displayMode,
            SelectionState.SphereDisplayMode sphereDisplayMode,
            Map<Long, BlockySphereMesh> blockySphereMeshes
    ) {
    }

    @FunctionalInterface
    private interface GeometrySink {
        void submit(PoseStack poseStack, RenderType renderType,
                    GeometryRenderer renderer);
    }

    @FunctionalInterface
    private interface GeometryRenderer {
        void render(PoseStack.Pose pose, VertexConsumer buffer);
    }

    private record BlockySphereMesh(int[] edges, int[] faceStrips) {
    }

    private static final class IntArrayBuilder {
        private int[] values;
        private int size;

        private IntArrayBuilder(int initialCapacity) {
            values = new int[initialCapacity];
        }

        private void add(int value) {
            if (size == values.length) {
                int[] expanded = new int[values.length * 2];
                System.arraycopy(values, 0, expanded, 0, values.length);
                values = expanded;
            }
            values[size++] = value;
        }

        private int[] toArray() {
            int[] result = new int[size];
            System.arraycopy(values, 0, result, 0, size);
            return result;
        }
    }

}
