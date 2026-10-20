package com.sakurakugu.autotorch.network;

import com.sakurakugu.autotorch.AutoTorch;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.ResourceLocation;

/**
 * 服务端返回给管理员界面的操作结果。
 * messageKey 由客户端翻译，detail 是配置键、分组名或界面显示用的范围说明。
 */
public final class ServerConfigResultPayload
        implements AutoTorchPayload  {
    private final boolean success;
    private final String messageKey;
    private final String detail;
    public ServerConfigResultPayload(boolean success, String messageKey, String detail) {
        this.success = success;
        this.messageKey = messageKey;
        this.detail = detail;
    }
    public boolean success() { return success; }
    public String messageKey() { return messageKey; }
    public String detail() { return detail; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ServerConfigResultPayload)) return false;
        ServerConfigResultPayload that = (ServerConfigResultPayload) other;
        return java.util.Objects.equals(success, that.success) && java.util.Objects.equals(messageKey, that.messageKey) && java.util.Objects.equals(detail, that.detail);
    }
    @Override public int hashCode() { return java.util.Objects.hash(success, messageKey, detail); }

    public static final ResourceLocation ID = new ResourceLocation(AutoTorch.MOD_ID, "server_config_result");

    public static ServerConfigResultPayload decode(PacketBuffer buffer) {
        return new ServerConfigResultPayload(buffer);
    }

    private ServerConfigResultPayload(PacketBuffer buffer) {
        this(buffer.readBoolean(), PacketStrings.read(buffer, 32767), PacketStrings.read(buffer, 32767));
    }

    public void write(PacketBuffer buffer) {
        buffer.writeBoolean(success);
        PacketStrings.write(buffer, messageKey);
        PacketStrings.write(buffer, detail);
    }

    @Override
    public ResourceLocation id() {
        return ID;
    }
}
