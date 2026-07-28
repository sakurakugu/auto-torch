package com.sakurakugu.autotorch.network;

import com.sakurakugu.autotorch.AutoTorch;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.ResourceLocation;

/** 客户端请求取消自己当前照明任务的无数据载荷。 */
public final class CancelLightingPayload implements AutoTorchPayload {
    public static final ResourceLocation ID = new ResourceLocation(AutoTorch.MOD_ID + ":cancel_lighting");

    public static CancelLightingPayload decode(PacketBuffer ignored) {
        return new CancelLightingPayload();
    }

    @Override
    public void write(PacketBuffer ignored) {
    }

    @Override
    public ResourceLocation id() {
        return ID;
    }
}
