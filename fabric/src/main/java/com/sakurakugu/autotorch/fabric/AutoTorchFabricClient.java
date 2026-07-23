package com.sakurakugu.autotorch.fabric;

import com.sakurakugu.autotorch.client.AutoTorchClient;
import com.sakurakugu.autotorch.client.ClientConfig;
import com.sakurakugu.autotorch.client.LightOverlayRenderer;
import com.sakurakugu.autotorch.client.SelectionRenderer;
import com.sakurakugu.autotorch.client.ServerConfigState;
import com.sakurakugu.autotorch.network.PlatformNetworking;
import com.sakurakugu.autotorch.network.ServerConfigPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.InteractionResult;

public final class AutoTorchFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ClientConfig.install(new PropertiesConfigBackend(
                FabricLoader.getInstance().getConfigDir().resolve("autotorch-client.properties")));
        PlatformNetworking.installSender(ClientPlayNetworking::send);
        ClientPlayNetworking.registerGlobalReceiver(ServerConfigPayload.TYPE, (payload, context) ->
                ServerConfigState.update(payload));

        AutoTorchClient client = new AutoTorchClient();
        KeyMappingHelper.registerKeyMapping(AutoTorchClient.OPEN_SCREEN);
        KeyMappingHelper.registerKeyMapping(AutoTorchClient.TOGGLE_LIGHT_OVERLAY);
        ClientTickEvents.END_CLIENT_TICK.register(minecraft -> client.tick());

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

        LevelExtractionEvents.END_EXTRACTION.register(context -> {
            SelectionRenderer.extract(context.camera().blockPosition());
            LightOverlayRenderer.extract();
        });
        LevelRenderEvents.COLLECT_SUBMITS.register(context -> {
            SelectionRenderer.submit(context.levelState().cameraRenderState.pos,
                    context.poseStack(), context.submitNodeCollector());
            LightOverlayRenderer.submit(context.levelState().cameraRenderState.pos,
                    context.poseStack(), context.submitNodeCollector());
        });
    }
}
