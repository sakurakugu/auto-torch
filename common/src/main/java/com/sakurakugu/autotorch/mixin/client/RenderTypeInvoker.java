package com.sakurakugu.autotorch.mixin.client;

import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** 访问新版 RenderType 的包级工厂方法。 */
@Mixin(RenderType.class)
public interface RenderTypeInvoker {
    @Invoker("create")
    static RenderType autotorch$create(String name, RenderSetup setup) {
        throw new AssertionError();
    }
}
