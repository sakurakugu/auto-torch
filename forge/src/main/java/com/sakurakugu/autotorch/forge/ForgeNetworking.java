package com.sakurakugu.autotorch.forge;

import com.sakurakugu.autotorch.AutoTorch;
import com.sakurakugu.autotorch.network.CancelLightingPayload;
import com.sakurakugu.autotorch.network.SetSelectionToolPayload;
import com.sakurakugu.autotorch.network.StartLightingPayload;
import com.sakurakugu.autotorch.server.LightingTaskManager;
import com.sakurakugu.autotorch.server.SelectionToolEvents;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraftforge.network.Channel;
import net.minecraftforge.network.ChannelBuilder;
import net.minecraftforge.network.PacketDistributor;

final class ForgeNetworking {
    private static final Channel<CustomPacketPayload> CHANNEL = ChannelBuilder
            .named(Identifier.fromNamespaceAndPath(AutoTorch.MOD_ID, "main"))
            .networkProtocolVersion(4)
            .payloadChannel()
            .play()
            .serverbound()
            .addMain(StartLightingPayload.TYPE, StartLightingPayload.STREAM_CODEC, (payload, context) -> {
                if (context.getSender() != null) LightingTaskManager.start(context.getSender(), payload);
            })
            .addMain(CancelLightingPayload.TYPE, CancelLightingPayload.STREAM_CODEC, (payload, context) -> {
                if (context.getSender() != null) LightingTaskManager.cancel(context.getSender());
            })
            .addMain(SetSelectionToolPayload.TYPE, SetSelectionToolPayload.STREAM_CODEC, (payload, context) -> {
                if (context.getSender() != null) SelectionToolEvents.setEnabled(context.getSender(), payload.enabled());
            })
            .build();

    private ForgeNetworking() {
    }

    static void initialize() {
    }

    static void sendToServer(CustomPacketPayload payload) {
        CHANNEL.send(payload, PacketDistributor.SERVER.noArg());
    }
}
