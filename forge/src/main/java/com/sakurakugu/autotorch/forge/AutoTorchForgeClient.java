package com.sakurakugu.autotorch.forge;

import com.sakurakugu.autotorch.client.AutoTorchClient;
import com.sakurakugu.autotorch.client.AutoTorchClientCommands;
import com.sakurakugu.autotorch.client.ClientConfig;
import com.sakurakugu.autotorch.client.LightOverlayRenderer;
import com.sakurakugu.autotorch.client.SelectionRenderer;
import com.sakurakugu.autotorch.network.PlatformNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

final class AutoTorchForgeClient {
    private final AutoTorchClient client = new AutoTorchClient();
    private BlockPos selectionClickPos;

    private AutoTorchForgeClient(FMLJavaModLoadingContext context) {
        ClientConfig.install(ForgeConfigs.CLIENT);
        PlatformNetworking.installSender(ForgeNetworking::sendToServer);
        context.registerConfig(ModConfig.Type.CLIENT, ForgeConfigs.CLIENT.spec());

        context.getModEventBus().addListener(this::registerKeys);
        MinecraftForge.EVENT_BUS.addListener(this::registerClientCommands);
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

    private void registerClientCommands(RegisterClientCommandsEvent event) {
        AutoTorchClientCommands.register(event.getDispatcher());
    }

    private void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            client.tick();
            if (!Minecraft.getInstance().options.keyAttack.isDown()) {
                selectionClickPos = null;
            }
        }
    }

    private void onLeftClick(PlayerInteractEvent.LeftClickBlock event) {
        boolean start = selectionClickPos == null || !selectionClickPos.equals(event.getPos());
        if (event.getEntity().getLevel() instanceof ClientLevel clientLevel
                && client.onLeftClick(clientLevel, event.getItemStack(), event.getPos(),
                start)) {
            // 1.19.4 的事件没有 START 阶段，取消破坏后还会在长按期间重复触发。
            selectionClickPos = event.getPos().immutable();
            event.setCanceled(true);
        }
    }

    private void onRightClick(PlayerInteractEvent.RightClickBlock event) {
        if (event.getEntity().getLevel() instanceof ClientLevel clientLevel
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

        var poseStack = event.getPoseStack();
        var buffers = minecraft.renderBuffers().bufferSource();
        SelectionRenderer.render(camera, poseStack, buffers);
        LightOverlayRenderer.render(camera, poseStack, buffers);
        buffers.endBatch(RenderType.lines());
        buffers.endBatch(SelectionRenderer.faceRenderType());
    }
}
