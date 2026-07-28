package com.sakurakugu.autotorch.network;

import com.sakurakugu.autotorch.AutoTorch;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.ResourceLocation;

/** 将客户端的木斧选区交互开关同步给服务端。 */
public final class SetSelectionToolPayload implements AutoTorchPayload {
    public static final ResourceLocation ID = new ResourceLocation(AutoTorch.MOD_ID + ":set_selection_tool");
    private final boolean enabled;

    public SetSelectionToolPayload(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean enabled() {
        return enabled;
    }

    public static SetSelectionToolPayload decode(PacketBuffer buffer) {
        return new SetSelectionToolPayload(buffer.readBoolean());
    }

    @Override
    public void write(PacketBuffer buffer) {
        buffer.writeBoolean(enabled);
    }

    @Override
    public ResourceLocation id() {
        return ID;
    }
}
