package com.sakurakugu.autotorch.network;

import com.sakurakugu.autotorch.AutoTorch;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** 服务端通知客户端打开管理员设置界面的无数据载荷。 */
public record OpenServerConfigScreenPayload() implements CustomPacketPayload {
    public static final Type<OpenServerConfigScreenPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(AutoTorch.MOD_ID, "open_server_config")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenServerConfigScreenPayload> STREAM_CODEC =
            CustomPacketPayload.codec(OpenServerConfigScreenPayload::write, OpenServerConfigScreenPayload::new);

    private OpenServerConfigScreenPayload(RegistryFriendlyByteBuf ignored) { this(); }
    private void write(RegistryFriendlyByteBuf ignored) { }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
