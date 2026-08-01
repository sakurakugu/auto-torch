package com.sakurakugu.autotorch.forge;

import com.sakurakugu.autotorch.AutoTorch;
import com.sakurakugu.autotorch.network.AutoTorchPayload;
import com.sakurakugu.autotorch.network.CancelLightingPayload;
import com.sakurakugu.autotorch.network.SetSelectionToolPayload;
import com.sakurakugu.autotorch.network.StartLightingPayload;
import com.sakurakugu.autotorch.network.ServerConfigPayload;
import com.sakurakugu.autotorch.network.TaskStatusPayload;
import com.sakurakugu.autotorch.network.TaskStatusRequestPayload;
import com.sakurakugu.autotorch.client.AutoTorchClientCommands;
import com.sakurakugu.autotorch.client.ServerConfigState;
import com.sakurakugu.autotorch.server.LightingTaskManager;
import com.sakurakugu.autotorch.server.SelectionToolEvents;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Optional;

final class ForgeNetworking {
    private static final String PROTOCOL_VERSION = "6";
    private static final SimpleChannel CHANNEL = NetworkRegistry.ChannelBuilder
            .named(ResourceLocation.tryBuild(AutoTorch.MOD_ID, "main"))
            .networkProtocolVersion(() -> PROTOCOL_VERSION)
            .clientAcceptedVersions(PROTOCOL_VERSION::equals)
            .serverAcceptedVersions(PROTOCOL_VERSION::equals)
            .simpleChannel();

    private ForgeNetworking() {
    }

    static void initialize() {
        CHANNEL.registerMessage(0, StartLightingPayload.class,
                StartLightingPayload::write, StartLightingPayload::decode, (payload, supplier) -> {
                    var context = supplier.get();
                    context.enqueueWork(() -> {
                        if (context.getSender() != null) LightingTaskManager.start(context.getSender(), payload);
                    });
                    context.setPacketHandled(true);
                }, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(1, CancelLightingPayload.class,
                CancelLightingPayload::write, CancelLightingPayload::decode, (payload, supplier) -> {
                    var context = supplier.get();
                    context.enqueueWork(() -> {
                        if (context.getSender() != null) LightingTaskManager.cancel(context.getSender());
                    });
                    context.setPacketHandled(true);
                }, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(2, SetSelectionToolPayload.class,
                SetSelectionToolPayload::write, SetSelectionToolPayload::decode, (payload, supplier) -> {
                    var context = supplier.get();
                    context.enqueueWork(() -> {
                        if (context.getSender() != null) {
                            SelectionToolEvents.setEnabled(context.getSender(), payload.enabled());
                        }
                    });
                    context.setPacketHandled(true);
                }, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(3, ServerConfigPayload.class,
                ServerConfigPayload::write, ServerConfigPayload::decode, (payload, supplier) -> {
                    var context = supplier.get();
                    context.enqueueWork(() -> ServerConfigState.update(payload));
                    context.setPacketHandled(true);
                }, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(4, TaskStatusRequestPayload.class,
                TaskStatusRequestPayload::write, TaskStatusRequestPayload::decode, (payload, supplier) -> {
                    var context = supplier.get();
                    context.enqueueWork(() -> {
                        if (context.getSender() != null) {
                            sendToPlayer(context.getSender(), LightingTaskManager.status(context.getSender()));
                        }
                    });
                    context.setPacketHandled(true);
                }, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(5, TaskStatusPayload.class,
                TaskStatusPayload::write, TaskStatusPayload::decode, (payload, supplier) -> {
                    var context = supplier.get();
                    context.enqueueWork(() -> AutoTorchClientCommands.receiveTaskStatus(payload));
                    context.setPacketHandled(true);
                }, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }

    static void sendToServer(AutoTorchPayload payload) {
        CHANNEL.sendToServer(payload);
    }

    static void sendToPlayer(net.minecraft.server.level.ServerPlayer player, AutoTorchPayload payload) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), payload);
    }
}
