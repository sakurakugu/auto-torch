package com.sakurakugu.autotorch.forge;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.sakurakugu.autotorch.client.AutoTorchClient;
import com.sakurakugu.autotorch.client.AutoTorchClientCommands;
import com.sakurakugu.autotorch.client.AutoTorchRenderTypes;
import com.sakurakugu.autotorch.client.ClientConfig;
import com.sakurakugu.autotorch.client.LightOverlayRenderer;
import com.sakurakugu.autotorch.client.LightOverlayState;
import com.sakurakugu.autotorch.client.SelectionRenderer;
import com.sakurakugu.autotorch.network.PlatformNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
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
import org.lwjgl.opengl.GL11;

final class AutoTorchForgeClient {
    private static final RenderType LIGHT_OVERLAY_LINES = new RenderType(
            "autotorch_light_overlay_lines",
            DefaultVertexFormat.POSITION_COLOR_NORMAL,
            VertexFormat.Mode.LINES,
            256,
            false,
            false,
            AutoTorchForgeClient::setupLightOverlayRenderState,
            AutoTorchForgeClient::clearLightOverlayRenderState
    ) {};
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
        event.register(AutoTorchClient.TOGGLE_LIGHT_OVERLAY_RENDER_THROUGH);
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
        if (event.getEntity().getLevel() instanceof ClientLevel clientLevel) {
            LightOverlayState.markBlockDirty(clientLevel, event.getPos());
            if (client.onLeftClick(clientLevel, event.getItemStack(), event.getPos(), start)) {
                // 1.19.2 的事件没有 START 阶段，取消破坏后还会在长按期间重复触发。
                selectionClickPos = event.getPos().immutable();
                event.setCanceled(true);
            }
        }
    }

    private void onRightClick(PlayerInteractEvent.RightClickBlock event) {
        if (event.getEntity().getLevel() instanceof ClientLevel clientLevel) {
            // Forge 放置方块时不总会及时触发客户端世界的方块 dirty 通知，提前标记以便渲染阶段复核。
            LightOverlayState.markBlockDirty(clientLevel, event.getPos());
            if (client.onRightClick(clientLevel, event.getHand(), event.getItemStack(), event.getPos())) {
                event.setCancellationResult(InteractionResult.SUCCESS);
                event.setCanceled(true);
            }
        }
    }

    private void onRender(RenderLevelStageEvent event) {
        boolean shaderTransparency = Minecraft.useShaderTransparency();
        RenderLevelStageEvent.Stage overlayStage = shaderTransparency
                ? RenderLevelStageEvent.Stage.AFTER_PARTICLES
                : RenderLevelStageEvent.Stage.AFTER_CUTOUT_BLOCKS;
        if (event.getStage() != overlayStage) {
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
        LightOverlayRenderer.endBatches(buffers);
        buffers.endBatch(RenderType.lines());
        buffers.endBatch(AutoTorchRenderTypes.seeThroughLines());
        buffers.endBatch(SelectionRenderer.faceRenderType());
        if (shaderTransparency) {
            buffers.endBatch(LIGHT_OVERLAY_LINES);
        }
    }

    private static void setupLightOverlayRenderState() {
        RenderSystem.setShader(GameRenderer::getRendertypeLinesShader);
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(
                GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA
        );
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(GL11.GL_LEQUAL);
        RenderSystem.disableCull();

        PoseStack modelView = RenderSystem.getModelViewStack();
        modelView.pushPose();
        modelView.scale(0.99975586F, 0.99975586F, 0.99975586F);
        RenderSystem.applyModelViewMatrix();

        // Fabulous 会分别合成水体和粒子；标记必须进入较后的粒子目标才不会被水面覆盖。
        if (Minecraft.useShaderTransparency()) {
            Minecraft.getInstance().levelRenderer.getParticlesTarget().bindWrite(false);
        }
        RenderSystem.lineWidth(Math.max(
                2.5F,
                (float) Minecraft.getInstance().getWindow().getWidth() / 1920.0F * 2.5F
        ));
    }

    private static void clearLightOverlayRenderState() {
        RenderSystem.lineWidth(1.0F);
        if (Minecraft.useShaderTransparency()) {
            Minecraft.getInstance().getMainRenderTarget().bindWrite(false);
        }

        PoseStack modelView = RenderSystem.getModelViewStack();
        modelView.popPose();
        RenderSystem.applyModelViewMatrix();

        RenderSystem.enableCull();
        RenderSystem.disableDepthTest();
        RenderSystem.depthFunc(GL11.GL_LEQUAL);
        RenderSystem.disableBlend();
        RenderSystem.defaultBlendFunc();
    }
}
