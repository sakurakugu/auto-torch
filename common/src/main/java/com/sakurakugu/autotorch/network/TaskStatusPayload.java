package com.sakurakugu.autotorch.network;

import com.sakurakugu.autotorch.AutoTorch;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** 服务端返回给客户端的照明任务进度快照。 */
public record TaskStatusPayload(boolean running, int percent, int placed) implements CustomPacketPayload {
    public static final Type<TaskStatusPayload> TYPE = new Type<>(
            ResourceLocation.tryBuild(AutoTorch.MOD_ID, "task_status"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TaskStatusPayload> STREAM_CODEC =
            CustomPacketPayload.codec(TaskStatusPayload::write, TaskStatusPayload::new);

    private TaskStatusPayload(RegistryFriendlyByteBuf buffer) {
        this(buffer.readBoolean(), buffer.readVarInt(), buffer.readVarInt());
    }
    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeBoolean(running);
        buffer.writeVarInt(percent);
        buffer.writeVarInt(placed);
    }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
