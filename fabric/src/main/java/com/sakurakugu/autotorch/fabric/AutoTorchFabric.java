package com.sakurakugu.autotorch.fabric;

import com.sakurakugu.autotorch.config.ConfigDefinitions;
import com.sakurakugu.autotorch.network.CancelLightingPayload;
import com.sakurakugu.autotorch.network.OpenServerConfigScreenPayload;
import com.sakurakugu.autotorch.network.SetSelectionToolPayload;
import com.sakurakugu.autotorch.network.ServerConfigPayload;
import com.sakurakugu.autotorch.network.ServerConfigResetPayload;
import com.sakurakugu.autotorch.network.ServerConfigResultPayload;
import com.sakurakugu.autotorch.network.ServerConfigSetPayload;
import com.sakurakugu.autotorch.network.StartLightingPayload;
import com.sakurakugu.autotorch.network.TaskStatusPayload;
import com.sakurakugu.autotorch.network.TaskStatusRequestPayload;
import com.sakurakugu.autotorch.network.WorldSeedPayload;
import com.sakurakugu.autotorch.server.LightingTaskManager;
import com.sakurakugu.autotorch.server.AutoTorchServerCommands;
import com.sakurakugu.autotorch.server.SelectionToolEvents;
import com.sakurakugu.autotorch.server.ServerConfig;
import com.sakurakugu.autotorch.server.ServerConfigNetwork;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;

public final class AutoTorchFabric implements ModInitializer {
    private static TomlConfigBackend serverConfig;

    @Override
    public void onInitialize() {
        // 单机、局域网和专用服务器都注册，客户端才能拿到服务端命令树并补全 serverconfig。
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                AutoTorchServerCommands.register(dispatcher, ServerConfigNetwork::broadcast));
        ServerConfigNetwork.installSender((player, payload) -> ServerPlayNetworking.send(player, payload));
        serverConfig = new TomlConfigBackend(
                FabricLoader.getInstance().getConfigDir().resolve("autotorch-server.toml"),
                ConfigDefinitions.SERVER);
        ServerConfig.install(serverConfig);
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> closeServerConfig());

        PayloadTypeRegistry.serverboundPlay().register(StartLightingPayload.TYPE, StartLightingPayload.STREAM_CODEC);
        PayloadTypeRegistry.serverboundPlay().register(CancelLightingPayload.TYPE, CancelLightingPayload.STREAM_CODEC);
        PayloadTypeRegistry.serverboundPlay().register(SetSelectionToolPayload.TYPE, SetSelectionToolPayload.STREAM_CODEC);
        PayloadTypeRegistry.serverboundPlay().register(TaskStatusRequestPayload.TYPE, TaskStatusRequestPayload.STREAM_CODEC);
        PayloadTypeRegistry.serverboundPlay().register(ServerConfigSetPayload.TYPE, ServerConfigSetPayload.STREAM_CODEC);
        PayloadTypeRegistry.serverboundPlay().register(ServerConfigResetPayload.TYPE, ServerConfigResetPayload.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(ServerConfigPayload.TYPE, ServerConfigPayload.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(OpenServerConfigScreenPayload.TYPE,
                OpenServerConfigScreenPayload.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(ServerConfigResultPayload.TYPE,
                ServerConfigResultPayload.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(TaskStatusPayload.TYPE, TaskStatusPayload.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(WorldSeedPayload.TYPE, WorldSeedPayload.STREAM_CODEC);
        ServerPlayNetworking.registerGlobalReceiver(StartLightingPayload.TYPE,
                (payload, context) -> LightingTaskManager.start(context.player(), payload));
        ServerPlayNetworking.registerGlobalReceiver(CancelLightingPayload.TYPE,
                (payload, context) -> LightingTaskManager.cancel(context.player()));
        ServerPlayNetworking.registerGlobalReceiver(SetSelectionToolPayload.TYPE,
                (payload, context) -> SelectionToolEvents.setEnabled(context.player(), payload.enabled(), payload.toolItem()));
        ServerPlayNetworking.registerGlobalReceiver(ServerConfigSetPayload.TYPE,
                (payload, context) -> ServerConfigNetwork.handleSet(context.player(), payload));
        ServerPlayNetworking.registerGlobalReceiver(ServerConfigResetPayload.TYPE,
                (payload, context) -> ServerConfigNetwork.handleReset(context.player(), payload));
        ServerPlayNetworking.registerGlobalReceiver(TaskStatusRequestPayload.TYPE,
                (payload, context) -> ServerPlayNetworking.send(context.player(),
                        LightingTaskManager.status(context.player())));

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
                {
                    ServerPlayer player = handler.getPlayer();
                    ServerConfigNetwork.sendConfig(player);
                    ServerPlayNetworking.send(player, new WorldSeedPayload(player.level().getSeed()));
                });
    }

    static void closeServerConfig() {
        if (serverConfig != null) {
            serverConfig.close();
        }
    }
}
