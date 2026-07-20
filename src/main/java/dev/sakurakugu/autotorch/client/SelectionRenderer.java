package dev.sakurakugu.autotorch.client;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import dev.sakurakugu.autotorch.AutoTorchMod;
import dev.sakurakugu.autotorch.network.AreaShape;
import dev.sakurakugu.autotorch.network.AreaZone;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ExtractLevelRenderStateEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;

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
    private static final int BLOCK_OFFSET_BITS = 9;
    private static final int BLOCK_OFFSET_MASK = (1 << BLOCK_OFFSET_BITS) - 1;
    private static final int BLOCK_OFFSET_BIAS = AreaZone.MAX_SPHERE_RADIUS;
    private static final int BLOCK_FACE_DIRECTION_SHIFT = BLOCK_OFFSET_BITS * 3;
    private static final int MAX_CACHED_BLOCKY_SPHERES = 4;
    private static final Map<Long, BlockySphereMesh> BLOCKY_SPHERE_CACHE = new LinkedHashMap<>(8, 0.75F, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Long, BlockySphereMesh> eldest) {
            return size() > MAX_CACHED_BLOCKY_SPHERES;
        }
    };
    private static final ContextKey<RenderData> RENDER_DATA = new ContextKey<>(
            Identifier.fromNamespaceAndPath(AutoTorchMod.MOD_ID, "selection_overlay")
    );

    private SelectionRenderer() {
    }

    public static void extract(ExtractLevelRenderStateEvent event) {
        if (!SelectionState.isOverlayEnabled()) {
            event.getRenderState().setRenderData(RENDER_DATA, new RenderData(
                    null, null, List.of(), SelectionState.displayMode(), SelectionState.sphereDisplayMode()
            ));
            return;
        }
        BlockPos fallback = event.getCamera().blockPosition();
        event.getRenderState().setRenderData(RENDER_DATA, new RenderData(
                SelectionState.drafting() ? SelectionState.draft(fallback) : null,
                SelectionState.lightingZone(),
                SelectionState.exclusions(),
                SelectionState.displayMode(),
                SelectionState.sphereDisplayMode()
        ));
    }

    public static void submit(SubmitCustomGeometryEvent event) {
        RenderData data = event.getLevelRenderState().getRenderData(RENDER_DATA);
        if (data == null) {
            return;
        }
        Vec3 camera = event.getLevelRenderState().cameraRenderState.pos;
        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        poseStack.translate(-camera.x(), -camera.y(), -camera.z());

        if (data.draft() != null) {
            submitZone(event, poseStack, data.draft(), data.displayMode(), data.sphereDisplayMode(),
                    DRAFT_LINE_COLOR, DRAFT_FACE_COLOR, 3.0F);
        }
        if (data.lightingZone() != null) {
            submitZone(event, poseStack, data.lightingZone(), data.displayMode(), data.sphereDisplayMode(),
                    SELECTION_LINE_COLOR, SELECTION_FACE_COLOR, 3.0F);
        }
        for (AreaZone exclusion : data.exclusions()) {
            submitZone(event, poseStack, exclusion, data.displayMode(), data.sphereDisplayMode(),
                    EXCLUSION_LINE_COLOR, EXCLUSION_FACE_COLOR, 2.0F);
        }
        poseStack.popPose();
    }

    private static void submitZone(
            SubmitCustomGeometryEvent event,
            PoseStack poseStack,
            AreaZone zone,
            SelectionState.DisplayMode displayMode,
            SelectionState.SphereDisplayMode sphereDisplayMode,
            int lineColor,
            int faceColor,
            float width
    ) {
        if (displayMode == SelectionState.DisplayMode.LINES) {
            if (zone.shape() == AreaShape.SPHERE) {
                if (sphereDisplayMode == SelectionState.SphereDisplayMode.BLOCKY) {
                    submitBlockySphereLines(event, poseStack, zone, lineColor, width);
                } else {
                    submitSphereLines(event, poseStack, zone, lineColor, width);
                }
            } else {
                submitBoxLines(event, poseStack, AABB.encapsulatingFullBlocks(zone.min(), zone.max()), lineColor, width);
            }
        } else if (zone.shape() == AreaShape.SPHERE) {
            if (sphereDisplayMode == SelectionState.SphereDisplayMode.BLOCKY) {
                submitBlockySphereFaces(event, poseStack, zone, faceColor);
            } else {
                submitSphereFaces(event, poseStack, zone, faceColor);
            }
        } else {
            submitBoxFaces(event, poseStack, AABB.encapsulatingFullBlocks(zone.min(), zone.max()), faceColor);
        }
    }

    private static void submitBoxLines(
            SubmitCustomGeometryEvent event, PoseStack poseStack, AABB box, int color, float width
    ) {
        event.getSubmitNodeCollector().submitCustomGeometry(poseStack, RenderTypes.linesTranslucent(), (pose, buffer) -> {
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
        });
    }

    private static void submitBoxFaces(SubmitCustomGeometryEvent event, PoseStack poseStack, AABB box, int color) {
        event.getSubmitNodeCollector().submitCustomGeometry(poseStack, RenderTypes.debugFilledBox(), (pose, buffer) -> {
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
        });
    }

    private static void submitSphereLines(
            SubmitCustomGeometryEvent event, PoseStack poseStack, AreaZone zone, int color, float width
    ) {
        double cx = zone.first().getX() + 0.5;
        double cy = zone.first().getY() + 0.5;
        double cz = zone.first().getZ() + 0.5;
        double radius = Math.sqrt(zone.radiusSquared()) + 0.5;
        event.getSubmitNodeCollector().submitCustomGeometry(poseStack, RenderTypes.linesTranslucent(), (pose, buffer) -> {
            for (int plane = 0; plane < 3; plane++) {
                for (int segment = 0; segment < SPHERE_LONGITUDE_SEGMENTS; segment++) {
                    double angle1 = Math.PI * 2.0 * segment / SPHERE_LONGITUDE_SEGMENTS;
                    double angle2 = Math.PI * 2.0 * (segment + 1) / SPHERE_LONGITUDE_SEGMENTS;
                    double a1 = Math.cos(angle1) * radius;
                    double b1 = Math.sin(angle1) * radius;
                    double a2 = Math.cos(angle2) * radius;
                    double b2 = Math.sin(angle2) * radius;
                    if (plane == 0) {
                        line(pose, buffer, cx + a1, cy + b1, cz, cx + a2, cy + b2, cz, color, width);
                    } else if (plane == 1) {
                        line(pose, buffer, cx + a1, cy, cz + b1, cx + a2, cy, cz + b2, color, width);
                    } else {
                        line(pose, buffer, cx, cy + a1, cz + b1, cx, cy + a2, cz + b2, color, width);
                    }
                }
            }
        });
    }

    private static void submitSphereFaces(SubmitCustomGeometryEvent event, PoseStack poseStack, AreaZone zone, int color) {
        double cx = zone.first().getX() + 0.5;
        double cy = zone.first().getY() + 0.5;
        double cz = zone.first().getZ() + 0.5;
        double radius = Math.sqrt(zone.radiusSquared()) + 0.5;
        event.getSubmitNodeCollector().submitCustomGeometry(poseStack, RenderTypes.debugFilledBox(), (pose, buffer) -> {
            for (int latitude = 0; latitude < SPHERE_LATITUDE_SEGMENTS; latitude++) {
                double lat1 = -Math.PI / 2.0 + Math.PI * latitude / SPHERE_LATITUDE_SEGMENTS;
                double lat2 = -Math.PI / 2.0 + Math.PI * (latitude + 1) / SPHERE_LATITUDE_SEGMENTS;
                for (int longitude = 0; longitude < SPHERE_LONGITUDE_SEGMENTS; longitude++) {
                    double lon1 = Math.PI * 2.0 * longitude / SPHERE_LONGITUDE_SEGMENTS;
                    double lon2 = Math.PI * 2.0 * (longitude + 1) / SPHERE_LONGITUDE_SEGMENTS;
                    double[] p1 = spherePoint(cx, cy, cz, radius, lat1, lon1);
                    double[] p2 = spherePoint(cx, cy, cz, radius, lat1, lon2);
                    double[] p3 = spherePoint(cx, cy, cz, radius, lat2, lon2);
                    double[] p4 = spherePoint(cx, cy, cz, radius, lat2, lon1);
                    quad(pose, buffer, p1[0], p1[1], p1[2], p2[0], p2[1], p2[2],
                            p3[0], p3[1], p3[2], p4[0], p4[1], p4[2], color);
                }
            }
        });
    }

    private static void submitBlockySphereFaces(
            SubmitCustomGeometryEvent event, PoseStack poseStack, AreaZone zone, int color
    ) {
        BlockySphereMesh mesh = blockySphereMesh(zone.radiusSquared());
        BlockPos center = zone.first();
        event.getSubmitNodeCollector().submitCustomGeometry(poseStack, RenderTypes.debugFilledBox(), (pose, buffer) -> {
            for (int index = 0; index < mesh.faceStrips().length; index += 2) {
                blockFaceStrip(pose, buffer, center, mesh.faceStrips()[index], mesh.faceStrips()[index + 1], color);
            }
        });
    }

    private static void submitBlockySphereLines(
            SubmitCustomGeometryEvent event, PoseStack poseStack, AreaZone zone, int color, float width
    ) {
        BlockySphereMesh mesh = blockySphereMesh(zone.radiusSquared());
        BlockPos center = zone.first();
        event.getSubmitNodeCollector().submitCustomGeometry(poseStack, RenderTypes.linesTranslucent(), (pose, buffer) -> {
            for (int face : mesh.faces()) {
                blockFace(pose, buffer, center, face, color, true, width);
            }
        });
    }

    private static BlockySphereMesh blockySphereMesh(long radiusSquared) {
        synchronized (BLOCKY_SPHERE_CACHE) {
            return BLOCKY_SPHERE_CACHE.computeIfAbsent(radiusSquared, SelectionRenderer::buildBlockySphereMesh);
        }
    }

    private static BlockySphereMesh buildBlockySphereMesh(long radiusSquared) {
        int radius = (int) Math.sqrt(radiusSquared);
        IntArrayBuilder faces = new IntArrayBuilder(Math.max(16, radius * radius * 16));
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
                faces.add(encodeBlockFace(edge, first, second, 0));
                faces.add(encodeBlockFace(-edge, first, second, 1));
                faces.add(encodeBlockFace(first, edge, second, 2));
                faces.add(encodeBlockFace(first, -edge, second, 3));
                faces.add(encodeBlockFace(first, second, edge, 4));
                faces.add(encodeBlockFace(first, second, -edge, 5));
            }
        }
        return new BlockySphereMesh(faces.toArray(), faceStrips.toArray());
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

    private static void blockFace(
            PoseStack.Pose pose, VertexConsumer buffer, BlockPos center, int encodedFace,
            int color, boolean outline, float width
    ) {
        int x = center.getX() + (encodedFace & BLOCK_OFFSET_MASK) - BLOCK_OFFSET_BIAS;
        int y = center.getY() + ((encodedFace >> BLOCK_OFFSET_BITS) & BLOCK_OFFSET_MASK) - BLOCK_OFFSET_BIAS;
        int z = center.getZ() + ((encodedFace >> (BLOCK_OFFSET_BITS * 2)) & BLOCK_OFFSET_MASK) - BLOCK_OFFSET_BIAS;
        int direction = encodedFace >>> BLOCK_FACE_DIRECTION_SHIFT;
        switch (direction) {
            case 0 -> blockFaceVertices(pose, buffer, color, outline, width,
                    x + 1, y, z, x + 1, y + 1, z, x + 1, y + 1, z + 1, x + 1, y, z + 1);
            case 1 -> blockFaceVertices(pose, buffer, color, outline, width,
                    x, y, z + 1, x, y + 1, z + 1, x, y + 1, z, x, y, z);
            case 2 -> blockFaceVertices(pose, buffer, color, outline, width,
                    x, y + 1, z + 1, x + 1, y + 1, z + 1, x + 1, y + 1, z, x, y + 1, z);
            case 3 -> blockFaceVertices(pose, buffer, color, outline, width,
                    x, y, z, x + 1, y, z, x + 1, y, z + 1, x, y, z + 1);
            case 4 -> blockFaceVertices(pose, buffer, color, outline, width,
                    x + 1, y, z + 1, x + 1, y + 1, z + 1, x, y + 1, z + 1, x, y, z + 1);
            default -> blockFaceVertices(pose, buffer, color, outline, width,
                    x, y, z, x, y + 1, z, x + 1, y + 1, z, x + 1, y, z);
        }
    }

    private static void blockFaceStrip(
            PoseStack.Pose pose, VertexConsumer buffer, BlockPos center, int encodedFace, int length, int color
    ) {
        int x = center.getX() + (encodedFace & BLOCK_OFFSET_MASK) - BLOCK_OFFSET_BIAS;
        int y = center.getY() + ((encodedFace >> BLOCK_OFFSET_BITS) & BLOCK_OFFSET_MASK) - BLOCK_OFFSET_BIAS;
        int z = center.getZ() + ((encodedFace >> (BLOCK_OFFSET_BITS * 2)) & BLOCK_OFFSET_MASK) - BLOCK_OFFSET_BIAS;
        int direction = encodedFace >>> BLOCK_FACE_DIRECTION_SHIFT;
        switch (direction) {
            case 0 -> blockFaceVertices(pose, buffer, color, false, 0.0F,
                    x + 1, y, z, x + 1, y + 1, z, x + 1, y + 1, z + length, x + 1, y, z + length);
            case 1 -> blockFaceVertices(pose, buffer, color, false, 0.0F,
                    x, y, z + length, x, y + 1, z + length, x, y + 1, z, x, y, z);
            case 2 -> blockFaceVertices(pose, buffer, color, false, 0.0F,
                    x, y + 1, z + length, x + 1, y + 1, z + length, x + 1, y + 1, z, x, y + 1, z);
            case 3 -> blockFaceVertices(pose, buffer, color, false, 0.0F,
                    x, y, z, x + 1, y, z, x + 1, y, z + length, x, y, z + length);
            case 4 -> blockFaceVertices(pose, buffer, color, false, 0.0F,
                    x + 1, y, z + 1, x + 1, y + length, z + 1, x, y + length, z + 1, x, y, z + 1);
            default -> blockFaceVertices(pose, buffer, color, false, 0.0F,
                    x, y, z, x, y + length, z, x + 1, y + length, z, x + 1, y, z);
        }
    }

    private static void blockFaceVertices(
            PoseStack.Pose pose, VertexConsumer buffer, int color, boolean outline, float width,
            double x1, double y1, double z1, double x2, double y2, double z2,
            double x3, double y3, double z3, double x4, double y4, double z4
    ) {
        if (outline) {
            line(pose, buffer, x1, y1, z1, x2, y2, z2, color, width);
            line(pose, buffer, x2, y2, z2, x3, y3, z3, color, width);
            line(pose, buffer, x3, y3, z3, x4, y4, z4, color, width);
            line(pose, buffer, x4, y4, z4, x1, y1, z1, color, width);
        } else {
            quad(pose, buffer, x1, y1, z1, x2, y2, z2, x3, y3, z3, x4, y4, z4, color);
        }
    }

    private static double[] spherePoint(double cx, double cy, double cz, double radius, double latitude, double longitude) {
        double horizontal = Math.cos(latitude) * radius;
        return new double[]{
                cx + Math.cos(longitude) * horizontal,
                cy + Math.sin(latitude) * radius,
                cz + Math.sin(longitude) * horizontal
        };
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
        float length = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        nx /= length;
        ny /= length;
        nz /= length;
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
            SelectionState.SphereDisplayMode sphereDisplayMode
    ) {
    }

    private record BlockySphereMesh(int[] faces, int[] faceStrips) {
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
