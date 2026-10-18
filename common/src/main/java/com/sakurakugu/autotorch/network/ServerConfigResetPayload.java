package com.sakurakugu.autotorch.network;

import com.sakurakugu.autotorch.AutoTorch;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * 客户端请求恢复服务端配置默认值；scope 为空表示全部，
 * 分组枚举名表示一个分组，配置键表示单项，也兼容 TOML 配置节前缀。
 */
public record ServerConfigResetPayload(String scope) implements CustomPacketPayload {
    public static final Type<ServerConfigResetPayload> TYPE = new Type<>(
            ResourceLocation.tryBuild(AutoTorch.MOD_ID, "server_config_reset")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, ServerConfigResetPayload> STREAM_CODEC =
            CustomPacketPayload.codec(ServerConfigResetPayload::write, ServerConfigResetPayload::new);

    private ServerConfigResetPayload(RegistryFriendlyByteBuf buffer) {
        this(buffer.readUtf());
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeUtf(scope);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
