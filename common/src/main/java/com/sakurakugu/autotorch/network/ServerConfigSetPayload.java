package com.sakurakugu.autotorch.network;

import com.sakurakugu.autotorch.AutoTorch;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** 客户端请求修改一个服务端配置项，值使用与命令一致的文本格式。 */
public record ServerConfigSetPayload(String key, String value) implements CustomPacketPayload {
    public static final Type<ServerConfigSetPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(AutoTorch.MOD_ID, "server_config_set")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, ServerConfigSetPayload> STREAM_CODEC =
            CustomPacketPayload.codec(ServerConfigSetPayload::write, ServerConfigSetPayload::new);

    private ServerConfigSetPayload(RegistryFriendlyByteBuf buffer) {
        this(buffer.readUtf(), buffer.readUtf());
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeUtf(key);
        buffer.writeUtf(value);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
