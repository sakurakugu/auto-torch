package dev.elric.autotorch.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.elric.autotorch.AutoTorchMod;
import dev.elric.autotorch.network.ExclusionZone;
import java.util.List;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ExtractLevelRenderStateEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;

/** 在世界中绘制选区和排除区的线框预览。 */
public final class SelectionRenderer {
    private static final int SELECTION_COLOR = 0xD050FF70;
    private static final int EXCLUSION_COLOR = 0xD0FF5050;
    private static final ContextKey<RenderData> RENDER_DATA = new ContextKey<>(
            Identifier.fromNamespaceAndPath(AutoTorchMod.MOD_ID, "selection_overlay")
    );

    private SelectionRenderer() {
    }

    public static void extract(ExtractLevelRenderStateEvent event) {
        BlockPos fallback = event.getCamera().blockPosition();
        // 渲染状态可能由其他线程消费，因此这里只提交不可变的数据快照。
        event.getRenderState().setRenderData(RENDER_DATA, new RenderData(
                SelectionState.first(fallback),
                SelectionState.second(fallback),
                SelectionState.exclusions()
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
        // 选区坐标位于世界空间，提交顶点前先抵消摄像机的世界坐标。
        poseStack.translate(-camera.x(), -camera.y(), -camera.z());

        submitBox(event, poseStack, AABB.encapsulatingFullBlocks(
                data.first(), data.second()
        ), SELECTION_COLOR, 3.0F);

        for (ExclusionZone exclusion : data.exclusions()) {
            submitBox(event, poseStack, AABB.encapsulatingFullBlocks(exclusion.min(), exclusion.max()), EXCLUSION_COLOR, 2.0F);
        }
        poseStack.popPose();
    }

    private static void submitBox(SubmitCustomGeometryEvent event, PoseStack poseStack, AABB box, int color, float width) {
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

    private static void line(
            PoseStack.Pose pose,
            VertexConsumer buffer,
            double x1, double y1, double z1,
            double x2, double y2, double z2,
            int color,
            float width
    ) {
        // 线渲染类型需要单位方向作为法线，并通过两个顶点组成一条边。
        float nx = (float) (x2 - x1);
        float ny = (float) (y2 - y1);
        float nz = (float) (z2 - z1);
        float length = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        nx /= length;
        ny /= length;
        nz /= length;
        buffer.addVertex(pose, (float) x1, (float) y1, (float) z1).setColor(color).setNormal(pose, nx, ny, nz).setLineWidth(width);
        buffer.addVertex(pose, (float) x2, (float) y2, (float) z2).setColor(color).setNormal(pose, nx, ny, nz).setLineWidth(width);
    }

    private record RenderData(BlockPos first, BlockPos second, List<ExclusionZone> exclusions) {
    }
}
