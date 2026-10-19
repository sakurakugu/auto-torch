package com.sakurakugu.autotorch.network;

import com.sakurakugu.autotorch.AutoTorch;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.ResourceLocation;

/** 服务端通知客户端打开管理员设置界面的无数据载荷。 */
public final class OpenServerConfigScreenPayload implements AutoTorchPayload  {
    public OpenServerConfigScreenPayload() {
    }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof OpenServerConfigScreenPayload)) return false;
        OpenServerConfigScreenPayload that = (OpenServerConfigScreenPayload) other;
        return true;
    }
    @Override public int hashCode() { return java.util.Objects.hash(); }

    public static final ResourceLocation ID = new ResourceLocation(AutoTorch.MOD_ID, "open_server_config");

    public static OpenServerConfigScreenPayload decode(PacketBuffer buffer) {
        return new OpenServerConfigScreenPayload(buffer);
    }

    private OpenServerConfigScreenPayload(PacketBuffer ignored) { this(); }
    public void write(PacketBuffer ignored) { }
    @Override public ResourceLocation id() { return ID; }
}
