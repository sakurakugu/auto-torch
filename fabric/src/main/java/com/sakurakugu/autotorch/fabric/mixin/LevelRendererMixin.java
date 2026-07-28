package com.sakurakugu.autotorch.fabric.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Matrix4f;
import com.sakurakugu.autotorch.fabric.AutoTorchFabricClient;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
abstract class LevelRendererMixin {
    @Inject(method = "renderLevel", at = @At("TAIL"))
    private void autotorch$renderWorld(PoseStack poseStack, float partialTick, long finishTimeNano,
            boolean renderBlockOutline, Camera camera, GameRenderer gameRenderer,
            LightTexture lightTexture, Matrix4f projectionMatrix, CallbackInfo callbackInfo) {
        AutoTorchFabricClient.renderWorld(poseStack, camera);
    }
}
