package com.sakurakugu.autotorch.network;

import com.sakurakugu.autotorch.AutoTorch;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

/** 服务端返回给客户端的照明任务进度快照。 */
public record TaskStatusPayload(boolean running, int percent, int placed) implements AutoTorchPayload {
    // 该构造器在 1.21 起标记为待删除，但 common 源码还要给 fabric 编译
    // （原版映射没有 fromNamespaceAndPath/parse 等工厂方法），只能保留构造器写法；
    // forge/neoforge 复用同一份源码时会产生 [removal] 告警，这里按最小范围抑制。
    @SuppressWarnings("removal")
    public static final ResourceLocation ID = new ResourceLocation(AutoTorch.MOD_ID, "task_status");

    public static TaskStatusPayload decode(FriendlyByteBuf buffer) {
        return new TaskStatusPayload(buffer.readBoolean(), buffer.readVarInt(), buffer.readVarInt());
    }

    @Override
    public void write(FriendlyByteBuf buffer) {
        buffer.writeBoolean(running);
        buffer.writeVarInt(percent);
        buffer.writeVarInt(placed);
    }

    @Override
    public ResourceLocation id() {
        return ID;
    }
}
