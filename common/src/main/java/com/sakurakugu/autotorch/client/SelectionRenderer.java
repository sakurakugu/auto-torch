package com.sakurakugu.autotorch.client;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;

import com.sakurakugu.autotorch.network.AreaShape;
import com.sakurakugu.autotorch.network.AreaZone;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.opengl.GL11;

/** 在世界中持续绘制选区草稿、照明范围和所有排除范围。 */
public final class SelectionRenderer {
    private static final int DEPTH_LEQUAL = 0x0203;
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
    private static final Map<Long, BlockySphereMesh> BLOCKY_SPHERE_CACHE =
            new LinkedHashMap<Long, BlockySphereMesh>(8, 0.75F, true) {
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
                    null, null, Collections.emptyList(), SelectionState.displayMode(),
                    SelectionState.sphereDisplayMode(), Collections.emptyMap()
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

    public static void render(Vec3 camera) {
        RenderData data = renderData;
        if (data == null || data.draft() == null && data.lightingZone() == null && data.exclusions().isEmpty()) {
            return;
        }
        boolean lines = data.displayMode() == SelectionState.DisplayMode.LINES;
        setupRenderState(lines);
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder builder = tesselator.getBuilder();
        builder.begin(lines ? GL11.GL_LINES : GL11.GL_QUADS, DefaultVertexFormat.POSITION_COLOR);
        builder.offset(-camera.x(), -camera.y(), -camera.z());
        renderZones(Pose.INSTANCE, new VertexConsumer(builder), data);
        tesselator.end();
        builder.offset(0.0D, 0.0D, 0.0D);
        clearRenderState(lines);
    }

    private static void setupRenderState(boolean lines) {
        GlStateManager.disableTexture();
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        GlStateManager.enableDepthTest();
        GlStateManager.depthFunc(DEPTH_LEQUAL);
        GlStateManager.disableCull();
        GlStateManager.depthMask(false);
        if (lines) {
            GlStateManager.lineWidth(3.0F);
        } else {
            // 将贴合方块的选区面略微拉近，避免移动时与方块表面发生深度闪烁。
            GlStateManager.polygonOffset(-1.0F, -10.0F);
            GlStateManager.enablePolygonOffset();
        }
    }

    private static void clearRenderState(boolean lines) {
        if (lines) {
            GlStateManager.lineWidth(1.0F);
        } else {
            GlStateManager.polygonOffset(0.0F, 0.0F);
            GlStateManager.disablePolygonOffset();
        }
        GlStateManager.depthMask(true);
        GlStateManager.enableCull();
        GlStateManager.disableBlend();
        GlStateManager.enableTexture();
    }

    private static void renderZones(Pose pose, VertexConsumer buffer, RenderData data) {
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
            Pose pose,
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
                        fullBlockBounds(zone), lineColor, width);
            }
        } else if (zone.shape() == AreaShape.SPHERE) {
            if (data.sphereDisplayMode() == SelectionState.SphereDisplayMode.BLOCKY) {
                renderBlockySphereFaces(pose, buffer, zone,
                        data.blockySphereMeshes().get(zone.radiusSquared()), faceColor);
            } else {
                renderSphereFaces(pose, buffer, zone, faceColor);
            }
        } else {
            renderBoxFaces(pose, buffer, fullBlockBounds(zone), faceColor);
        }
    }

    private static AABB fullBlockBounds(AreaZone zone) {
        return new AABB(
                zone.min().getX(), zone.min().getY(), zone.min().getZ(),
                zone.max().getX() + 1, zone.max().getY() + 1, zone.max().getZ() + 1
        );
    }

    private static void renderBoxLines(Pose pose, VertexConsumer buffer, AABB box, int color, float width) {
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

    private static void renderBoxFaces(Pose pose, VertexConsumer buffer, AABB box, int color) {
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
            Pose pose, VertexConsumer buffer, AreaZone zone, int color, float width
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

    private static void renderSphereFaces(Pose pose, VertexConsumer buffer, AreaZone zone, int color) {
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
            Pose pose, VertexConsumer buffer, AreaZone zone, BlockySphereMesh mesh, int color
    ) {
        BlockPos center = zone.first();
        for (int index = 0; index < mesh.faceStrips().length; index += 2) {
            blockFaceStrip(pose, buffer, center, mesh.faceStrips()[index], mesh.faceStrips()[index + 1], color);
        }
    }

    private static void renderBlockySphereLines(
            Pose pose, VertexConsumer buffer, AreaZone zone, BlockySphereMesh mesh, int color, float width
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
            return Collections.emptyMap();
        }
        Map<Long, BlockySphereMesh> meshes = new HashMap<>();
        addBlockySphereMesh(meshes, draft);
        addBlockySphereMesh(meshes, lightingZone);
        for (AreaZone exclusion : exclusions) {
            addBlockySphereMesh(meshes, exclusion);
        }
        return Collections.unmodifiableMap(new HashMap<>(meshes));
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
            case 0: addXFaceEdges(edges, x + 1, y, z); break;
            case 1: addXFaceEdges(edges, x, y, z); break;
            case 2: addYFaceEdges(edges, x, y + 1, z); break;
            case 3: addYFaceEdges(edges, x, y, z); break;
            case 4: addZFaceEdges(edges, x, y, z + 1); break;
            default: addZFaceEdges(edges, x, y, z); break;
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
            Pose pose, VertexConsumer buffer, BlockPos center, int encodedEdge, int color, float width
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
            Pose pose, VertexConsumer buffer, BlockPos center, int encodedFace, int length, int color
    ) {
        int x = center.getX() + (encodedFace & BLOCK_OFFSET_MASK) - BLOCK_OFFSET_BIAS;
        int y = center.getY() + ((encodedFace >> BLOCK_OFFSET_BITS) & BLOCK_OFFSET_MASK) - BLOCK_OFFSET_BIAS;
        int z = center.getZ() + ((encodedFace >> (BLOCK_OFFSET_BITS * 2)) & BLOCK_OFFSET_MASK) - BLOCK_OFFSET_BIAS;
        int direction = encodedFace >>> BLOCK_FACE_DIRECTION_SHIFT;
        switch (direction) {
            case 0: quad(pose, buffer,
                    x + 1, y, z, x + 1, y + 1, z, x + 1, y + 1, z + length, x + 1, y, z + length, color);
                break;
            case 1: quad(pose, buffer,
                    x, y, z + length, x, y + 1, z + length, x, y + 1, z, x, y, z, color);
                break;
            case 2: quad(pose, buffer,
                    x, y + 1, z + length, x + 1, y + 1, z + length, x + 1, y + 1, z, x, y + 1, z, color);
                break;
            case 3: quad(pose, buffer,
                    x, y, z, x + 1, y, z, x + 1, y, z + length, x, y, z + length, color);
                break;
            case 4: quad(pose, buffer,
                    x + 1, y, z + 1, x + 1, y + length, z + 1, x, y + length, z + 1, x, y, z + 1, color);
                break;
            default: quad(pose, buffer,
                    x, y, z, x, y + length, z, x + 1, y + length, z, x + 1, y, z, color);
                break;
        }
    }

    private static void sphereQuad(
            Pose pose, VertexConsumer buffer, double cx, double cy, double cz, double radius,
            int latitude, int longitude, int color
    ) {
        sphereVertex(pose, buffer, cx, cy, cz, radius, latitude, longitude, color);
        sphereVertex(pose, buffer, cx, cy, cz, radius, latitude, longitude + 1, color);
        sphereVertex(pose, buffer, cx, cy, cz, radius, latitude + 1, longitude + 1, color);
        sphereVertex(pose, buffer, cx, cy, cz, radius, latitude + 1, longitude, color);
    }

    private static void sphereVertex(
            Pose pose, VertexConsumer buffer, double cx, double cy, double cz, double radius,
            int latitude, int longitude, int color
    ) {
        double horizontal = SPHERE_LATITUDE_COS[latitude] * radius;
        applyColor(buffer.vertex(pose.pose(),
                (float) (cx + SPHERE_LONGITUDE_COS[longitude] * horizontal),
                (float) (cy + SPHERE_LATITUDE_SIN[latitude] * radius),
                (float) (cz + SPHERE_LONGITUDE_SIN[longitude] * horizontal)), color).endVertex();
    }

    private static void quad(
            Pose pose, VertexConsumer buffer,
            double x1, double y1, double z1, double x2, double y2, double z2,
            double x3, double y3, double z3, double x4, double y4, double z4, int color
    ) {
        // 选区面使用独立四边形且不写深度，保证水面等透明内容仍能正常渲染。
        applyColor(buffer.vertex(pose.pose(), (float) x1, (float) y1, (float) z1), color).endVertex();
        applyColor(buffer.vertex(pose.pose(), (float) x2, (float) y2, (float) z2), color).endVertex();
        applyColor(buffer.vertex(pose.pose(), (float) x3, (float) y3, (float) z3), color).endVertex();
        applyColor(buffer.vertex(pose.pose(), (float) x4, (float) y4, (float) z4), color).endVertex();
    }

    private static void line(
            Pose pose, VertexConsumer buffer,
            double x1, double y1, double z1, double x2, double y2, double z2,
            int color, float width
    ) {
        applyColor(buffer.vertex(pose.pose(), (float) x1, (float) y1, (float) z1), color)
                .endVertex();
        applyColor(buffer.vertex(pose.pose(), (float) x2, (float) y2, (float) z2), color)
                .endVertex();
    }

    private static VertexConsumer applyColor(VertexConsumer vertex, int color) {
        return vertex.color((color >> 16) & 0xFF, (color >> 8) & 0xFF,
                color & 0xFF, (color >>> 24) & 0xFF);
    }

    private static final class RenderData {
        private final AreaZone draft;
        private final AreaZone lightingZone;
        private final List<AreaZone> exclusions;
        private final SelectionState.DisplayMode displayMode;
        private final SelectionState.SphereDisplayMode sphereDisplayMode;
        private final Map<Long, BlockySphereMesh> blockySphereMeshes;

        private RenderData(
                AreaZone draft, AreaZone lightingZone, List<AreaZone> exclusions,
                SelectionState.DisplayMode displayMode, SelectionState.SphereDisplayMode sphereDisplayMode,
                Map<Long, BlockySphereMesh> blockySphereMeshes
        ) {
            this.draft = draft;
            this.lightingZone = lightingZone;
            this.exclusions = exclusions;
            this.displayMode = displayMode;
            this.sphereDisplayMode = sphereDisplayMode;
            this.blockySphereMeshes = blockySphereMeshes;
        }

        private AreaZone draft() { return draft; }
        private AreaZone lightingZone() { return lightingZone; }
        private List<AreaZone> exclusions() { return exclusions; }
        private SelectionState.DisplayMode displayMode() { return displayMode; }
        private SelectionState.SphereDisplayMode sphereDisplayMode() { return sphereDisplayMode; }
        private Map<Long, BlockySphereMesh> blockySphereMeshes() { return blockySphereMeshes; }
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
            builder.vertex(x, y, z);
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
        private enum SourceFactor { SRC_ALPHA }
        private enum DestFactor { ONE_MINUS_SRC_ALPHA }

        private static void disableTexture() { GL11.glDisable(GL11.GL_TEXTURE_2D); }
        private static void enableTexture() { GL11.glEnable(GL11.GL_TEXTURE_2D); }
        private static void enableBlend() { GL11.glEnable(GL11.GL_BLEND); }
        private static void disableBlend() { GL11.glDisable(GL11.GL_BLEND); }
        private static void blendFunc(SourceFactor source, DestFactor destination) {
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        }
        private static void enableDepthTest() { GL11.glEnable(GL11.GL_DEPTH_TEST); }
        private static void depthFunc(int function) { GL11.glDepthFunc(function); }
        private static void depthMask(boolean enabled) { GL11.glDepthMask(enabled); }
        private static void disableCull() { GL11.glDisable(GL11.GL_CULL_FACE); }
        private static void enableCull() { GL11.glEnable(GL11.GL_CULL_FACE); }
        private static void lineWidth(float width) { GL11.glLineWidth(width); }
        private static void polygonOffset(float factor, float units) { GL11.glPolygonOffset(factor, units); }
        private static void enablePolygonOffset() { GL11.glEnable(GL11.GL_POLYGON_OFFSET_FILL); }
        private static void disablePolygonOffset() { GL11.glDisable(GL11.GL_POLYGON_OFFSET_FILL); }
    }

    private static final class BlockySphereMesh {
        private final int[] edges;
        private final int[] faceStrips;

        private BlockySphereMesh(int[] edges, int[] faceStrips) {
            this.edges = edges;
            this.faceStrips = faceStrips;
        }

        private int[] edges() { return edges; }
        private int[] faceStrips() { return faceStrips; }
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
