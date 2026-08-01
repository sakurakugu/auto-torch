package com.sakurakugu.autotorch.fabric.mixin;

import com.sakurakugu.autotorch.client.AutoTorchClientCommands;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LocalPlayer.class)
abstract class LocalPlayerMixin {
    @Inject(method = "chat", at = @At("HEAD"), cancellable = true)
    private void autotorch$executeClientCommand(String message, CallbackInfo callbackInfo) {
        if (AutoTorchClientCommands.tryExecute(message)) {
            callbackInfo.cancel();
        }
    }
}
