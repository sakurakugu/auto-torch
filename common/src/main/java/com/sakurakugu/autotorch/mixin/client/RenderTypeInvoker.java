package com.sakurakugu.autotorch.mixin.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** 访问新版 RenderType 的包级工厂方法。 */
@Mixin(RenderType.class)
public abstract class RenderTypeInvoker extends RenderType {
    private RenderTypeInvoker() {
        super("autotorch_internal", DefaultVertexFormat.POSITION_COLOR_NORMAL, VertexFormat.Mode.LINES,
                1, false, false, () -> { }, () -> { });
    }

    @Invoker("create")
    public static RenderType autotorch$create(String name, VertexFormat format, VertexFormat.Mode mode,
                                               int bufferSize, boolean affectsCrumbling, boolean sortOnUpload,
                                               CompositeState state) {
        throw new AssertionError();
    }
}
