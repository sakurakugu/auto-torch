package dev.sakurakugu.autotorch.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.sakurakugu.autotorch.AutoTorchMod;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ExtractLevelRenderStateEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;

/** Draws cached light-risk markers as depth-tested crosses on spawnable floor surfaces. */
public final class LightOverlayRenderer {
    private static final int ALWAYS_RISK_COLOR = 0xE0FF3030;
    private static final int NIGHT_RISK_COLOR = 0xE0FFD23C;
    private static final float LINE_WIDTH = 2.5F;
    private static final double SURFACE_OFFSET = 0.0125D;
    private static final double CROSS_MARGIN = 0.14D;
    private static final ContextKey<RenderData> RENDER_DATA = new ContextKey<>(
            Identifier.fromNamespaceAndPath(AutoTorchMod.MOD_ID, "light_overlay")
    );

    private LightOverlayRenderer() {
    }

    public static void extract(ExtractLevelRenderStateEvent event) {
        List<LightOverlayState.Marker> markers = LightOverlayState.isEnabled()
                ? LightOverlayState.markers() : List.of();
        event.getRenderState().setRenderData(RENDER_DATA, new RenderData(markers));
    }

    public static void submit(SubmitCustomGeometryEvent event) {
        RenderData data = event.getLevelRenderState().getRenderData(RENDER_DATA);
        if (data == null || data.markers().isEmpty() || Minecraft.getInstance().level == null) {
            return;
        }

        Vec3 camera = event.getLevelRenderState().cameraRenderState.pos;
        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        poseStack.translate(-camera.x(), -camera.y(), -camera.z());
        event.getSubmitNodeCollector().submitCustomGeometry(
                poseStack, RenderTypes.linesTranslucent(), (pose, buffer) -> submitMarkers(pose, buffer, data.markers())
        );
        poseStack.popPose();
    }

    private static void submitMarkers(PoseStack.Pose pose, VertexConsumer buffer, List<LightOverlayState.Marker> markers) {
        for (LightOverlayState.Marker marker : markers) {
            double x0 = marker.pos().getX() + CROSS_MARGIN;
            double x1 = marker.pos().getX() + 1.0D - CROSS_MARGIN;
            double y = marker.pos().getY() + SURFACE_OFFSET;
            double z0 = marker.pos().getZ() + CROSS_MARGIN;
            double z1 = marker.pos().getZ() + 1.0D - CROSS_MARGIN;
            int color = marker.nightOnly() ? NIGHT_RISK_COLOR : ALWAYS_RISK_COLOR;
            line(pose, buffer, x0, y, z0, x1, y, z1, color);
            line(pose, buffer, x1, y, z0, x0, y, z1, color);
        }
    }

    private static void line(
            PoseStack.Pose pose, VertexConsumer buffer,
            double x1, double y1, double z1, double x2, double y2, double z2, int color
    ) {
        float nx = (float) (x2 - x1);
        float ny = (float) (y2 - y1);
        float nz = (float) (z2 - z1);
        buffer.addVertex(pose, (float) x1, (float) y1, (float) z1)
                .setColor(color).setNormal(pose, nx, ny, nz).setLineWidth(LINE_WIDTH);
        buffer.addVertex(pose, (float) x2, (float) y2, (float) z2)
                .setColor(color).setNormal(pose, nx, ny, nz).setLineWidth(LINE_WIDTH);
    }

    private record RenderData(List<LightOverlayState.Marker> markers) {
    }
}
