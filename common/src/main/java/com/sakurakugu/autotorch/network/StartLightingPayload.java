package com.sakurakugu.autotorch.network;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.sakurakugu.autotorch.AutoTorch;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.ResourceLocation;

/** 客户端提交的照明任务配置；服务端收到后仍需进行完整的边界校验。 */
public final class StartLightingPayload implements AutoTorchPayload {
    public static final int MAX_EXCLUSIONS = 32;
    public static final ResourceLocation ID = new ResourceLocation(AutoTorch.MOD_ID + ":start_lighting");
    private final AreaZone selection;
    private final int maxTorches;
    private final int minSpacing;
    private final int lightThreshold;
    private final boolean consumeTorches;
    private final boolean undergroundOnly;
    private final List<AreaZone> exclusions;

    public StartLightingPayload(
            AreaZone selection, int maxTorches, int minSpacing, int lightThreshold,
            boolean consumeTorches, boolean undergroundOnly, List<AreaZone> exclusions
    ) {
        this.selection = selection;
        this.maxTorches = maxTorches;
        this.minSpacing = minSpacing;
        this.lightThreshold = lightThreshold;
        this.consumeTorches = consumeTorches;
        this.undergroundOnly = undergroundOnly;
        // 固化列表，避免编码或异步处理期间调用方继续修改数据。
        this.exclusions = Collections.unmodifiableList(new ArrayList<>(exclusions));
    }

    public AreaZone selection() { return selection; }
    public int maxTorches() { return maxTorches; }
    public int minSpacing() { return minSpacing; }
    public int lightThreshold() { return lightThreshold; }
    public boolean consumeTorches() { return consumeTorches; }
    public boolean undergroundOnly() { return undergroundOnly; }
    public List<AreaZone> exclusions() { return exclusions; }

    public static StartLightingPayload decode(PacketBuffer buffer) {
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

    @Override
    public void write(PacketBuffer buffer) {
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

    private static void writeZone(PacketBuffer buffer, AreaZone zone) {
        buffer.writeByte(zone.shape().ordinal());
        buffer.writeBlockPos(zone.first());
        buffer.writeBlockPos(zone.second());
    }

    private static AreaZone readZone(PacketBuffer buffer) {
        int shapeId = buffer.readUnsignedByte();
        AreaShape[] shapes = AreaShape.values();
        if (shapeId >= shapes.length) {
            throw new DecoderException("Invalid Auto Torch area shape: " + shapeId);
        }
        return new AreaZone(shapes[shapeId], buffer.readBlockPos(), buffer.readBlockPos());
    }

    private static List<AreaZone> readExclusions(PacketBuffer buffer) {
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
