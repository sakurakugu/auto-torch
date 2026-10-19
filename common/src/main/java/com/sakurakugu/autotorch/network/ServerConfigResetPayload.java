package com.sakurakugu.autotorch.network;

import com.sakurakugu.autotorch.AutoTorch;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.ResourceLocation;

/**
 * 客户端请求恢复服务端配置默认值；scope 为空表示全部，
 * 分组枚举名表示一个分组，配置键表示单项，也兼容 TOML 配置节前缀。
 */
public final class ServerConfigResetPayload implements AutoTorchPayload  {
    private final String scope;
    public ServerConfigResetPayload(String scope) {
        this.scope = scope;
    }
    public String scope() { return scope; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ServerConfigResetPayload)) return false;
        ServerConfigResetPayload that = (ServerConfigResetPayload) other;
        return java.util.Objects.equals(scope, that.scope);
    }
    @Override public int hashCode() { return java.util.Objects.hash(scope); }

    public static final ResourceLocation ID = new ResourceLocation(AutoTorch.MOD_ID, "server_config_reset");

    public static ServerConfigResetPayload decode(PacketBuffer buffer) {
        return new ServerConfigResetPayload(buffer);
    }

    private ServerConfigResetPayload(PacketBuffer buffer) {
        this(buffer.readString(32767));
    }

    public void write(PacketBuffer buffer) {
        buffer.writeString(scope);
    }

    @Override
    public ResourceLocation id() {
        return ID;
    }
}
