package com.sakurakugu.autotorch.forge;

import com.sakurakugu.autotorch.AutoTorch;
import com.sakurakugu.autotorch.network.AutoTorchPayload;
import com.sakurakugu.autotorch.network.CancelLightingPayload;
import com.sakurakugu.autotorch.network.SetSelectionToolPayload;
import com.sakurakugu.autotorch.network.StartLightingPayload;
import com.sakurakugu.autotorch.network.ServerConfigPayload;
import com.sakurakugu.autotorch.client.ServerConfigState;
import com.sakurakugu.autotorch.server.LightingTaskManager;
import com.sakurakugu.autotorch.server.SelectionToolEvents;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.network.NetworkDirection;
import net.minecraftforge.fml.network.NetworkRegistry;
import net.minecraftforge.fml.network.NetworkEvent;
import net.minecraftforge.fml.network.PacketDistributor;
import net.minecraftforge.fml.network.simple.SimpleChannel;

import java.util.Optional;

final class ForgeNetworking {
    private static final String PROTOCOL_VERSION = "5";
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(AutoTorch.MOD_ID + ":main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    private ForgeNetworking() {
    }

    static void initialize() {
        CHANNEL.registerMessage(0, StartLightingPayload.class,
                StartLightingPayload::write, StartLightingPayload::decode, (payload, supplier) -> {
                    NetworkEvent.Context context = supplier.get();
                    context.enqueueWork(() -> {
                        if (context.getSender() != null) LightingTaskManager.start(context.getSender(), payload);
                    });
                    context.setPacketHandled(true);
                }, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(1, CancelLightingPayload.class,
                CancelLightingPayload::write, CancelLightingPayload::decode, (payload, supplier) -> {
                    NetworkEvent.Context context = supplier.get();
                    context.enqueueWork(() -> {
                        if (context.getSender() != null) LightingTaskManager.cancel(context.getSender());
                    });
                    context.setPacketHandled(true);
                }, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(2, SetSelectionToolPayload.class,
                SetSelectionToolPayload::write, SetSelectionToolPayload::decode, (payload, supplier) -> {
                    NetworkEvent.Context context = supplier.get();
                    context.enqueueWork(() -> {
                        if (context.getSender() != null) {
                            SelectionToolEvents.setEnabled(context.getSender(), payload.enabled());
                        }
                    });
                    context.setPacketHandled(true);
                }, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(3, ServerConfigPayload.class,
                ServerConfigPayload::write, ServerConfigPayload::decode, (payload, supplier) -> {
                    NetworkEvent.Context context = supplier.get();
                    context.enqueueWork(() -> ServerConfigState.update(payload));
                    context.setPacketHandled(true);
                }, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }

    static void sendToServer(AutoTorchPayload payload) {
        CHANNEL.sendToServer(payload);
    }

    static void sendToPlayer(net.minecraft.entity.player.ServerPlayerEntity player, AutoTorchPayload payload) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), payload);
    }
}
