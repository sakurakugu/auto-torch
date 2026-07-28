package com.sakurakugu.autotorch.fabric.mixin;

import com.sakurakugu.autotorch.fabric.AutoTorchFabricClient;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
abstract class LevelRendererMixin {
    @Inject(method = "render(FJ)V",
            at = @At(value = "CONSTANT", args = "stringValue=hand"))
    private void autotorch$renderWorld(float partialTick, long finishTimeNano, CallbackInfo callbackInfo) {
        AutoTorchFabricClient.renderWorld(((GameRenderer) (Object) this).getMainCamera());
    }
}
