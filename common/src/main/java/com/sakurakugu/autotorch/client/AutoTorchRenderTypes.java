package com.sakurakugu.autotorch.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.sakurakugu.autotorch.mixin.client.RenderTypeInvoker;
import net.minecraft.client.renderer.RenderType;
import org.lwjgl.opengl.GL11;

/** 创建透视线框使用的无深度测试渲染类型。 */
public final class AutoTorchRenderTypes {
    private static final RenderType SEE_THROUGH_LINES = RenderTypeInvoker.autotorch$create(
            "autotorch_light_overlay_see_through_lines", DefaultVertexFormat.POSITION_COLOR,
            GL11.GL_LINES, 1536, false, false,
            () -> {
                RenderSystem.disableTexture();
                RenderSystem.enableBlend();
                RenderSystem.defaultBlendFunc();
                RenderSystem.disableDepthTest();
                RenderSystem.depthMask(false);
            },
            () -> {
                RenderSystem.depthMask(true);
                RenderSystem.enableDepthTest();
                RenderSystem.disableBlend();
                RenderSystem.enableTexture();
            }
    );

    private AutoTorchRenderTypes() {
    }

    public static RenderType seeThroughLines() {
        return SEE_THROUGH_LINES;
    }
}
