package com.sakurakugu.autotorch.forge;

import com.mojang.blaze3d.vertex.PoseStack;
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
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraftforge.client.event.ClientChatEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fmlclient.registry.ClientRegistry;

final class AutoTorchForgeClient {
    private final AutoTorchClient client = new AutoTorchClient();
    private CommandDispatcher<SharedSuggestionProvider> suggestionCommands;
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
        event.enqueueWork(() -> {
            ClientRegistry.registerKeyBinding(AutoTorchClient.OPEN_SCREEN);
            ClientRegistry.registerKeyBinding(AutoTorchClient.TOGGLE_LIGHT_OVERLAY);
            ClientRegistry.registerKeyBinding(AutoTorchClient.TOGGLE_LIGHT_OVERLAY_RENDER_THROUGH);
        });
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
        CommandDispatcher<SharedSuggestionProvider> commands = minecraft.player.connection.getCommands();
        if (commands != suggestionCommands) {
            AutoTorchClientCommands.register(commands);
            suggestionCommands = commands;
        }
    }

    private void onLeftClick(PlayerInteractEvent.LeftClickBlock event) {
        boolean start = selectionClickPos == null || !selectionClickPos.equals(event.getPos());
        if (event.getEntity().level instanceof ClientLevel clientLevel) {
            LightOverlayState.markBlockDirty(clientLevel, event.getPos());
            if (client.onLeftClick(clientLevel, event.getItemStack(), event.getPos(), start)) {
                selectionClickPos = event.getPos().immutable();
                event.setCanceled(true);
            }
        }
    }

    private void onRightClick(PlayerInteractEvent.RightClickBlock event) {
        if (event.getEntity().level instanceof ClientLevel clientLevel) {
            LightOverlayState.markBlockDirty(clientLevel, event.getPos());
            if (client.onRightClick(clientLevel, event.getHand(), event.getItemStack(), event.getPos())) {
                event.setCancellationResult(InteractionResult.SUCCESS);
                event.setCanceled(true);
            }
        }
    }

    private void onRender(RenderWorldLastEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) return;
        var levelCamera = minecraft.gameRenderer.getMainCamera();
        var camera = levelCamera.getPosition();
        SelectionRenderer.extract(levelCamera.getBlockPosition());
        LightOverlayRenderer.extract();
        PoseStack poseStack = event.getMatrixStack();
        var buffers = minecraft.renderBuffers().bufferSource();
        SelectionRenderer.render(camera, poseStack, buffers);
        LightOverlayRenderer.render(camera, poseStack, buffers);
        LightOverlayRenderer.endBatches(buffers);
        buffers.endBatch(RenderType.lines());
        buffers.endBatch(AutoTorchRenderTypes.seeThroughLines());
        buffers.endBatch(SelectionRenderer.faceRenderType());
    }
}
