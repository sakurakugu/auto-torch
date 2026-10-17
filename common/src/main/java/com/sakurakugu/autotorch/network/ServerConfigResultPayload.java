package com.sakurakugu.autotorch.network;

import com.sakurakugu.autotorch.AutoTorch;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * 服务端返回给管理员界面的操作结果。
 * messageKey 由客户端翻译，detail 是配置键、分组名或界面显示用的范围说明。
 */
public record ServerConfigResultPayload(boolean success, String messageKey, String detail)
        implements CustomPacketPayload {
    public static final Type<ServerConfigResultPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(AutoTorch.MOD_ID, "server_config_result")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, ServerConfigResultPayload> STREAM_CODEC =
            CustomPacketPayload.codec(ServerConfigResultPayload::write, ServerConfigResultPayload::new);

    private ServerConfigResultPayload(RegistryFriendlyByteBuf buffer) {
        this(buffer.readBoolean(), buffer.readUtf(), buffer.readUtf());
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeBoolean(success);
        buffer.writeUtf(messageKey);
        buffer.writeUtf(detail);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
