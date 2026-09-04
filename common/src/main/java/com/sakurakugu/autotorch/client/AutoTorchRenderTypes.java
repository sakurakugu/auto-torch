package com.sakurakugu.autotorch.client;

import java.util.HashMap;
import java.util.Map;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

/** 创建线框渲染使用的自定义线宽和透视状态。 */
public final class AutoTorchRenderTypes {
    private static final Map<Integer, RenderType> LINES = new HashMap<>();
    private static final Map<Integer, RenderType> SEE_THROUGH_LINES = new HashMap<>();
    private static final Map<ResourceLocation, RenderType> NUMBERS = new HashMap<>();
    private static final Map<ResourceLocation, RenderType> SEE_THROUGH_NUMBERS = new HashMap<>();

    private AutoTorchRenderTypes() {
    }

    public static RenderType seeThroughLines() {
        return lines(1.0F, true);
    }

    /** 根据线宽缓存渲染类型，避免旧版顶点格式无法逐顶点提交线宽。 */
    public static RenderType lines(float width, boolean seeThrough) {
        int widthKey = Math.round(width * 100.0F);
        Map<Integer, RenderType> renderTypes = seeThrough ? SEE_THROUGH_LINES : LINES;
        return renderTypes.computeIfAbsent(widthKey,
                ignored -> RenderTypeAccess.createLines(widthKey / 100.0F, seeThrough));
    }

    /** 结束本次渲染阶段中所有按线宽创建的批次。 */
    public static void endBatches(MultiBufferSource.BufferSource buffers) {
        for (RenderType renderType : LINES.values()) {
            buffers.endBatch(renderType);
        }
        for (RenderType renderType : SEE_THROUGH_LINES.values()) {
            buffers.endBatch(renderType);
        }
    }

    /** 创建数字纹理渲染类型，显式设置深度状态以保证透视开关在各加载器上一致。 */
    public static RenderType numbers(ResourceLocation texture, boolean seeThrough) {
        Map<ResourceLocation, RenderType> renderTypes = seeThrough ? SEE_THROUGH_NUMBERS : NUMBERS;
        return renderTypes.computeIfAbsent(texture, key -> createNumbers(key, seeThrough));
    }

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
                DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP, VertexFormat.Mode.QUADS, 256, false, true,
                () -> {
                    RenderSystem.setShader(seeThrough
                            ? GameRenderer::getRendertypeTextSeeThroughShader
                            : GameRenderer::getRendertypeTextShader);
                    RenderSystem.setShaderTexture(0, texture);
                    RenderSystem.enableBlend();
                    RenderSystem.defaultBlendFunc();
                    if (seeThrough) {
                        RenderSystem.disableDepthTest();
                    } else {
                        RenderSystem.enableDepthTest();
                        RenderSystem.depthFunc(org.lwjgl.opengl.GL11.GL_LEQUAL);
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
        ) {};
    }

    /** 旧版本将复合渲染状态及其预设声明为 protected，只能经由子类访问。 */
    private static final class RenderTypeAccess extends RenderType {
        private RenderTypeAccess() {
            super("autotorch_internal", DefaultVertexFormat.POSITION_COLOR_NORMAL, VertexFormat.Mode.LINES,
                    1, false, false, () -> { }, () -> { });
        }

        private static RenderType createLines(float width, boolean seeThrough) {
            return new RenderType(
                    seeThrough ? "autotorch_see_through_lines" : "autotorch_lines",
                    DefaultVertexFormat.POSITION_COLOR_NORMAL, VertexFormat.Mode.LINES, 1536, false, false,
                    () -> {
                        RenderSystem.setShader(GameRenderer::getRendertypeLinesShader);
                        RenderSystem.lineWidth(width);
                        RenderSystem.enableBlend();
                        RenderSystem.defaultBlendFunc();
                        if (seeThrough) {
                            RenderSystem.disableDepthTest();
                            RenderSystem.depthMask(false);
                        } else {
                            RenderSystem.enableDepthTest();
                            RenderSystem.depthFunc(org.lwjgl.opengl.GL11.GL_LEQUAL);
                            RenderSystem.depthMask(true);
                        }
                        RenderSystem.disableCull();
                    },
                    () -> {
                        RenderSystem.lineWidth(1.0F);
                        RenderSystem.depthMask(true);
                        RenderSystem.enableDepthTest();
                        RenderSystem.enableCull();
                        RenderSystem.disableBlend();
                    }
            ) {};
        }
    }
}
