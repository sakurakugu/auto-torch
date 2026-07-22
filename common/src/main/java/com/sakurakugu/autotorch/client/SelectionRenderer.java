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
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
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
    private static final int SPHERE_LONGITUDE_SEGMENTS = 24;
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

    public static void submit(Vec3 camera, PoseStack poseStack, SubmitNodeCollector collector) {
        renderGeometry(camera, poseStack,
                (stack, renderType, renderer) -> collector.submitCustomGeometry(stack, renderType, renderer::render));
    }

    public static void render(Vec3 camera, PoseStack poseStack, MultiBufferSource.BufferSource buffers) {
        renderGeometry(camera, poseStack,
                (stack, renderType, renderer) -> renderer.render(stack.last(), buffers.getBuffer(renderType)));
    }

    private static void renderGeometry(Vec3 camera, PoseStack poseStack, GeometrySink sink) {
        RenderData data = renderData;
        if (data == null || data.draft() == null && data.lightingZone() == null && data.exclusions().isEmpty()) {
            return;
        }
        poseStack.pushPose();
        poseStack.translate(-camera.x(), -camera.y(), -camera.z());
        RenderType renderType = data.displayMode() == SelectionState.DisplayMode.LINES
                ? RenderTypes.linesTranslucent() : RenderTypes.debugFilledBox();
        sink.submit(poseStack, renderType, (pose, buffer) -> renderZones(pose, buffer, data));
        poseStack.popPose();
    }

    private static void renderZones(PoseStack.Pose pose, VertexConsumer buffer, RenderData data) {
        if (data.draft() != null) {
            renderZone(pose, buffer, data, data.draft(), DRAFT_LINE_COLOR, DRAFT_FACE_COLOR, 3.0F);
        }
        if (data.lightingZone() != null) {
            renderZone(pose, buffer, data, data.lightingZone(),
                    SELECTION_LINE_COLOR, SELECTION_FACE_COLOR, 3.0F);
        }
        for (AreaZone exclusion : data.exclusions()) {
            renderZone(pose, buffer, data, exclusion, EXCLUSION_LINE_COLOR, EXCLUSION_FACE_COLOR, 2.0F);
        }
    }

    private static void renderZone(
            PoseStack.Pose pose,
            VertexConsumer buffer,
            RenderData data,
            AreaZone zone,
            int lineColor,
            int faceColor,
            float width
    ) {
        if (zone.shape() == AreaShape.SPHERE && zone.radiusSquared() > MAX_SPHERE_RADIUS_SQUARED) {
            return;
        }
        if (data.displayMode() == SelectionState.DisplayMode.LINES) {
            if (zone.shape() == AreaShape.SPHERE) {
                if (data.sphereDisplayMode() == SelectionState.SphereDisplayMode.BLOCKY) {
                    renderBlockySphereLines(pose, buffer, zone,
                            data.blockySphereMeshes().get(zone.radiusSquared()), lineColor, width);
                } else {
                    renderSphereLines(pose, buffer, zone, lineColor, width);
                }
            } else {
                renderBoxLines(pose, buffer,
                        AABB.encapsulatingFullBlocks(zone.min(), zone.max()), lineColor, width);
            }
        } else if (zone.shape() == AreaShape.SPHERE) {
            if (data.sphereDisplayMode() == SelectionState.SphereDisplayMode.BLOCKY) {
                renderBlockySphereFaces(pose, buffer, zone,
                        data.blockySphereMeshes().get(zone.radiusSquared()), faceColor);
            } else {
                renderSphereFaces(pose, buffer, zone, faceColor);
            }
        } else {
            renderBoxFaces(pose, buffer, AABB.encapsulatingFullBlocks(zone.min(), zone.max()), faceColor);
        }
    }

    private static void renderBoxLines(PoseStack.Pose pose, VertexConsumer buffer, AABB box, int color, float width) {
        line(pose, buffer, box.minX, box.minY, box.minZ, box.maxX, box.minY, box.minZ, color, width);
        line(pose, buffer, box.maxX, box.minY, box.minZ, box.maxX, box.minY, box.maxZ, color, width);
        line(pose, buffer, box.maxX, box.minY, box.maxZ, box.minX, box.minY, box.maxZ, color, width);
        line(pose, buffer, box.minX, box.minY, box.maxZ, box.minX, box.minY, box.minZ, color, width);
        line(pose, buffer, box.minX, box.maxY, box.minZ, box.maxX, box.maxY, box.minZ, color, width);
        line(pose, buffer, box.maxX, box.maxY, box.minZ, box.maxX, box.maxY, box.maxZ, color, width);
        line(pose, buffer, box.maxX, box.maxY, box.maxZ, box.minX, box.maxY, box.maxZ, color, width);
        line(pose, buffer, box.minX, box.maxY, box.maxZ, box.minX, box.maxY, box.minZ, color, width);
        line(pose, buffer, box.minX, box.minY, box.minZ, box.minX, box.maxY, box.minZ, color, width);
        line(pose, buffer, box.maxX, box.minY, box.minZ, box.maxX, box.maxY, box.minZ, color, width);
        line(pose, buffer, box.maxX, box.minY, box.maxZ, box.maxX, box.maxY, box.maxZ, color, width);
        line(pose, buffer, box.minX, box.minY, box.maxZ, box.minX, box.maxY, box.maxZ, color, width);
    }

    private static void renderBoxFaces(PoseStack.Pose pose, VertexConsumer buffer, AABB box, int color) {
        quad(pose, buffer, box.minX, box.minY, box.minZ, box.maxX, box.minY, box.minZ,
                box.maxX, box.minY, box.maxZ, box.minX, box.minY, box.maxZ, color);
        quad(pose, buffer, box.minX, box.maxY, box.maxZ, box.maxX, box.maxY, box.maxZ,
                box.maxX, box.maxY, box.minZ, box.minX, box.maxY, box.minZ, color);
        quad(pose, buffer, box.minX, box.minY, box.maxZ, box.maxX, box.minY, box.maxZ,
                box.maxX, box.maxY, box.maxZ, box.minX, box.maxY, box.maxZ, color);
        quad(pose, buffer, box.maxX, box.minY, box.minZ, box.minX, box.minY, box.minZ,
                box.minX, box.maxY, box.minZ, box.maxX, box.maxY, box.minZ, color);
        quad(pose, buffer, box.minX, box.minY, box.minZ, box.minX, box.minY, box.maxZ,
                box.minX, box.maxY, box.maxZ, box.minX, box.maxY, box.minZ, color);
        quad(pose, buffer, box.maxX, box.minY, box.maxZ, box.maxX, box.minY, box.minZ,
                box.maxX, box.maxY, box.minZ, box.maxX, box.maxY, box.maxZ, color);
    }

    private static void renderSphereLines(
            PoseStack.Pose pose, VertexConsumer buffer, AreaZone zone, int color, float width
    ) {
        double cx = zone.first().getX() + 0.5;
        double cy = zone.first().getY() + 0.5;
        double cz = zone.first().getZ() + 0.5;
        double radius = Math.sqrt(zone.radiusSquared()) + 0.5;
        for (int plane = 0; plane < 3; plane++) {
            for (int segment = 0; segment < SPHERE_LONGITUDE_SEGMENTS; segment++) {
                double a1 = SPHERE_LONGITUDE_COS[segment] * radius;
                double b1 = SPHERE_LONGITUDE_SIN[segment] * radius;
                double a2 = SPHERE_LONGITUDE_COS[segment + 1] * radius;
                double b2 = SPHERE_LONGITUDE_SIN[segment + 1] * radius;
                if (plane == 0) {
                    line(pose, buffer, cx + a1, cy + b1, cz, cx + a2, cy + b2, cz, color, width);
                } else if (plane == 1) {
                    line(pose, buffer, cx + a1, cy, cz + b1, cx + a2, cy, cz + b2, color, width);
                } else {
                    line(pose, buffer, cx, cy + a1, cz + b1, cx, cy + a2, cz + b2, color, width);
                }
            }
        }
    }

    private static void renderSphereFaces(PoseStack.Pose pose, VertexConsumer buffer, AreaZone zone, int color) {
        double cx = zone.first().getX() + 0.5;
        double cy = zone.first().getY() + 0.5;
        double cz = zone.first().getZ() + 0.5;
        double radius = Math.sqrt(zone.radiusSquared()) + 0.5;
        for (int latitude = 0; latitude < SPHERE_LATITUDE_SEGMENTS; latitude++) {
            for (int longitude = 0; longitude < SPHERE_LONGITUDE_SEGMENTS; longitude++) {
                sphereQuad(pose, buffer, cx, cy, cz, radius, latitude, longitude, color);
            }
        }
    }

    private static void renderBlockySphereFaces(
            PoseStack.Pose pose, VertexConsumer buffer, AreaZone zone, BlockySphereMesh mesh, int color
    ) {
        BlockPos center = zone.first();
        for (int index = 0; index < mesh.faceStrips().length; index += 2) {
            blockFaceStrip(pose, buffer, center, mesh.faceStrips()[index], mesh.faceStrips()[index + 1], color);
        }
    }

    private static void renderBlockySphereLines(
            PoseStack.Pose pose, VertexConsumer buffer, AreaZone zone, BlockySphereMesh mesh, int color, float width
    ) {
        BlockPos center = zone.first();
        for (int edge : mesh.edges()) {
            blockEdge(pose, buffer, center, edge, color, width);
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
            PoseStack.Pose pose, VertexConsumer buffer, BlockPos center, int encodedEdge, int color, float width
    ) {
        int x = center.getX() + (encodedEdge & BLOCK_OFFSET_MASK) - BLOCK_OFFSET_BIAS;
        int y = center.getY() + ((encodedEdge >> BLOCK_OFFSET_BITS) & BLOCK_OFFSET_MASK) - BLOCK_OFFSET_BIAS;
        int z = center.getZ() + ((encodedEdge >> (BLOCK_OFFSET_BITS * 2)) & BLOCK_OFFSET_MASK) - BLOCK_OFFSET_BIAS;
        int axis = encodedEdge >>> BLOCK_EDGE_AXIS_SHIFT;
        line(pose, buffer, x, y, z,
                x + (axis == 0 ? 1 : 0), y + (axis == 1 ? 1 : 0), z + (axis == 2 ? 1 : 0),
                color, width);
    }

    private static void blockFaceStrip(
            PoseStack.Pose pose, VertexConsumer buffer, BlockPos center, int encodedFace, int length, int color
    ) {
        int x = center.getX() + (encodedFace & BLOCK_OFFSET_MASK) - BLOCK_OFFSET_BIAS;
        int y = center.getY() + ((encodedFace >> BLOCK_OFFSET_BITS) & BLOCK_OFFSET_MASK) - BLOCK_OFFSET_BIAS;
        int z = center.getZ() + ((encodedFace >> (BLOCK_OFFSET_BITS * 2)) & BLOCK_OFFSET_MASK) - BLOCK_OFFSET_BIAS;
        int direction = encodedFace >>> BLOCK_FACE_DIRECTION_SHIFT;
        switch (direction) {
            case 0 -> quad(pose, buffer,
                    x + 1, y, z, x + 1, y + 1, z, x + 1, y + 1, z + length, x + 1, y, z + length, color);
            case 1 -> quad(pose, buffer,
                    x, y, z + length, x, y + 1, z + length, x, y + 1, z, x, y, z, color);
            case 2 -> quad(pose, buffer,
                    x, y + 1, z + length, x + 1, y + 1, z + length, x + 1, y + 1, z, x, y + 1, z, color);
            case 3 -> quad(pose, buffer,
                    x, y, z, x + 1, y, z, x + 1, y, z + length, x, y, z + length, color);
            case 4 -> quad(pose, buffer,
                    x + 1, y, z + 1, x + 1, y + length, z + 1, x, y + length, z + 1, x, y, z + 1, color);
            default -> quad(pose, buffer,
                    x, y, z, x, y + length, z, x + 1, y + length, z, x + 1, y, z, color);
        }
    }

    private static void sphereQuad(
            PoseStack.Pose pose, VertexConsumer buffer, double cx, double cy, double cz, double radius,
            int latitude, int longitude, int color
    ) {
        sphereVertex(pose, buffer, cx, cy, cz, radius, latitude, longitude, color);
        sphereVertex(pose, buffer, cx, cy, cz, radius, latitude, longitude + 1, color);
        sphereVertex(pose, buffer, cx, cy, cz, radius, latitude + 1, longitude + 1, color);
        sphereVertex(pose, buffer, cx, cy, cz, radius, latitude + 1, longitude, color);
    }

    private static void sphereVertex(
            PoseStack.Pose pose, VertexConsumer buffer, double cx, double cy, double cz, double radius,
            int latitude, int longitude, int color
    ) {
        double horizontal = SPHERE_LATITUDE_COS[latitude] * radius;
        buffer.addVertex(pose,
                (float) (cx + SPHERE_LONGITUDE_COS[longitude] * horizontal),
                (float) (cy + SPHERE_LATITUDE_SIN[latitude] * radius),
                (float) (cz + SPHERE_LONGITUDE_SIN[longitude] * horizontal)).setColor(color);
    }

    private static void quad(
            PoseStack.Pose pose, VertexConsumer buffer,
            double x1, double y1, double z1, double x2, double y2, double z2,
            double x3, double y3, double z3, double x4, double y4, double z4, int color
    ) {
        buffer.addVertex(pose, (float) x1, (float) y1, (float) z1).setColor(color);
        buffer.addVertex(pose, (float) x2, (float) y2, (float) z2).setColor(color);
        buffer.addVertex(pose, (float) x3, (float) y3, (float) z3).setColor(color);
        buffer.addVertex(pose, (float) x4, (float) y4, (float) z4).setColor(color);
    }

    private static void line(
            PoseStack.Pose pose, VertexConsumer buffer,
            double x1, double y1, double z1, double x2, double y2, double z2,
            int color, float width
    ) {
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
        buffer.addVertex(pose, (float) x1, (float) y1, (float) z1)
                .setColor(color).setNormal(pose, nx, ny, nz).setLineWidth(width);
        buffer.addVertex(pose, (float) x2, (float) y2, (float) z2)
                .setColor(color).setNormal(pose, nx, ny, nz).setLineWidth(width);
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
