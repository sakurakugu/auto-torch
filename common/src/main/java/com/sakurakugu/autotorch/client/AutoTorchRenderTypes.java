package com.sakurakugu.autotorch.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

/** 创建线框与数字渲染使用的明确深度状态。 */
public final class AutoTorchRenderTypes {
    private static final int DEPTH_LEQUAL = 0x0203;
    private static final Map<ResourceLocation, RenderType> NUMBERS = new HashMap<>();
    private static final Map<ResourceLocation, RenderType> SEE_THROUGH_NUMBERS = new HashMap<>();
    private static final RenderType SEE_THROUGH_LINES = createLines(true);

    private AutoTorchRenderTypes() {
    }

    public static RenderType seeThroughLines() {
        return SEE_THROUGH_LINES;
    }

    /** 创建数字纹理渲染类型，显式设置深度状态以保证两个加载器行为一致。 */
    public static RenderType numbers(ResourceLocation texture, boolean seeThrough) {
        Map<ResourceLocation, RenderType> renderTypes = seeThrough ? SEE_THROUGH_NUMBERS : NUMBERS;
        return renderTypes.computeIfAbsent(texture, key -> createNumbers(key, seeThrough));
    }

    /** 在相机模型视图矩阵仍有效时结束全部数字批次。 */
    public static void endNumberBatches(MultiBufferSource.BufferSource buffers) {
        for (RenderType renderType : NUMBERS.values()) {
            buffers.endBatch(renderType);
        }
        for (RenderType renderType : SEE_THROUGH_NUMBERS.values()) {
            buffers.endBatch(renderType);
        }
    }

    private static RenderType createNumbers(ResourceLocation texture, boolean seeThrough) {
        return new RenderType(
                seeThrough ? "autotorch_see_through_numbers" : "autotorch_numbers",
                DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP,
                VertexFormat.Mode.QUADS,
                256,
                false,
                true,
                () -> {
                    RenderSystem.setShader(seeThrough
                            ? GameRenderer::getRendertypeTextSeeThroughShader
                            : GameRenderer::getRendertypeTextShader);
                    RenderSystem.setShaderTexture(0, texture);
                    Minecraft minecraft = Minecraft.getInstance();
                    minecraft.gameRenderer.lightTexture().turnOnLightLayer();
                    RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
                    RenderSystem.enableBlend();
                    RenderSystem.defaultBlendFunc();
                    if (seeThrough) {
                        RenderSystem.disableDepthTest();
                    } else {
                        RenderSystem.enableDepthTest();
                        RenderSystem.depthFunc(DEPTH_LEQUAL);
                    }
                    RenderSystem.depthMask(false);
                    RenderSystem.disableCull();
                },
                () -> {
                    RenderSystem.depthMask(true);
                    RenderSystem.enableDepthTest();
                    RenderSystem.enableCull();
                    Minecraft.getInstance().gameRenderer.lightTexture().turnOffLightLayer();
                    RenderSystem.disableBlend();
                }
        ) { };
    }

    private static RenderType createLines(boolean seeThrough) {
        return new RenderType(
                "autotorch_light_overlay_see_through_lines",
                DefaultVertexFormat.POSITION_COLOR_NORMAL,
                VertexFormat.Mode.LINES,
                1536,
                false,
                false,
                () -> {
                    RenderSystem.setShader(GameRenderer::getRendertypeLinesShader);
                    RenderSystem.enableBlend();
                    RenderSystem.defaultBlendFunc();
                    if (seeThrough) {
                        RenderSystem.disableDepthTest();
                    } else {
                        RenderSystem.enableDepthTest();
                        RenderSystem.depthFunc(DEPTH_LEQUAL);
                    }
                    RenderSystem.depthMask(false);
                    RenderSystem.disableCull();
                },
                () -> {
                    RenderSystem.depthMask(true);
                    RenderSystem.enableDepthTest();
                    RenderSystem.enableCull();
                    RenderSystem.disableBlend();
                }
        ) { };
    }
}
