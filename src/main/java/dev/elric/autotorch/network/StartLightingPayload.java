package dev.elric.autotorch.network;

import dev.elric.autotorch.AutoTorchMod;
import io.netty.handler.codec.DecoderException;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** 客户端提交的照明任务配置；服务端收到后仍需进行完整的边界校验。 */
public record StartLightingPayload(
        BlockPos first,
        BlockPos second,
        int maxTorches,
        int minSpacing,
        boolean consumeTorches,
        boolean undergroundOnly,
        List<ExclusionZone> exclusions
) implements CustomPacketPayload {
    public static final int MAX_EXCLUSIONS = 32;
    public static final Type<StartLightingPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(AutoTorchMod.MOD_ID, "start_lighting")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, StartLightingPayload> STREAM_CODEC =
            CustomPacketPayload.codec(StartLightingPayload::write, StartLightingPayload::new);

    private StartLightingPayload(RegistryFriendlyByteBuf buffer) {
        this(
                buffer.readBlockPos(),
                buffer.readBlockPos(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readBoolean(),
                buffer.readBoolean(),
                readExclusions(buffer)
        );
    }

    public StartLightingPayload {
        // 固化坐标与列表，避免编码或异步处理期间调用方继续修改数据。
        first = first.immutable();
        second = second.immutable();
        exclusions = List.copyOf(exclusions);
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeBlockPos(first);
        buffer.writeBlockPos(second);
        buffer.writeVarInt(maxTorches);
        buffer.writeVarInt(minSpacing);
        buffer.writeBoolean(consumeTorches);
        buffer.writeBoolean(undergroundOnly);
        buffer.writeVarInt(exclusions.size());
        for (ExclusionZone exclusion : exclusions) {
            buffer.writeBlockPos(exclusion.center());
            buffer.writeVarInt(exclusion.radius());
        }
    }

    private static List<ExclusionZone> readExclusions(RegistryFriendlyByteBuf buffer) {
        int count = buffer.readVarInt();
        // 在分配列表前限制数量，防止恶意数据包造成过量内存分配。
        if (count < 0 || count > MAX_EXCLUSIONS) {
            throw new DecoderException("Invalid Auto Torch exclusion count: " + count);
        }
        List<ExclusionZone> exclusions = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            exclusions.add(new ExclusionZone(buffer.readBlockPos(), buffer.readVarInt()));
        }
        return exclusions;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
