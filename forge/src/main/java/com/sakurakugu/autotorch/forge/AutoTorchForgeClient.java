package com.sakurakugu.autotorch.forge;

import com.mojang.blaze3d.vertex.PoseStack;
import com.sakurakugu.autotorch.client.AutoTorchClient;
import com.sakurakugu.autotorch.client.ClientConfig;
import com.sakurakugu.autotorch.client.LightOverlayRenderer;
import com.sakurakugu.autotorch.client.SelectionRenderer;
import com.sakurakugu.autotorch.network.PlatformNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.InteractionResult;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

final class AutoTorchForgeClient {
    private final AutoTorchClient client = new AutoTorchClient();

    private AutoTorchForgeClient(FMLJavaModLoadingContext context) {
        ClientConfig.install(ForgeConfigs.CLIENT);
        PlatformNetworking.installSender(ForgeNetworking::sendToServer);
        context.registerConfig(ModConfig.Type.CLIENT, ForgeConfigs.CLIENT.spec());

        context.getModEventBus().addListener(this::registerKeys);
        MinecraftForge.EVENT_BUS.addListener(this::onRender);
        MinecraftForge.EVENT_BUS.addListener(this::onTick);
        MinecraftForge.EVENT_BUS.addListener(this::onLeftClick);
        MinecraftForge.EVENT_BUS.addListener(this::onRightClick);
    }

    static void initialize(FMLJavaModLoadingContext context) {
        new AutoTorchForgeClient(context);
    }

    private void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(AutoTorchClient.OPEN_SCREEN);
        event.register(AutoTorchClient.TOGGLE_LIGHT_OVERLAY);
    }

    private void onTick(TickEvent.ClientTickEvent.Post event) {
        client.tick();
    }

    private void onLeftClick(PlayerInteractEvent.LeftClickBlock event) {
        if (event.getEntity().level() instanceof ClientLevel clientLevel
                && client.onLeftClick(clientLevel, event.getItemStack(), event.getPos(),
                event.getAction() == PlayerInteractEvent.LeftClickBlock.Action.START)) {
            event.setCanceled(true);
        }
    }

    private void onRightClick(PlayerInteractEvent.RightClickBlock event) {
        if (event.getEntity().level() instanceof ClientLevel clientLevel
                && client.onRightClick(clientLevel, event.getHand(), event.getItemStack(), event.getPos())) {
            event.setCancellationResult(InteractionResult.SUCCESS);
            event.setCanceled(true);
        }
    }

    private void onRender(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) return;
        var camera = event.getCamera().getPosition();
        SelectionRenderer.extract(event.getCamera().getBlockPosition());
        LightOverlayRenderer.extract();

        PoseStack poseStack = new PoseStack();
        var buffers = minecraft.renderBuffers().bufferSource();
        SelectionRenderer.render(camera, poseStack, buffers);
        LightOverlayRenderer.render(camera, poseStack, buffers);
        buffers.endBatch(RenderType.lines());
        buffers.endBatch(RenderType.debugStructureQuads());
    }
}
