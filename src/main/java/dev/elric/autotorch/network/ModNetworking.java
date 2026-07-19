package dev.elric.autotorch.network;

import dev.elric.autotorch.server.LightingTaskManager;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** 注册客户端发往服务端的任务控制协议。 */
public final class ModNetworking {
    private ModNetworking() {
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        // 协议版本变化时应同步调整此版本号，以拒绝不兼容的客户端。
        PayloadRegistrar registrar = event.registrar("3");
        registrar.playToServer(StartLightingPayload.TYPE, StartLightingPayload.STREAM_CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) {
                LightingTaskManager.start(player, payload);
            }
        });
        registrar.playToServer(CancelLightingPayload.TYPE, CancelLightingPayload.STREAM_CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) {
                LightingTaskManager.cancel(player);
            }
        });
    }
}
