package com.sakurakugu.autotorch.network;

import com.sakurakugu.autotorch.AutoTorch;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** 将客户端的木斧选区交互开关同步给服务端。 */
public record SetSelectionToolPayload(boolean enabled) implements CustomPacketPayload {
    public static final Type<SetSelectionToolPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(AutoTorch.MOD_ID, "set_selection_tool")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, SetSelectionToolPayload> STREAM_CODEC =
            CustomPacketPayload.codec(SetSelectionToolPayload::write, SetSelectionToolPayload::new);

    private SetSelectionToolPayload(RegistryFriendlyByteBuf buffer) {
        this(buffer.readBoolean());
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeBoolean(enabled);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
