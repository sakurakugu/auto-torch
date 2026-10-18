package com.sakurakugu.autotorch.network;

import com.sakurakugu.autotorch.AutoTorch;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

/**
 * 服务端返回给管理员界面的操作结果。
 * messageKey 由客户端翻译，detail 是配置键、分组名或界面显示用的范围说明。
 */
public record ServerConfigResultPayload(boolean success, String messageKey, String detail)
        implements AutoTorchPayload {
    public static final ResourceLocation ID = ResourceLocation.tryBuild(AutoTorch.MOD_ID, "server_config_result");

    public static ServerConfigResultPayload decode(FriendlyByteBuf buffer) {
        return new ServerConfigResultPayload(buffer);
    }

    private ServerConfigResultPayload(FriendlyByteBuf buffer) {
        this(buffer.readBoolean(), buffer.readUtf(), buffer.readUtf());
    }

    public void write(FriendlyByteBuf buffer) {
        buffer.writeBoolean(success);
        buffer.writeUtf(messageKey);
        buffer.writeUtf(detail);
    }

    @Override
    public ResourceLocation id() {
        return ID;
    }
}
