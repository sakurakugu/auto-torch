package com.sakurakugu.autotorch.network;

import java.util.ArrayList;
import java.util.List;

import com.sakurakugu.autotorch.AutoTorch;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

/** 客户端提交的照明任务配置；服务端收到后仍需进行完整的边界校验。 */
public record StartLightingPayload(
        AreaZone selection,
        int maxTorches,
        int minSpacing,
        int lightThreshold,
        boolean consumeTorches,
        boolean undergroundOnly,
        List<AreaZone> exclusions
) implements AutoTorchPayload {
    public static final int MAX_EXCLUSIONS = 32;
    public static final ResourceLocation ID = ResourceLocation.tryBuild(AutoTorch.MOD_ID, "start_lighting");

    public static StartLightingPayload decode(FriendlyByteBuf buffer) {
        return new StartLightingPayload(
                readZone(buffer),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readBoolean(),
                buffer.readBoolean(),
                readExclusions(buffer)
        );
    }

    public StartLightingPayload {
        // 固化坐标与列表，避免编码或异步处理期间调用方继续修改数据。
        exclusions = List.copyOf(exclusions);
    }

    @Override
    public void write(FriendlyByteBuf buffer) {
        writeZone(buffer, selection);
        buffer.writeVarInt(maxTorches);
        buffer.writeVarInt(minSpacing);
        buffer.writeVarInt(lightThreshold);
        buffer.writeBoolean(consumeTorches);
        buffer.writeBoolean(undergroundOnly);
        buffer.writeVarInt(exclusions.size());
        for (AreaZone exclusion : exclusions) {
            writeZone(buffer, exclusion);
        }
    }

    private static void writeZone(FriendlyByteBuf buffer, AreaZone zone) {
        buffer.writeByte(zone.shape().ordinal());
        buffer.writeBlockPos(zone.first());
        buffer.writeBlockPos(zone.second());
    }

    private static AreaZone readZone(FriendlyByteBuf buffer) {
        int shapeId = buffer.readUnsignedByte();
        AreaShape[] shapes = AreaShape.values();
        if (shapeId >= shapes.length) {
            throw new DecoderException("Invalid Auto Torch area shape: " + shapeId);
        }
        return new AreaZone(shapes[shapeId], buffer.readBlockPos(), buffer.readBlockPos());
    }

    private static List<AreaZone> readExclusions(FriendlyByteBuf buffer) {
        int count = buffer.readVarInt();
        // 在分配列表前限制数量，防止恶意数据包造成过量内存分配。
        if (count < 0 || count > MAX_EXCLUSIONS) {
            throw new DecoderException("Invalid Auto Torch exclusion count: " + count);
        }
        List<AreaZone> exclusions = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            exclusions.add(readZone(buffer));
        }
        return exclusions;
    }

    @Override
    public ResourceLocation id() {
        return ID;
    }
}
