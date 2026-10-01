package com.sakurakugu.autotorch.network;

import com.sakurakugu.autotorch.AutoTorch;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

/** 将客户端的木斧选区交互开关同步给服务端。 */
public record SetSelectionToolPayload(boolean enabled) implements AutoTorchPayload {
    // 该构造器在 1.21 起标记为待删除，但 common 源码还要给 fabric 编译
    // （原版映射没有 fromNamespaceAndPath/parse 等工厂方法），只能保留构造器写法；
    // forge/neoforge 复用同一份源码时会产生 [removal] 告警，这里按最小范围抑制。
    @SuppressWarnings("removal")
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
