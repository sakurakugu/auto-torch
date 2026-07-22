package com.sakurakugu.autotorch.network;

import com.sakurakugu.autotorch.AutoTorch;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** 服务端在玩家登录后同步会影响客户端显示的权威配置。 */
public record ServerConfigPayload(boolean survivalConsumesTorches) implements CustomPacketPayload {
    public static final Type<ServerConfigPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(AutoTorch.MOD_ID, "server_config")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, ServerConfigPayload> STREAM_CODEC =
            CustomPacketPayload.codec(ServerConfigPayload::write, ServerConfigPayload::new);

    private ServerConfigPayload(RegistryFriendlyByteBuf buffer) {
        this(buffer.readBoolean());
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeBoolean(survivalConsumesTorches);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
