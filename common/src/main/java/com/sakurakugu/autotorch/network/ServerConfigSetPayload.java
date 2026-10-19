package com.sakurakugu.autotorch.network;

import com.sakurakugu.autotorch.AutoTorch;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

/** 客户端请求修改一个服务端配置项，值使用与命令一致的文本格式。 */
public record ServerConfigSetPayload(String key, String value) implements AutoTorchPayload {
    public static final ResourceLocation ID = new ResourceLocation(AutoTorch.MOD_ID, "server_config_set");

    public static ServerConfigSetPayload decode(FriendlyByteBuf buffer) {
        return new ServerConfigSetPayload(buffer);
    }

    private ServerConfigSetPayload(FriendlyByteBuf buffer) {
        this(buffer.readUtf(), buffer.readUtf());
    }

    public void write(FriendlyByteBuf buffer) {
        buffer.writeUtf(key);
        buffer.writeUtf(value);
    }

    @Override
    public ResourceLocation id() {
        return ID;
    }
}
