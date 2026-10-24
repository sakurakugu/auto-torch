package com.sakurakugu.autotorch.network;

import com.sakurakugu.autotorch.AutoTorch;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

/** 服务端向客户端同步当前世界种子，用于判断地下史莱姆区块。 */
public final class WorldSeedPayload implements AutoTorchPayload {
    public static final ResourceLocation ID = new ResourceLocation(AutoTorch.MOD_ID, "world_seed");
    private final long seed;

    public WorldSeedPayload(long seed) {
        this.seed = seed;
    }

    public long seed() {
        return seed;
    }

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
