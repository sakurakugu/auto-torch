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
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Camera;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

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
        ClientTickEvents.END_CLIENT_TICK.register(minecraft -> client.tick());
        AutoTorchClientCommands.register(ClientCommandManager.DISPATCHER);

        AttackBlockCallback.EVENT.register((player, level, hand, pos, direction) -> {
            if (client.onLeftClick(level, player.getItemInHand(hand), pos, true)) {
                return InteractionResult.SUCCESS;
            }
            return InteractionResult.PASS;
        });
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            if (client.onRightClick(level, hand, player.getItemInHand(hand), hit.getBlockPos())) {
                return InteractionResult.SUCCESS;
            }
            return InteractionResult.PASS;
        });

    }

    public static void renderWorld(Camera camera) {
        Minecraft minecraft = Minecraft.getInstance();
        Vec3 cameraPosition = camera.getPosition();
        SelectionRenderer.extract(camera.getBlockPosition());
        LightOverlayRenderer.extract();
        SelectionRenderer.render(cameraPosition);
        LightOverlayRenderer.render(cameraPosition);
        if (minecraft.level != null && minecraft.level.getFluidState(camera.getBlockPosition()).isEmpty()) {
            LightOverlayRenderer.renderWaterVisible(
                    cameraPosition, target ->
                            minecraft.level.clip(new ClipContext(
                                    cameraPosition, target, ClipContext.Block.COLLIDER,
                                    ClipContext.Fluid.NONE, camera.getEntity()
                            )).getType() == HitResult.Type.MISS);
        }
    }
}
