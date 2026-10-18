package com.sakurakugu.autotorch.forge;

import com.sakurakugu.autotorch.AutoTorch;
import com.sakurakugu.autotorch.network.CancelLightingPayload;
import com.sakurakugu.autotorch.network.OpenServerConfigScreenPayload;
import com.sakurakugu.autotorch.network.SetSelectionToolPayload;
import com.sakurakugu.autotorch.network.StartLightingPayload;
import com.sakurakugu.autotorch.network.ServerConfigPayload;
import com.sakurakugu.autotorch.network.ServerConfigResetPayload;
import com.sakurakugu.autotorch.network.ServerConfigResultPayload;
import com.sakurakugu.autotorch.network.ServerConfigSetPayload;
import com.sakurakugu.autotorch.network.TaskStatusPayload;
import com.sakurakugu.autotorch.network.TaskStatusRequestPayload;
import com.sakurakugu.autotorch.client.AutoTorchClient;
import com.sakurakugu.autotorch.client.AutoTorchClientCommands;
import com.sakurakugu.autotorch.client.ServerConfigScreen;
import com.sakurakugu.autotorch.client.ServerConfigState;
import com.sakurakugu.autotorch.server.LightingTaskManager;
import com.sakurakugu.autotorch.server.SelectionToolEvents;
import com.sakurakugu.autotorch.server.ServerConfigNetwork;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.Channel;
import net.minecraftforge.network.ChannelBuilder;
import net.minecraftforge.network.PacketDistributor;

final class ForgeNetworking {
    private static final Channel<CustomPacketPayload> CHANNEL = ChannelBuilder
            .named(ResourceLocation.fromNamespaceAndPath(AutoTorch.MOD_ID, "main"))
            .networkProtocolVersion(8)
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
                if (context.getSender() != null) SelectionToolEvents.setEnabled(context.getSender(), payload.enabled(), payload.toolItem());
            })
            .addMain(ServerConfigSetPayload.TYPE, ServerConfigSetPayload.STREAM_CODEC, (payload, context) -> {
                if (context.getSender() != null) ServerConfigNetwork.handleSet(context.getSender(), payload);
            })
            .addMain(ServerConfigResetPayload.TYPE, ServerConfigResetPayload.STREAM_CODEC, (payload, context) -> {
                if (context.getSender() != null) ServerConfigNetwork.handleReset(context.getSender(), payload);
            })
            .addMain(TaskStatusRequestPayload.TYPE, TaskStatusRequestPayload.STREAM_CODEC, (payload, context) -> {
                if (context.getSender() != null) sendToPlayer(context.getSender(),
                        LightingTaskManager.status(context.getSender()));
            })
            .clientbound()
            .addMain(ServerConfigPayload.TYPE, ServerConfigPayload.STREAM_CODEC, (payload, context) ->
                    ServerConfigState.update(payload))
            .addMain(OpenServerConfigScreenPayload.TYPE, OpenServerConfigScreenPayload.STREAM_CODEC,
                    (payload, context) -> AutoTorchClient.requestOpenServerConfigScreen())
            .addMain(ServerConfigResultPayload.TYPE, ServerConfigResultPayload.STREAM_CODEC,
                    (payload, context) -> ServerConfigScreen.receiveResult(payload))
            .addMain(TaskStatusPayload.TYPE, TaskStatusPayload.STREAM_CODEC, (payload, context) ->
                    AutoTorchClientCommands.receiveTaskStatus(payload))
            .build();

    private ForgeNetworking() {
    }

    static void initialize() {
    }

    static void sendToServer(CustomPacketPayload payload) {
        CHANNEL.send(payload, PacketDistributor.SERVER.noArg());
    }

    static void sendToPlayer(net.minecraft.server.level.ServerPlayer player, CustomPacketPayload payload) {
        CHANNEL.send(payload, PacketDistributor.PLAYER.with(player));
    }
}
