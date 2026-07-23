package com.sakurakugu.autotorch.fabric;

import com.sakurakugu.autotorch.network.CancelLightingPayload;
import com.sakurakugu.autotorch.network.SetSelectionToolPayload;
import com.sakurakugu.autotorch.network.ServerConfigPayload;
import com.sakurakugu.autotorch.network.StartLightingPayload;
import com.sakurakugu.autotorch.server.LightingTaskManager;
import com.sakurakugu.autotorch.server.SelectionToolEvents;
import com.sakurakugu.autotorch.server.ServerConfig;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;

public final class AutoTorchFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        ServerConfig.install(new PropertiesConfigBackend(
                FabricLoader.getInstance().getConfigDir().resolve("autotorch-server.properties")));

        PayloadTypeRegistry.playC2S().register(StartLightingPayload.TYPE, StartLightingPayload.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(CancelLightingPayload.TYPE, CancelLightingPayload.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(SetSelectionToolPayload.TYPE, SetSelectionToolPayload.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(ServerConfigPayload.TYPE, ServerConfigPayload.STREAM_CODEC);
        ServerPlayNetworking.registerGlobalReceiver(StartLightingPayload.TYPE,
                (payload, context) -> LightingTaskManager.start(context.player(), payload));
        ServerPlayNetworking.registerGlobalReceiver(CancelLightingPayload.TYPE,
                (payload, context) -> LightingTaskManager.cancel(context.player()));
        ServerPlayNetworking.registerGlobalReceiver(SetSelectionToolPayload.TYPE,
                (payload, context) -> SelectionToolEvents.setEnabled(context.player(), payload.enabled()));

        ServerTickEvents.END_SERVER_TICK.register(LightingTaskManager::onServerTick);
        AttackBlockCallback.EVENT.register((player, level, hand, pos, direction) ->
                player instanceof ServerPlayer serverPlayer
                        && SelectionToolEvents.handlesInteraction(serverPlayer, player.getItemInHand(hand))
                        ? InteractionResult.SUCCESS : InteractionResult.PASS);
        UseBlockCallback.EVENT.register((player, level, hand, hit) ->
                hand == InteractionHand.MAIN_HAND
                        && player instanceof ServerPlayer serverPlayer
                        && SelectionToolEvents.handlesInteraction(serverPlayer, player.getItemInHand(hand))
                        ? InteractionResult.SUCCESS_SERVER : InteractionResult.PASS);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                SelectionToolEvents.onLogout(handler.getPlayer()));
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                ServerPlayNetworking.send(handler.getPlayer(),
                        ServerConfigPayload.current()));
    }
}
