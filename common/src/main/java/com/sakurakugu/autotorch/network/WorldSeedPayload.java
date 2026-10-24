package com.sakurakugu.autotorch.network;

import com.sakurakugu.autotorch.AutoTorch;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.ResourceLocation;

/** 服务端登录时同步世界种子，供客户端复现史莱姆区块判定。 */
public final class WorldSeedPayload implements AutoTorchPayload {
    public static final ResourceLocation ID = new ResourceLocation(AutoTorch.MOD_ID, "world_seed");
    private final long seed;

    public WorldSeedPayload(long seed) {
        this.seed = seed;
    }

    public long seed() { return seed; }

    public static WorldSeedPayload decode(PacketBuffer buffer) {
        return new WorldSeedPayload(buffer.readLong());
    }

    @Override
    public void write(PacketBuffer buffer) {
        buffer.writeLong(seed);
    }

    @Override
    public ResourceLocation id() {
        return ID;
    }
}
