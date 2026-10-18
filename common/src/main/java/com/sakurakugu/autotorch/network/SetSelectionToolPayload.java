package com.sakurakugu.autotorch.network;

import com.sakurakugu.autotorch.AutoTorch;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

/** 将客户端的选区工具交互开关同步给服务端。 */
public record SetSelectionToolPayload(boolean enabled, String toolItem) implements AutoTorchPayload {
    public static final ResourceLocation ID = ResourceLocation.tryBuild(AutoTorch.MOD_ID, "set_selection_tool");

    public static SetSelectionToolPayload decode(FriendlyByteBuf buffer) {
        return new SetSelectionToolPayload(buffer);
    }

    private SetSelectionToolPayload(FriendlyByteBuf buffer) {
        this(buffer.readBoolean(), buffer.readUtf(256));
    }

    public void write(FriendlyByteBuf buffer) {
        buffer.writeBoolean(enabled);
        buffer.writeUtf(toolItem, 256);
    }

    @Override
    public ResourceLocation id() {
        return ID;
    }
}
