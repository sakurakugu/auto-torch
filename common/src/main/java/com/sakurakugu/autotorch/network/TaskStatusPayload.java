package com.sakurakugu.autotorch.network;

import com.sakurakugu.autotorch.AutoTorch;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.ResourceLocation;

/** 服务端返回给客户端的照明任务进度快照。placementItem 是任务实际使用的放置物品注册 ID，没有任务时为空串。 */
public final class TaskStatusPayload implements AutoTorchPayload {
    public static final ResourceLocation ID = new ResourceLocation(AutoTorch.MOD_ID, "task_status");
    private final boolean running;
    private final int percent;
    private final int placed;
    private final String placementItem;

    public TaskStatusPayload(boolean running, int percent, int placed, String placementItem) {
        this.running = running;
        this.percent = percent;
        this.placed = placed;
        this.placementItem = placementItem;
    }

    public boolean running() { return running; }
    public int percent() { return percent; }
    public int placed() { return placed; }
    public String placementItem() { return placementItem; }

    public static TaskStatusPayload decode(PacketBuffer buffer) {
        return new TaskStatusPayload(buffer.readBoolean(), buffer.readVarIntFromBuffer(),
                buffer.readVarIntFromBuffer(), buffer.readStringFromBuffer(256));
    }

    @Override
    public void write(PacketBuffer buffer) {
        buffer.writeBoolean(running);
        buffer.writeVarIntToBuffer(percent);
        buffer.writeVarIntToBuffer(placed);
        // 1.8.9 的 PacketBuffer 只有 writeString(String)，长度上限由 readStringFromBuffer(maxLength) 在读取侧限制。
        buffer.writeString(placementItem);
    }

    @Override
    public ResourceLocation id() {
        return ID;
    }
}
