package com.sakurakugu.autotorch.network;

import com.sakurakugu.autotorch.AutoTorch;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

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

    public static SetSelectionToolPayload decode(FriendlyByteBuf buffer) {
        return new SetSelectionToolPayload(buffer.readBoolean(), buffer.readUtf(256));
    }

    @Override
    public void write(FriendlyByteBuf buffer) {
        buffer.writeBoolean(enabled);
        buffer.writeUtf(itemId, 256);
    }

    @Override
    public ResourceLocation id() {
        return ID;
    }
}

