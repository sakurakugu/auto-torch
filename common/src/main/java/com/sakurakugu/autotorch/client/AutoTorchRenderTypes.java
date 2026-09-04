package com.sakurakugu.autotorch.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.opengl.GL11;

/** 创建透视线框使用的无深度测试渲染类型。 */
public final class AutoTorchRenderTypes {
    private static final Map<ResourceLocation, RenderType> SEE_THROUGH_NUMBERS = new HashMap<>();
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

    /** 返回显式关闭深度测试的数字纹理渲染类型。 */
    public static RenderType seeThroughNumbers(ResourceLocation texture) {
        return SEE_THROUGH_NUMBERS.computeIfAbsent(texture, AutoTorchRenderTypes::createSeeThroughNumbers);
    }

    /** 在相机模型视图矩阵仍有效时结束全部数字透视批次。 */
    public static void endSeeThroughNumberBatches(MultiBufferSource.BufferSource buffers) {
        for (RenderType renderType : SEE_THROUGH_NUMBERS.values()) {
            buffers.endBatch(renderType);
        }
    }

    private static RenderType createSeeThroughNumbers(ResourceLocation texture) {
        return RenderTypeInvoker.autotorch$create(
                "autotorch_light_overlay_see_through_numbers",
                DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP,
                GL11.GL_QUADS,
                256,
                false,
                true,
                () -> {
                    RenderSystem.enableTexture();
                    Minecraft minecraft = Minecraft.getInstance();
                    minecraft.getTextureManager().bind(texture);
                    RenderSystem.enableAlphaTest();
                    RenderSystem.defaultAlphaFunc();
                    RenderSystem.enableBlend();
                    RenderSystem.defaultBlendFunc();
                    minecraft.gameRenderer.lightTexture().turnOnLightLayer();
                    RenderSystem.disableDepthTest();
                    RenderSystem.depthMask(false);
                    RenderSystem.disableCull();
                    RenderSystem.color4f(1.0F, 1.0F, 1.0F, 1.0F);
                },
                () -> {
                    RenderSystem.enableCull();
                    RenderSystem.depthMask(true);
                    RenderSystem.enableDepthTest();
                    Minecraft.getInstance().gameRenderer.lightTexture().turnOffLightLayer();
                    RenderSystem.disableBlend();
                    RenderSystem.disableAlphaTest();
                }
        );
    }
}
