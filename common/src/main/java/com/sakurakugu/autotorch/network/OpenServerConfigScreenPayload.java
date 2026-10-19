package com.sakurakugu.autotorch.network;

import com.sakurakugu.autotorch.AutoTorch;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

/** 服务端通知客户端打开管理员设置界面的无数据载荷。 */
public record OpenServerConfigScreenPayload() implements AutoTorchPayload {
    public static final ResourceLocation ID = new ResourceLocation(AutoTorch.MOD_ID, "open_server_config");

    public static OpenServerConfigScreenPayload decode(FriendlyByteBuf buffer) {
        return new OpenServerConfigScreenPayload(buffer);
    }

    private OpenServerConfigScreenPayload(FriendlyByteBuf ignored) { this(); }
    public void write(FriendlyByteBuf ignored) { }
    @Override public ResourceLocation id() { return ID; }
}
