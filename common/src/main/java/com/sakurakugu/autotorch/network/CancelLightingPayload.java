package com.sakurakugu.autotorch.network;

import com.sakurakugu.autotorch.AutoTorch;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** 客户端请求取消自己当前照明任务的无数据载荷。 */
public record CancelLightingPayload() implements CustomPacketPayload {
    public static final Type<CancelLightingPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(AutoTorch.MOD_ID, "cancel_lighting")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, CancelLightingPayload> STREAM_CODEC =
            CustomPacketPayload.codec(CancelLightingPayload::write, CancelLightingPayload::new);

    private CancelLightingPayload(RegistryFriendlyByteBuf ignored) {
        this();
    }

    private void write(RegistryFriendlyByteBuf ignored) {
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
