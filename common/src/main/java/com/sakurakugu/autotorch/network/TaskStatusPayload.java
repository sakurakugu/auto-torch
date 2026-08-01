package com.sakurakugu.autotorch.network;

import com.sakurakugu.autotorch.AutoTorch;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

/** 服务端返回给客户端的照明任务进度快照。 */
public record TaskStatusPayload(boolean running, int percent, int placed) implements AutoTorchPayload {
    public static final ResourceLocation ID = ResourceLocation.tryBuild(AutoTorch.MOD_ID, "task_status");

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
