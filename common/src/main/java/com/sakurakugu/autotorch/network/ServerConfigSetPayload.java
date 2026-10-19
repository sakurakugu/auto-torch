package com.sakurakugu.autotorch.network;

import com.sakurakugu.autotorch.AutoTorch;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.ResourceLocation;

/** 客户端请求修改一个服务端配置项，值使用与命令一致的文本格式。 */
public final class ServerConfigSetPayload implements AutoTorchPayload  {
    private final String key;
    private final String value;
    public ServerConfigSetPayload(String key, String value) {
        this.key = key;
        this.value = value;
    }
    public String key() { return key; }
    public String value() { return value; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ServerConfigSetPayload)) return false;
        ServerConfigSetPayload that = (ServerConfigSetPayload) other;
        return java.util.Objects.equals(key, that.key) && java.util.Objects.equals(value, that.value);
    }
    @Override public int hashCode() { return java.util.Objects.hash(key, value); }

    public static final ResourceLocation ID = new ResourceLocation(AutoTorch.MOD_ID, "server_config_set");

    public static ServerConfigSetPayload decode(PacketBuffer buffer) {
        return new ServerConfigSetPayload(buffer);
    }

    private ServerConfigSetPayload(PacketBuffer buffer) {
        this(buffer.readString(32767), buffer.readString(32767));
    }

    public void write(PacketBuffer buffer) {
        buffer.writeString(key);
        buffer.writeString(value);
    }

    @Override
    public ResourceLocation id() {
        return ID;
    }
}
