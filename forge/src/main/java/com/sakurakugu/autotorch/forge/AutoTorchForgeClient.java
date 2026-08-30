package com.sakurakugu.autotorch.forge;

import com.mojang.blaze3d.framegraph.FramePass;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.resource.ResourceHandle;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.sakurakugu.autotorch.AutoTorch;
import com.sakurakugu.autotorch.client.AutoTorchClient;
import com.sakurakugu.autotorch.client.AutoTorchClientCommands;
import com.sakurakugu.autotorch.client.ClientConfig;
import com.sakurakugu.autotorch.client.LightOverlayRenderer;
import com.sakurakugu.autotorch.client.LightOverlayState;
import com.sakurakugu.autotorch.client.SelectionRenderer;
import com.sakurakugu.autotorch.network.PlatformNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelTargetBundle;
import net.minecraft.client.renderer.RenderBuffers;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionResult;
import net.minecraftforge.client.FramePassManager;
import net.minecraftforge.client.event.AddFramePassEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.GameShuttingDownEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

final class AutoTorchForgeClient {
    private final AutoTorchClient client = new AutoTorchClient();
    private OverlayPass overlayPass;

    private AutoTorchForgeClient(FMLJavaModLoadingContext context) {
        ClientConfig.install(ForgeConfigs.CLIENT);
        PlatformNetworking.installSender(ForgeNetworking::sendToServer);
        context.registerConfig(ModConfig.Type.CLIENT, ForgeConfigs.CLIENT.spec());

        RegisterKeyMappingsEvent.BUS.addListener(this::registerKeys);
        RegisterClientCommandsEvent.BUS.addListener(this::registerClientCommands);
        AddFramePassEvent.BUS.addListener(this::registerRenderPass);
        GameShuttingDownEvent.BUS.addListener(this::onShutdown);
        TickEvent.ClientTickEvent.Post.BUS.addListener(this::onTick);
        PlayerInteractEvent.LeftClickBlock.BUS.addListener(this::onLeftClick);
        PlayerInteractEvent.RightClickBlock.BUS.addListener(this::onRightClick);
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

    private void onTick(TickEvent.ClientTickEvent.Post event) {
        client.tick();
    }

    private void registerRenderPass(AddFramePassEvent event) {
        overlayPass = new OverlayPass();
        event.addPass(Identifier.fromNamespaceAndPath(AutoTorch.MOD_ID, "overlays"), overlayPass);
    }

    private void onShutdown(GameShuttingDownEvent event) {
        if (overlayPass != null) {
            overlayPass.close();
        }
    }

    private boolean onLeftClick(PlayerInteractEvent.LeftClickBlock event) {
        if (event.getEntity().level() instanceof ClientLevel clientLevel) {
            // Forge 破坏方块路径不一定触发 ClientLevel 的方块 dirty 通知，提前标记以便渲染阶段复核。
            LightOverlayState.markBlockDirty(clientLevel, event.getPos()); // 不加这个会慢半拍
            if (client.onLeftClick(clientLevel, event.getItemStack(), event.getPos(),
                    event.getAction() == PlayerInteractEvent.LeftClickBlock.Action.START)) {
                return true;
            }
        }
        return false;
    }

    private boolean onRightClick(PlayerInteractEvent.RightClickBlock event) {
        if (event.getEntity().level() instanceof ClientLevel clientLevel) {
            // Forge 放置方块时不总会及时触发客户端世界的方块 dirty 通知，提前标记以便渲染阶段复核。
            LightOverlayState.markBlockDirty(clientLevel, event.getPos());
            if (client.onRightClick(clientLevel, event.getHand(), event.getItemStack(), event.getPos())) {
                event.setCancellationResult(InteractionResult.SUCCESS);
                return true;
            }
        }
        return false;
    }

    private static final class OverlayPass implements FramePassManager.PassDefinition, AutoCloseable {
        private final RenderBuffers renderBuffers;
        private final FeatureRenderDispatcher featureRenderer;
        private ResourceHandle<RenderTarget> mainTarget;
        private SubmitNodeStorage submitNodes;

        private OverlayPass() {
            Minecraft minecraft = Minecraft.getInstance();
            renderBuffers = new RenderBuffers(1);
            featureRenderer = new FeatureRenderDispatcher(
                    renderBuffers,
                    minecraft.getModelManager(),
                    minecraft.getAtlasManager(),
                    minecraft.font,
                    minecraft.gameRenderer.gameRenderState()
            );
        }

        @Override
        public void extracts(LevelTargetBundle targets, FramePass pass, DeltaTracker deltaTracker) {
            targets.main = pass.readsAndWrites(targets.main);
            mainTarget = targets.main;

            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.level == null) {
                submitNodes = null;
                return;
            }
            // Forge 的客户端 tick 可能早于原版光照传播；在渲染阶段再次扫描以读取已完成更新的光照值。
            LightOverlayState.tick(minecraft);
            var camera = minecraft.gameRenderer.mainCamera().position();
            SelectionRenderer.extract(BlockPos.containing(camera));
            LightOverlayRenderer.extract();

            submitNodes = new SubmitNodeStorage();
            PoseStack poseStack = new PoseStack();
            SelectionRenderer.submit(camera, poseStack, submitNodes);
            LightOverlayRenderer.submit(camera, poseStack, submitNodes);
        }

        @Override
        public void executes(LevelRenderState state) {
            if (submitNodes == null || mainTarget == null) return;
            RenderSystem.outputColorTextureOverride = mainTarget.get().getColorTextureView();
            RenderSystem.outputDepthTextureOverride = mainTarget.get().getDepthTextureView();
            try {
                featureRenderer.renderAllFeatures(submitNodes);
            } finally {
                renderBuffers.endFrame();
                submitNodes = null;
                RenderSystem.outputColorTextureOverride = null;
                RenderSystem.outputDepthTextureOverride = null;
            }
        }

        @Override
        public void close() {
            featureRenderer.close();
            renderBuffers.close();
        }
    }
}
