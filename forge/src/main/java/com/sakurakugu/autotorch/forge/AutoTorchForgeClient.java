package com.sakurakugu.autotorch.forge;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.brigadier.CommandDispatcher;
import com.sakurakugu.autotorch.client.AutoTorchClient;
import com.sakurakugu.autotorch.client.AutoTorchClientCommands;
import com.sakurakugu.autotorch.client.AutoTorchRenderTypes;
import com.sakurakugu.autotorch.client.ClientConfig;
import com.sakurakugu.autotorch.client.LightOverlayRenderer;
import com.sakurakugu.autotorch.client.LightOverlayState;
import com.sakurakugu.autotorch.client.SelectionRenderer;
import com.sakurakugu.autotorch.network.PlatformNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ActiveRenderInfo;
import net.minecraft.client.renderer.IRenderTypeBuffer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.command.ISuggestionProvider;
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
import org.lwjgl.opengl.GL11;

final class AutoTorchForgeClient {
    private static final RenderType WATER_VISIBLE_LINES = new RenderType(
            "autotorch_water_visible_lines",
            DefaultVertexFormats.POSITION_COLOR,
            GL11.GL_LINES,
            256,
            false,
            false,
            AutoTorchForgeClient::setupWaterVisibleRenderState,
            AutoTorchForgeClient::clearWaterVisibleRenderState
    ) {};
    private final AutoTorchClient client = new AutoTorchClient();
    private CommandDispatcher<ISuggestionProvider> suggestionCommands;
    private BlockPos selectionClickPos;

    private AutoTorchForgeClient(FMLJavaModLoadingContext context) {
        ClientConfig.install(ForgeConfigs.CLIENT);
        LightOverlayRenderer.setDrownedMarkerVisibility((level, camera, marker) -> {
            Minecraft minecraft = Minecraft.getInstance();
            return minecraft.player != null
                    && isVisibleDrownedMarker(minecraft, camera, minecraft.player, marker);
        });
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
        ClientRegistry.registerKeyBinding(AutoTorchClient.TOGGLE_LIGHT_OVERLAY_RENDER_THROUGH);
    }

    private void onClientChat(ClientChatEvent event) {
        event.setCanceled(AutoTorchClientCommands.tryExecute(event.getMessage()));
    }

    private void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            client.tick();
            updateCommandSuggestions();
            if (!Minecraft.getInstance().options.keyAttack.isDown()) {
                selectionClickPos = null;
            }
        }
    }

    private void updateCommandSuggestions() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            suggestionCommands = null;
            return;
        }
        // Forge 1.16.5 没有客户端命令注册事件，直接合并到聊天框使用的命令树以提供本地补全。
        CommandDispatcher<ISuggestionProvider> commands = minecraft.player.connection.getCommands();
        if (commands != suggestionCommands) {
            AutoTorchClientCommands.register(commands);
            suggestionCommands = commands;
        }
    }

    private void onLeftClick(PlayerInteractEvent.LeftClickBlock event) {
        boolean start = selectionClickPos == null || !selectionClickPos.equals(event.getPos());
        if (event.getEntity().level instanceof ClientWorld) {
            ClientWorld clientWorld = (ClientWorld) event.getEntity().level;
            LightOverlayState.markBlockDirty(clientWorld, event.getPos());
            if (client.onLeftClick(clientWorld, event.getItemStack(), event.getPos(), start)) {
                // 1.18.2 及其以下的事件没有 START 阶段，取消破坏后还会在长按期间重复触发。
                selectionClickPos = event.getPos().immutable();
                event.setCanceled(true);
            }
        }
    }

    private void onRightClick(PlayerInteractEvent.RightClickBlock event) {
        if (event.getEntity().level instanceof ClientWorld) {
            ClientWorld clientWorld = (ClientWorld) event.getEntity().level;
            // Forge 放置方块时不总会及时触发客户端世界的方块 dirty 通知，提前标记以便渲染阶段复核。
            LightOverlayState.markBlockDirty(clientWorld, event.getPos());
            if (client.onRightClick(clientWorld, event.getHand(), event.getItemStack(), event.getPos())) {
                event.setCancellationResult(ActionResultType.SUCCESS);
                event.setCanceled(true);
            }
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
        LightOverlayRenderer.endBatches(buffers);
        buffers.endBatch(RenderType.lines());
        buffers.endBatch(AutoTorchRenderTypes.seeThroughLines());
        buffers.endBatch(SelectionRenderer.faceRenderType());
    }

    private static boolean isVisibleDrownedMarker(
            Minecraft minecraft, Vec3d camera, net.minecraft.entity.Entity cameraEntity,
            LightOverlayState.Marker marker
    ) {
        if (marker.riskType() != LightOverlayState.RiskType.DROWNED) {
            return false;
        }
        Vec3d target = new Vec3d(
                marker.pos().getX() + 0.5D,
                marker.pos().getY() + 0.0125D,
                marker.pos().getZ() + 0.5D
        );
        // 忽略流体进行射线检测，水下标记只穿过水面，不穿过实体方块。
        return minecraft.level.clip(new RayTraceContext(
                camera, target, RayTraceContext.BlockMode.COLLIDER,
                RayTraceContext.FluidMode.NONE, cameraEntity
        )).getType() == RayTraceResult.Type.MISS;
    }

    private static void setupWaterVisibleRenderState() {
        // RenderWorldLastEvent 会继承世界渲染状态，纯色线条需要显式关闭纹理。
        RenderSystem.disableTexture();
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(
                GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA
        );
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();

        RenderSystem.pushMatrix();
        RenderSystem.scalef(0.99975586F, 0.99975586F, 0.99975586F);
        RenderSystem.lineWidth(Math.max(
                2.5F,
                (float) Minecraft.getInstance().getWindow().getWidth() / 1920.0F * 2.5F
        ));
    }

    private static void clearWaterVisibleRenderState() {
        RenderSystem.lineWidth(1.0F);
        RenderSystem.popMatrix();

        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableTexture();
    }
}
