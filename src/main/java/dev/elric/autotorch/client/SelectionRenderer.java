package dev.elric.autotorch.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.elric.autotorch.AutoTorchMod;
import dev.elric.autotorch.network.AreaShape;
import dev.elric.autotorch.network.AreaZone;
import java.util.List;
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
    private static final ContextKey<RenderData> RENDER_DATA = new ContextKey<>(
            Identifier.fromNamespaceAndPath(AutoTorchMod.MOD_ID, "selection_overlay")
    );

    private SelectionRenderer() {
    }

    public static void extract(ExtractLevelRenderStateEvent event) {
        BlockPos fallback = event.getCamera().blockPosition();
        event.getRenderState().setRenderData(RENDER_DATA, new RenderData(
                SelectionState.drafting() ? SelectionState.draft(fallback) : null,
                SelectionState.lightingZone(),
                SelectionState.exclusions(),
                SelectionState.displayMode()
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
            submitZone(event, poseStack, data.draft(), data.displayMode(), DRAFT_LINE_COLOR, DRAFT_FACE_COLOR, 3.0F);
        }
        if (data.lightingZone() != null) {
            submitZone(event, poseStack, data.lightingZone(), data.displayMode(),
                    SELECTION_LINE_COLOR, SELECTION_FACE_COLOR, 3.0F);
        }
        for (AreaZone exclusion : data.exclusions()) {
            submitZone(event, poseStack, exclusion, data.displayMode(),
                    EXCLUSION_LINE_COLOR, EXCLUSION_FACE_COLOR, 2.0F);
        }
        poseStack.popPose();
    }

    private static void submitZone(
            SubmitCustomGeometryEvent event,
            PoseStack poseStack,
            AreaZone zone,
            SelectionState.DisplayMode displayMode,
            int lineColor,
            int faceColor,
            float width
    ) {
        if (displayMode == SelectionState.DisplayMode.LINES) {
            if (zone.shape() == AreaShape.SPHERE) {
                submitSphereLines(event, poseStack, zone, lineColor, width);
            } else {
                submitBoxLines(event, poseStack, AABB.encapsulatingFullBlocks(zone.min(), zone.max()), lineColor, width);
            }
        } else if (zone.shape() == AreaShape.SPHERE) {
            submitSphereFaces(event, poseStack, zone, faceColor);
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
            SelectionState.DisplayMode displayMode
    ) {
    }
}
