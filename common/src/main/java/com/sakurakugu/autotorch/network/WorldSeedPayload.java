package com.sakurakugu.autotorch.network;

import com.sakurakugu.autotorch.AutoTorch;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

/** 服务端向客户端同步当前世界种子。 */
public record WorldSeedPayload(long seed) implements AutoTorchPayload {
    public static final ResourceLocation ID =
            ResourceLocation.tryBuild(AutoTorch.MOD_ID, "world_seed");

    public static WorldSeedPayload decode(FriendlyByteBuf buffer) {
        return new WorldSeedPayload(buffer.readLong());
    }

    @Override
    public void write(FriendlyByteBuf buffer) {
        buffer.writeLong(seed);
    }

    @Override
    public ResourceLocation id() {
        return ID;
    }
}
