package com.sakurakugu.autotorch.forge;

import com.mojang.blaze3d.matrix.MatrixStack;
import com.sakurakugu.autotorch.client.AutoTorchClient;
import com.sakurakugu.autotorch.client.AutoTorchClientCommands;
import com.sakurakugu.autotorch.client.ClientConfig;
import com.sakurakugu.autotorch.client.LightOverlayRenderer;
import com.sakurakugu.autotorch.client.SelectionRenderer;
import com.sakurakugu.autotorch.network.PlatformNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ActiveRenderInfo;
import net.minecraft.client.renderer.IRenderTypeBuffer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.ActionResultType;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceContext;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.client.event.ClientChatEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

final class AutoTorchForgeClient {
    private final AutoTorchClient client = new AutoTorchClient();
    private BlockPos selectionClickPos;

    private AutoTorchForgeClient(FMLJavaModLoadingContext context) {
        ClientConfig.install(ForgeConfigs.CLIENT);
        PlatformNetworking.installSender(ForgeNetworking::sendToServer);
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, ForgeConfigs.CLIENT.spec());

        context.getModEventBus().addListener(this::registerKeys);
        MinecraftForge.EVENT_BUS.addListener(this::onClientChat);
        MinecraftForge.EVENT_BUS.addListener(this::onRender);
        MinecraftForge.EVENT_BUS.addListener(this::onTick);
        MinecraftForge.EVENT_BUS.addListener(this::onLeftClick);
        MinecraftForge.EVENT_BUS.addListener(this::onRightClick);
    }

    static void initialize(FMLJavaModLoadingContext context) {
        new AutoTorchForgeClient(context);
    }

    private void registerKeys(FMLClientSetupEvent event) {
        ClientRegistry.registerKeyBinding(AutoTorchClient.OPEN_SCREEN);
        ClientRegistry.registerKeyBinding(AutoTorchClient.TOGGLE_LIGHT_OVERLAY);
    }

    private void onClientChat(ClientChatEvent event) {
        event.setCanceled(AutoTorchClientCommands.tryExecute(event.getMessage()));
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
        if (event.getEntity().level instanceof ClientWorld
                && client.onLeftClick((ClientWorld) event.getEntity().level, event.getItemStack(), event.getPos(),
                start)) {
            // 1.18.2 及其以下的事件没有 START 阶段，取消破坏后还会在长按期间重复触发。
            selectionClickPos = event.getPos().immutable();
            event.setCanceled(true);
        }
    }

    private void onRightClick(PlayerInteractEvent.RightClickBlock event) {
        if (event.getEntity().level instanceof ClientWorld
                && client.onRightClick((ClientWorld) event.getEntity().level,
                event.getHand(), event.getItemStack(), event.getPos())) {
            event.setCancellationResult(ActionResultType.SUCCESS);
            event.setCanceled(true);
        }
    }

    private void onRender(RenderWorldLastEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) return;
        ActiveRenderInfo levelCamera = minecraft.gameRenderer.getMainCamera();
        Vec3d camera = levelCamera.getPosition();
        SelectionRenderer.extract(levelCamera.getBlockPosition());
        LightOverlayRenderer.extract();

        MatrixStack poseStack = event.getMatrixStack();
        IRenderTypeBuffer.Impl buffers = minecraft.renderBuffers().bufferSource();
        SelectionRenderer.render(camera, poseStack, buffers);
        LightOverlayRenderer.render(camera, poseStack, buffers);
        buffers.endBatch(RenderType.lines());
        buffers.endBatch(SelectionRenderer.faceRenderType());
        if (minecraft.level.getFluidState(levelCamera.getBlockPosition()).isEmpty()) {
            LightOverlayRenderer.renderWaterVisible(
                    camera, poseStack, buffers, target ->
                            minecraft.level.clip(new RayTraceContext(
                                    camera, target, RayTraceContext.BlockMode.COLLIDER,
                                    RayTraceContext.FluidMode.NONE, levelCamera.getEntity()
                            )).getType() == RayTraceResult.Type.MISS);
            buffers.endBatch(LightOverlayRenderer.waterVisibleRenderType());
        }
    }
}
