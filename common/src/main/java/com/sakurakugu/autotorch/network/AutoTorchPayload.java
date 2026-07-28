package com.sakurakugu.autotorch.network;

import net.minecraft.network.PacketBuffer;
import net.minecraft.util.ResourceLocation;

/** 兼容旧版加载器网络 API 的公共载荷约定。 */
public interface AutoTorchPayload {
    ResourceLocation id();

    void write(PacketBuffer buffer);
}
