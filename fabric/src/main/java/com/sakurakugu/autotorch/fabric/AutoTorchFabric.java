package com.sakurakugu.autotorch.fabric;

import com.sakurakugu.autotorch.config.ConfigDefinitions;
import com.sakurakugu.autotorch.network.CancelLightingPayload;
import com.sakurakugu.autotorch.network.SetSelectionToolPayload;
import com.sakurakugu.autotorch.network.ServerConfigPayload;
import com.sakurakugu.autotorch.network.StartLightingPayload;
import com.sakurakugu.autotorch.network.TaskStatusPayload;
import com.sakurakugu.autotorch.network.TaskStatusRequestPayload;
import com.sakurakugu.autotorch.server.LightingTaskManager;
import com.sakurakugu.autotorch.server.AutoTorchServerCommands;
import com.sakurakugu.autotorch.server.SelectionToolEvents;
import com.sakurakugu.autotorch.server.ServerConfig;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.command.v1.CommandRegistrationCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;

public final class AutoTorchFabric implements ModInitializer {
    private static TomlConfigBackend serverConfig;

    @Override
    public void onInitialize() {
        // 单机、局域网和专用服务器都注册，客户端才能拿到服务端命令树并补全 serverconfig。
        CommandRegistrationCallback.EVENT.register((dispatcher, dedicated) ->
                AutoTorchServerCommands.register(dispatcher, server ->
                        server.getPlayerList().getPlayers().forEach(player ->
                                sendServerConfig(player))));
        serverConfig = new TomlConfigBackend(
                FabricLoader.getInstance().getConfigDir().resolve("autotorch-server.toml"),
                ConfigDefinitions.SERVER);
        ServerConfig.install(serverConfig);
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> closeServerConfig());

        ServerPlayNetworking.registerGlobalReceiver(StartLightingPayload.ID,
                (server, player, handler, buffer, sender) -> {
                    StartLightingPayload payload = StartLightingPayload.decode(buffer);
                    server.execute(() -> LightingTaskManager.start(player, payload));
                });
        ServerPlayNetworking.registerGlobalReceiver(CancelLightingPayload.ID,
                (server, player, handler, buffer, sender) ->
                        server.execute(() -> LightingTaskManager.cancel(player)));
        ServerPlayNetworking.registerGlobalReceiver(SetSelectionToolPayload.ID,
                (server, player, handler, buffer, sender) -> {
                    SetSelectionToolPayload payload = SetSelectionToolPayload.decode(buffer);
                    server.execute(() -> SelectionToolEvents.setEnabled(player, payload.enabled()));
                });
        ServerPlayNetworking.registerGlobalReceiver(TaskStatusRequestPayload.ID,
                (server, player, handler, buffer, sender) -> server.execute(() -> {
                    TaskStatusPayload payload = LightingTaskManager.status(player);
                    var response = PacketByteBufs.create();
                    payload.write(response);
                    ServerPlayNetworking.send(player, payload.id(), response);
                }));

        ServerTickEvents.END_SERVER_TICK.register(LightingTaskManager::onServerTick);
        AttackBlockCallback.EVENT.register((player, level, hand, pos, direction) ->
                player instanceof ServerPlayer serverPlayer
                        && SelectionToolEvents.handlesInteraction(serverPlayer, player.getItemInHand(hand))
                        ? InteractionResult.SUCCESS : InteractionResult.PASS);
        UseBlockCallback.EVENT.register((player, level, hand, hit) ->
                hand == InteractionHand.MAIN_HAND
                        && player instanceof ServerPlayer serverPlayer
                        && SelectionToolEvents.handlesInteraction(serverPlayer, player.getItemInHand(hand))
                        ? InteractionResult.SUCCESS : InteractionResult.PASS);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                SelectionToolEvents.onLogout(handler.getPlayer()));
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            sendServerConfig(handler.getPlayer());
        });
    }

    private static void sendServerConfig(ServerPlayer player) {
        ServerConfigPayload payload = ServerConfigPayload.current();
        var buffer = PacketByteBufs.create();
        payload.write(buffer);
        ServerPlayNetworking.send(player, payload.id(), buffer);
    }

    static void closeServerConfig() {
        if (serverConfig != null) {
            serverConfig.close();
        }
    }
}
