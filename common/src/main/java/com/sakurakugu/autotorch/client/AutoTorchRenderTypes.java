package com.sakurakugu.autotorch.client;

import java.util.Optional;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderPipeline.Snippet;
import com.mojang.blaze3d.platform.DepthTestFunction;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import com.sakurakugu.autotorch.mixin.client.RenderTypeInvoker;

/** 创建透视线框使用的无深度测试渲染类型。 */
public final class AutoTorchRenderTypes {
    private static final RenderType SEE_THROUGH_LINES = createSeeThroughLines();
    private AutoTorchRenderTypes() { }
    public static RenderType seeThroughLines() { return SEE_THROUGH_LINES; }
    private static RenderType createSeeThroughLines() {
        RenderPipeline source = RenderPipelines.LINES;
        Snippet snippet = new Snippet(Optional.of(source.getVertexShader()), Optional.of(source.getFragmentShader()),
                Optional.of(source.getShaderDefines()), Optional.of(source.getSamplers()),
                Optional.of(source.getUniforms()), source.getBlendFunction(), Optional.of(DepthTestFunction.NO_DEPTH_TEST),
                Optional.of(source.getPolygonMode()), Optional.of(source.isCull()), Optional.of(source.isWriteColor()),
                Optional.of(source.isWriteAlpha()), Optional.of(false), Optional.of(source.getColorLogic()),
                Optional.of(source.getVertexFormat()), Optional.of(source.getVertexFormatMode()));
        RenderPipeline pipeline = RenderPipeline.builder(snippet)
                .withLocation(Identifier.fromNamespaceAndPath("autotorch", "light_overlay_see_through_lines")).build();
        return RenderTypeInvoker.autotorch$create("autotorch:light_overlay_see_through_lines",
                RenderSetup.builder(pipeline).createRenderSetup());
    }
}
