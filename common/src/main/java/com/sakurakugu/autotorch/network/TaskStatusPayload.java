package com.sakurakugu.autotorch.network;

import com.sakurakugu.autotorch.AutoTorch;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

/** 服务端返回给客户端的照明任务进度快照。 */
public final class TaskStatusPayload implements AutoTorchPayload {
    public static final ResourceLocation ID = new ResourceLocation(AutoTorch.MOD_ID, "task_status");
    private final boolean running;
    private final int percent;
    private final int placed;

    public TaskStatusPayload(boolean running, int percent, int placed) {
        this.running = running;
        this.percent = percent;
        this.placed = placed;
    }

    public boolean running() { return running; }
    public int percent() { return percent; }
    public int placed() { return placed; }

    public static TaskStatusPayload decode(FriendlyByteBuf buffer) {
        return new TaskStatusPayload(buffer.readBoolean(), buffer.readVarInt(), buffer.readVarInt());
    }

    @Override
    public void write(FriendlyByteBuf buffer) {
        buffer.writeBoolean(running);
        buffer.writeVarInt(percent);
        buffer.writeVarInt(placed);
    }

    @Override
    public ResourceLocation id() {
        return ID;
    }
}
