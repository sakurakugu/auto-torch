package com.sakurakugu.autotorch.fabric;

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
import net.fabricmc.fabric.api.client.command.v1.ClientCommandManager;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.InteractionResult;

public final class AutoTorchFabricClient implements ClientModInitializer {
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
        PlatformNetworking.installSender(payload -> {
            var buffer = PacketByteBufs.create();
            payload.write(buffer);
            ClientPlayNetworking.send(payload.id(), buffer);
        });
        ClientPlayNetworking.registerGlobalReceiver(ServerConfigPayload.ID,
                (minecraft, handler, buffer, sender) -> {
                    ServerConfigPayload payload = ServerConfigPayload.decode(buffer);
                    minecraft.execute(() -> ServerConfigState.update(payload));
                });
        ClientPlayNetworking.registerGlobalReceiver(TaskStatusPayload.ID,
                (minecraft, handler, buffer, sender) -> {
                    TaskStatusPayload payload = TaskStatusPayload.decode(buffer);
                    minecraft.execute(() -> AutoTorchClientCommands.receiveTaskStatus(payload));
                });

        AutoTorchClient client = new AutoTorchClient();
        KeyBindingHelper.registerKeyBinding(AutoTorchClient.OPEN_SCREEN);
        KeyBindingHelper.registerKeyBinding(AutoTorchClient.TOGGLE_LIGHT_OVERLAY);
        ClientTickEvents.END_CLIENT_TICK.register(minecraft -> client.tick());
        AutoTorchClientCommands.register(ClientCommandManager.DISPATCHER);

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

        WorldRenderEvents.BEFORE_ENTITIES.register(context -> {
            var camera = context.camera().getPosition();
            SelectionRenderer.extract(context.camera().getBlockPosition());
            LightOverlayRenderer.extract();
            var poseStack = context.matrixStack();
            var buffers = Minecraft.getInstance().renderBuffers().bufferSource();
            SelectionRenderer.render(camera, poseStack, buffers);
            LightOverlayRenderer.render(camera, poseStack, buffers);
            // 自定义几何必须在当前相机模型视图仍有效时提交，不能留到共享缓冲区稍后冲刷。
            buffers.endBatch(RenderType.lines());
            buffers.endBatch(SelectionRenderer.faceRenderType());
        });
    }
}
