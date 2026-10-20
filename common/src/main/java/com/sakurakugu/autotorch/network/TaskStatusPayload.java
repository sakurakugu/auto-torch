package com.sakurakugu.autotorch.network;

import com.sakurakugu.autotorch.AutoTorch;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

/** 服务端返回给客户端的照明任务进度快照。placementItem 是任务实际使用的放置物品注册 ID，没有任务时为空串。 */
public record TaskStatusPayload(boolean running, int percent, int placed, String placementItem)
        implements AutoTorchPayload {
    public static final ResourceLocation ID = new ResourceLocation(AutoTorch.MOD_ID, "task_status");

    public static TaskStatusPayload decode(FriendlyByteBuf buffer) {
        return new TaskStatusPayload(buffer.readBoolean(), buffer.readVarInt(), buffer.readVarInt(),
                buffer.readUtf(256));
    }

    @Override
    public void write(FriendlyByteBuf buffer) {
        buffer.writeBoolean(running);
        buffer.writeVarInt(percent);
        buffer.writeVarInt(placed);
        buffer.writeUtf(placementItem, 256);
    }

    @Override
    public ResourceLocation id() {
        return ID;
    }
}
