package com.sakurakugu.autotorch.client;

import java.util.Optional;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderPipeline.Snippet;
import com.mojang.blaze3d.platform.CompareOp;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;

/** 创建透视线框使用的无深度测试渲染类型。 */
public final class AutoTorchRenderTypes {
    private static final RenderType SEE_THROUGH_LINES = createSeeThroughLines();
    private AutoTorchRenderTypes() { }
    public static RenderType seeThroughLines() { return SEE_THROUGH_LINES; }
    private static RenderType createSeeThroughLines() {
        RenderPipeline source = RenderPipelines.LINES;
        Snippet snippet = new Snippet(Optional.of(source.getVertexShader()), Optional.of(source.getFragmentShader()),
                Optional.of(source.getShaderDefines()), Optional.of(source.getBindGroupLayouts()),
                source.getColorTargetStates(), source.getColorTargetStates().length,
                Optional.ofNullable(source.getDepthStencilState()), Optional.of(source.getPolygonMode()),
                Optional.of(source.isCull()), source.getVertexFormatBindings(), Optional.of(source.getPrimitiveTopology()));
        RenderPipeline pipeline = RenderPipeline.builder(snippet)
                .withLocation(Identifier.fromNamespaceAndPath("autotorch", "light_overlay_see_through_lines"))
                .withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, false)).build();
        RenderSetup setup = RenderSetup.builder(pipeline).createRenderSetup();
        try {
            Method create = findCreateMethod();
            return (RenderType) create.invoke(null, "autotorch:light_overlay_see_through_lines", setup);
        } catch (ReflectiveOperationException | SecurityException exception) {
            // 某些加载器会在 RenderType 加载后才初始化 Mixin，反射失败时退回原版线框，避免客户端崩溃。
            System.err.println("Auto Torch 无法创建透视渲染类型，将使用普通线框: " + exception);
            return RenderTypes.lines();
        }
    }

    private static Method findCreateMethod() throws NoSuchMethodException {
        for (Method method : RenderType.class.getDeclaredMethods()) {
            if (Modifier.isStatic(method.getModifiers())
                    && method.getReturnType() == RenderType.class
                    && method.getParameterCount() == 2
                    && method.getParameterTypes()[0] == String.class
                    && method.getParameterTypes()[1] == RenderSetup.class) {
                method.setAccessible(true);
                return method;
            }
        }
        throw new NoSuchMethodException("RenderType(String, RenderSetup) factory");
    }
}
