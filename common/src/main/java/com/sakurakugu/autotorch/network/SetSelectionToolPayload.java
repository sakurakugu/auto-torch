package com.sakurakugu.autotorch.network;

import com.sakurakugu.autotorch.AutoTorch;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.ResourceLocation;

/** 将客户端的木斧选区交互开关同步给服务端。 */
public final class SetSelectionToolPayload implements AutoTorchPayload {
    public static final ResourceLocation ID = new ResourceLocation(AutoTorch.MOD_ID + ":set_selection_tool");
    private final boolean enabled;
    private final String itemId;

    public SetSelectionToolPayload(boolean enabled) { this(enabled, "minecraft:wooden_axe"); }

    public SetSelectionToolPayload(boolean enabled, String itemId) {
        this.enabled = enabled;
        this.itemId = itemId;
    }

    public boolean enabled() { return enabled; }
    public String itemId() { return itemId; }

    public static SetSelectionToolPayload decode(PacketBuffer buffer) {
        return new SetSelectionToolPayload(buffer.readBoolean(), buffer.readString(256));
    }

    @Override
    public void write(PacketBuffer buffer) {
        buffer.writeBoolean(enabled);
        // 1.11.2 的 PacketBuffer 只有 writeString(String)，长度上限由 readString(maxLength) 在读取侧限制。
        buffer.writeString(itemId);
    }

    @Override
    public ResourceLocation id() {
        return ID;
    }
}
