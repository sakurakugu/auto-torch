package com.sakurakugu.autotorch.network;

import com.sakurakugu.autotorch.AutoTorch;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

/** 客户端请求取消自己当前照明任务的无数据载荷。 */
public record CancelLightingPayload() implements AutoTorchPayload {
    public static final ResourceLocation ID = new ResourceLocation(AutoTorch.MOD_ID, "cancel_lighting");

    public static CancelLightingPayload decode(FriendlyByteBuf ignored) {
        return new CancelLightingPayload();
    }

    @Override
    public void write(FriendlyByteBuf ignored) {
    }

    @Override
    public ResourceLocation id() {
        return ID;
    }
}
