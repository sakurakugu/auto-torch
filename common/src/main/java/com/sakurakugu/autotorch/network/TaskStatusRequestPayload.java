package com.sakurakugu.autotorch.network;

import com.sakurakugu.autotorch.AutoTorch;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** 客户端请求自己当前照明任务状态的无数据载荷。 */
public record TaskStatusRequestPayload() implements CustomPacketPayload {
    public static final Type<TaskStatusRequestPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(AutoTorch.MOD_ID, "task_status_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TaskStatusRequestPayload> STREAM_CODEC =
            CustomPacketPayload.codec(TaskStatusRequestPayload::write, TaskStatusRequestPayload::new);

    private TaskStatusRequestPayload(RegistryFriendlyByteBuf ignored) { this(); }
    private void write(RegistryFriendlyByteBuf ignored) { }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
