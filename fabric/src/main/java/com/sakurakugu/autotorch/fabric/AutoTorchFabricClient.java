package com.sakurakugu.autotorch.fabric;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.blaze3d.vertex.PoseStack;
import com.sakurakugu.autotorch.client.AutoTorchClient;
import com.sakurakugu.autotorch.client.AutoTorchClientCommands;
import com.sakurakugu.autotorch.client.ClientConfig;
import com.sakurakugu.autotorch.client.LightOverlayRenderer;
import com.sakurakugu.autotorch.client.SelectionRenderer;
import com.sakurakugu.autotorch.client.ServerConfigState;
import com.sakurakugu.autotorch.config.ConfigDefinitions;
import com.sakurakugu.autotorch.network.PlatformNetworking;
import com.sakurakugu.autotorch.network.ServerConfigPayload;
import com.sakurakugu.autotorch.network.TaskStatusPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.world.InteractionResult;

public final class AutoTorchFabricClient implements ClientModInitializer {
    private CommandDispatcher<SharedSuggestionProvider> suggestionCommands;

    @Override
    public void onInitializeClient() {
        TomlConfigBackend clientConfig = new TomlConfigBackend(
                FabricLoader.getInstance().getConfigDir().resolve("autotorch-client.toml"),
                ConfigDefinitions.CLIENT);
        ClientConfig.install(clientConfig);
        ClientLifecycleEvents.CLIENT_STOPPING.register(minecraft -> {
            clientConfig.close();
            AutoTorchFabric.closeServerConfig();
        });
        PlatformNetworking.installSender(ClientPlayNetworking::send);
        ClientPlayNetworking.registerGlobalReceiver(ServerConfigPayload.TYPE, (payload, context) ->
                ServerConfigState.update(payload));
        ClientPlayNetworking.registerGlobalReceiver(TaskStatusPayload.TYPE, (payload, context) ->
                AutoTorchClientCommands.receiveTaskStatus(payload));

        AutoTorchClient client = new AutoTorchClient();
        KeyBindingHelper.registerKeyBinding(AutoTorchClient.OPEN_SCREEN);
        KeyBindingHelper.registerKeyBinding(AutoTorchClient.TOGGLE_LIGHT_OVERLAY);
        KeyBindingHelper.registerKeyBinding(AutoTorchClient.TOGGLE_LIGHT_OVERLAY_RENDER_THROUGH);
        ClientTickEvents.END_CLIENT_TICK.register(minecraft -> {
            client.tick();
            updateCommandSuggestions(minecraft);
        });
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, context) ->
                AutoTorchClientCommands.register(dispatcher));

        AttackBlockCallback.EVENT.register((player, level, hand, pos, direction) -> {
            if (level instanceof ClientLevel clientLevel
                    && client.onLeftClick(clientLevel, player.getItemInHand(hand), pos, true)) {
                return InteractionResult.SUCCESS;
            }
            return InteractionResult.PASS;
        });
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            if (level instanceof ClientLevel clientLevel
                    && client.onRightClick(clientLevel, hand, player.getItemInHand(hand), hit.getBlockPos())) {
                return InteractionResult.SUCCESS;
            }
            return InteractionResult.PASS;
        });

        WorldRenderEvents.AFTER_ENTITIES.register(context -> {
            var camera = context.camera().getPosition();
            SelectionRenderer.extract(context.camera().getBlockPosition());
            LightOverlayRenderer.extract();
            // AFTER_ENTITIES 已进入本帧相机模型视图矩阵，使用 Fabric 提供的矩阵栈和缓冲区。
            PoseStack poseStack = context.matrixStack();
            var buffers = context.consumers();
            SelectionRenderer.render(camera, poseStack, buffers);
            LightOverlayRenderer.render(camera, poseStack, buffers);
        });
    }

    private void updateCommandSuggestions(Minecraft minecraft) {
        if (minecraft.player == null) {
            suggestionCommands = null;
            return;
        }
        // 服务端命令树会与客户端命令树共用补全调度器，连接后合并一次以保留本地子命令补全。
        CommandDispatcher<SharedSuggestionProvider> commands = minecraft.player.connection.getCommands();
        if (commands != suggestionCommands) {
            AutoTorchClientCommands.register(commands);
            suggestionCommands = commands;
        }
    }
}
