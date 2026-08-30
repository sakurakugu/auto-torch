package com.sakurakugu.autotorch.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.sakurakugu.autotorch.mixin.client.RenderTypeInvoker;
import net.minecraft.client.renderer.RenderType;

/** 创建透视线框使用的无深度测试渲染类型。 */
public final class AutoTorchRenderTypes {
    private static final RenderType SEE_THROUGH_LINES = RenderTypeAccess.createSeeThroughLines();

    private AutoTorchRenderTypes() {
    }

    public static RenderType seeThroughLines() {
        return SEE_THROUGH_LINES;
    }

    /** 旧版本将复合渲染状态及其预设声明为 protected，只能经由子类访问。 */
    private static final class RenderTypeAccess extends RenderType {
        private RenderTypeAccess() {
            super("autotorch_internal", DefaultVertexFormat.POSITION_COLOR_NORMAL, VertexFormat.Mode.LINES,
                    1, false, false, () -> { }, () -> { });
        }

        private static RenderType createSeeThroughLines() {
            return RenderTypeInvoker.autotorch$create("autotorch_light_overlay_see_through_lines",
                    DefaultVertexFormat.POSITION_COLOR_NORMAL, VertexFormat.Mode.LINES, 1536, false, false,
                    CompositeState.builder().setShaderState(RENDERTYPE_LINES_SHADER).setLineState(DEFAULT_LINE)
                            .setTransparencyState(TRANSLUCENT_TRANSPARENCY).setDepthTestState(NO_DEPTH_TEST)
                            .setCullState(NO_CULL).setWriteMaskState(COLOR_WRITE).createCompositeState(false));
        }
    }
}
