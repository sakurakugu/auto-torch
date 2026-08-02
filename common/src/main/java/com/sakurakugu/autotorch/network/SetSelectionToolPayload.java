package com.sakurakugu.autotorch.network;

import com.sakurakugu.autotorch.AutoTorch;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

/** 将客户端的木斧选区交互开关同步给服务端。 */
public record SetSelectionToolPayload(boolean enabled) implements AutoTorchPayload {
    public static final ResourceLocation ID = new ResourceLocation(AutoTorch.MOD_ID, "set_selection_tool");

    public static SetSelectionToolPayload decode(FriendlyByteBuf buffer) {
        return new SetSelectionToolPayload(buffer.readBoolean());
    }

    @Override
    public void write(FriendlyByteBuf buffer) {
        buffer.writeBoolean(enabled);
    }

    @Override
    public ResourceLocation id() {
        return ID;
    }
}
