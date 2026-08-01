package com.sakurakugu.autotorch.network;

import com.sakurakugu.autotorch.AutoTorch;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.ResourceLocation;

/** 客户端请求自己当前照明任务状态的无数据载荷。 */
public final class TaskStatusRequestPayload implements AutoTorchPayload {
    public static final ResourceLocation ID = new ResourceLocation(AutoTorch.MOD_ID, "task_status_request");

    public static TaskStatusRequestPayload decode(PacketBuffer ignored) {
        return new TaskStatusRequestPayload();
    }

    @Override
    public void write(PacketBuffer ignored) {
    }

    @Override
    public ResourceLocation id() {
        return ID;
    }
}
