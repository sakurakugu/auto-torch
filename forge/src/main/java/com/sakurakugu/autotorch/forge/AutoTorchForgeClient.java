package com.sakurakugu.autotorch.forge;

import com.mojang.blaze3d.framegraph.FramePass;
import com.mojang.blaze3d.vertex.PoseStack;
import com.sakurakugu.autotorch.AutoTorch;
import com.sakurakugu.autotorch.client.AutoTorchClient;
import com.sakurakugu.autotorch.client.ClientConfig;
import com.sakurakugu.autotorch.client.LightOverlayRenderer;
import com.sakurakugu.autotorch.client.SelectionRenderer;
import com.sakurakugu.autotorch.network.PlatformNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelTargetBundle;
import net.minecraft.client.renderer.state.LevelRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionResult;
import net.minecraftforge.client.FramePassManager;
import net.minecraftforge.client.event.AddFramePassEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
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

        RegisterKeyMappingsEvent.BUS.addListener(this::registerKeys);
        AddFramePassEvent.BUS.addListener(this::registerRenderPass);
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
    }

    private void onTick(TickEvent.ClientTickEvent.Post event) {
        client.tick();
    }

    private void registerRenderPass(AddFramePassEvent event) {
        event.addPass(Identifier.fromNamespaceAndPath(AutoTorch.MOD_ID, "overlays"), new OverlayPass());
    }

    private boolean onLeftClick(PlayerInteractEvent.LeftClickBlock event) {
        if (event.getEntity().level() instanceof ClientLevel clientLevel
                && client.onLeftClick(clientLevel, event.getItemStack(), event.getPos(),
                event.getAction() == PlayerInteractEvent.LeftClickBlock.Action.START)) {
            return true;
        }
        return false;
    }

    private boolean onRightClick(PlayerInteractEvent.RightClickBlock event) {
        if (event.getEntity().level() instanceof ClientLevel clientLevel
                && client.onRightClick(clientLevel, event.getHand(), event.getItemStack(), event.getPos())) {
            event.setCancellationResult(InteractionResult.SUCCESS);
            return true;
        }
        return false;
    }

    private static final class OverlayPass implements FramePassManager.PassDefinition {
        @Override
        public void extracts(LevelTargetBundle targets, FramePass pass, DeltaTracker deltaTracker) {
            targets.main = pass.readsAndWrites(targets.main);

            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.level == null) {
                return;
            }
            var camera = minecraft.gameRenderer.getMainCamera().position();
            SelectionRenderer.extract(BlockPos.containing(camera));
            LightOverlayRenderer.extract();

            PoseStack poseStack = new PoseStack();
            // 复用原版主通道的提交队列，使标记先于透明水体绘制，同时保留实体方块的深度遮挡。
            var submitNodes = minecraft.gameRenderer.getSubmitNodeStorage();
            SelectionRenderer.submit(camera, poseStack, submitNodes);
            LightOverlayRenderer.submit(camera, poseStack, submitNodes);
        }

        @Override
        public void executes(LevelRenderState state) {
            // 几何已由原版主世界通道消费，此帧图通道仅提供提取时机。
        }
    }
}
