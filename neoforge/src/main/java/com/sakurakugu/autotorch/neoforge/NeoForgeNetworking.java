package com.sakurakugu.autotorch.neoforge;

import com.sakurakugu.autotorch.network.CancelLightingPayload;
import com.sakurakugu.autotorch.network.SetSelectionToolPayload;
import com.sakurakugu.autotorch.network.StartLightingPayload;
import com.sakurakugu.autotorch.server.LightingTaskManager;
import com.sakurakugu.autotorch.server.SelectionToolEvents;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

final class NeoForgeNetworking {
    private NeoForgeNetworking() {
    }

    static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("4");
        registrar.playToServer(StartLightingPayload.TYPE, StartLightingPayload.STREAM_CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) LightingTaskManager.start(player, payload);
        });
        registrar.playToServer(CancelLightingPayload.TYPE, CancelLightingPayload.STREAM_CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) LightingTaskManager.cancel(player);
        });
        registrar.playToServer(SetSelectionToolPayload.TYPE, SetSelectionToolPayload.STREAM_CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) SelectionToolEvents.setEnabled(player, payload.enabled());
        });
    }
}
