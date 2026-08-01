package com.sakurakugu.autotorch.fabric;

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
        PlatformNetworking.installSender(ClientPlayNetworking::send);
        ClientPlayNetworking.registerGlobalReceiver(ServerConfigPayload.TYPE, (payload, context) ->
                ServerConfigState.update(payload));

        AutoTorchClient client = new AutoTorchClient();
        KeyBindingHelper.registerKeyBinding(AutoTorchClient.OPEN_SCREEN);
        KeyBindingHelper.registerKeyBinding(AutoTorchClient.TOGGLE_LIGHT_OVERLAY);
        ClientTickEvents.END_CLIENT_TICK.register(minecraft -> client.tick());
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

        WorldRenderEvents.BEFORE_ENTITIES.register(context -> {
            var camera = context.camera().getPosition();
            SelectionRenderer.extract(context.camera().getBlockPosition());
            LightOverlayRenderer.extract();
            // 1.20.6 在此阶段不提供矩阵栈，需要显式应用视图旋转并使用相机相对坐标。
            PoseStack poseStack = new PoseStack();
            poseStack.mulPose(context.positionMatrix());
            var buffers = Minecraft.getInstance().renderBuffers().bufferSource();
            SelectionRenderer.render(camera, poseStack, buffers);
            LightOverlayRenderer.render(camera, poseStack, buffers);
            // 自定义几何必须在当前相机模型视图仍有效时提交，不能留到共享缓冲区稍后冲刷。
            buffers.endBatch(RenderType.lines());
            buffers.endBatch(SelectionRenderer.faceRenderType());
        });
    }
}
