package com.sakurakugu.autotorch.network;

import com.sakurakugu.autotorch.AutoTorch;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** 服务端同步世界种子，使客户端能够准确判断地下史莱姆区块。 */
public record WorldSeedPayload(long seed) implements CustomPacketPayload {
    public static final Type<WorldSeedPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(AutoTorch.MOD_ID, "world_seed"));
    public static final StreamCodec<RegistryFriendlyByteBuf, WorldSeedPayload> STREAM_CODEC =
            CustomPacketPayload.codec(WorldSeedPayload::write, WorldSeedPayload::new);

    private WorldSeedPayload(RegistryFriendlyByteBuf buffer) {
        this(buffer.readLong());
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeLong(seed);
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
