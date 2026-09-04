package com.sakurakugu.autotorch.fabric;

import com.mojang.brigadier.CommandDispatcher;
import com.sakurakugu.autotorch.client.AutoTorchClient;
import com.sakurakugu.autotorch.client.AutoTorchClientCommands;
import com.sakurakugu.autotorch.client.AutoTorchRenderTypes;
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
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;

public final class AutoTorchFabricClient implements ClientModInitializer {
    private CommandDispatcher<SharedSuggestionProvider> suggestionCommands;

    @Override
    public void onInitializeClient() {
        TomlConfigBackend clientConfig = new TomlConfigBackend(
                FabricLoader.getInstance().getConfigDir().resolve("autotorch-client.toml"),
                ConfigDefinitions.CLIENT);
        ClientConfig.install(clientConfig);
        LightOverlayRenderer.setDrownedMarkerVisibility((level, camera, marker) -> {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.player == null) {
                return false;
            }
            Vec3 target = new Vec3(
                    marker.pos().getX() + 0.5D,
                    marker.pos().getY() + 0.0125D,
                    marker.pos().getZ() + 0.5D
            );
            // 忽略流体进行射线检测，使水下标记可以穿过水面，但实体方块仍会遮挡标记。
            return level.clip(new ClipContext(
                    camera, target, ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE, minecraft.player
            )).getType() == HitResult.Type.MISS;
        });
        ClientLifecycleEvents.CLIENT_STOPPING.register(minecraft -> {
            clientConfig.close();
            AutoTorchFabric.closeServerConfig();
        });
        PlatformNetworking.installSender(payload -> {
            FriendlyByteBuf buffer = PacketByteBufs.create();
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
        KeyBindingHelper.registerKeyBinding(AutoTorchClient.TOGGLE_LIGHT_OVERLAY_RENDER_THROUGH);
        ClientTickEvents.END_CLIENT_TICK.register(minecraft -> {
            client.tick();
            updateCommandSuggestions(minecraft);
        });

        AttackBlockCallback.EVENT.register((player, level, hand, pos, direction) -> {
            if (level instanceof ClientLevel
                    && client.onLeftClick((ClientLevel) level, player.getItemInHand(hand), pos, true)) {
                return InteractionResult.SUCCESS;
            }
            return InteractionResult.PASS;
        });
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            if (level instanceof ClientLevel
                    && client.onRightClick((ClientLevel) level, hand, player.getItemInHand(hand), hit.getBlockPos())) {
                return InteractionResult.SUCCESS;
            }
            return InteractionResult.PASS;
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

    public static void renderWorld(PoseStack poseStack, Camera camera) {
        Minecraft minecraft = Minecraft.getInstance();
        Vec3 cameraPosition = camera.getPosition();
        SelectionRenderer.extract(camera.getBlockPosition());
        LightOverlayRenderer.extract();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        SelectionRenderer.render(cameraPosition, poseStack, buffers);
        LightOverlayRenderer.render(cameraPosition, poseStack, buffers);
        // 自定义几何必须在当前相机模型视图仍有效时提交，不能留到共享缓冲区稍后冲刷。
        LightOverlayRenderer.endBatches(buffers);
        buffers.endBatch(RenderType.lines());
        buffers.endBatch(AutoTorchRenderTypes.seeThroughLines());
        buffers.endBatch(SelectionRenderer.faceRenderType());
    }
}
