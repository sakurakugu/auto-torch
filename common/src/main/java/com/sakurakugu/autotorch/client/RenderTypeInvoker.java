package com.sakurakugu.autotorch.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.minecraft.client.renderer.RenderType;

/** 1.16 将 RenderType 构造器设为 protected，通过子类创建自定义固定管线类型。 */
public final class RenderTypeInvoker extends RenderType {
    private RenderTypeInvoker() {
        super("autotorch_internal", DefaultVertexFormat.POSITION_COLOR, 1, 1,
                false, false, () -> { }, () -> { });
    }

    public static RenderType autotorch$create(
            String name, com.mojang.blaze3d.vertex.VertexFormat format, int mode,
            int bufferSize, boolean affectsCrumbling, boolean sortOnUpload,
            Runnable setupState, Runnable clearState
    ) {
        return new RenderType(name, format, mode, bufferSize, affectsCrumbling, sortOnUpload,
                setupState, clearState) { };
    }
}
