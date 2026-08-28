package com.sakurakugu.autotorch.forge;

import com.sakurakugu.autotorch.client.AutoTorchClient;
import com.sakurakugu.autotorch.client.AutoTorchClientCommands;
import com.sakurakugu.autotorch.client.LightOverlayRenderer;
import com.sakurakugu.autotorch.client.LightOverlayState;
import com.sakurakugu.autotorch.client.SelectionRenderer;
import com.sakurakugu.autotorch.network.PlatformNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ActiveRenderInfo;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.client.ClientCommandHandler;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

final class AutoTorchForgeClient {
    private final AutoTorchClient client = new AutoTorchClient();
    private BlockPos selectionClickPos;
    private AutoTorchForgeClient() {
        PlatformNetworking.installSender(ForgeNetworking::sendToServer);
        ClientRegistry.registerKeyBinding(AutoTorchClient.OPEN_SCREEN);
        ClientRegistry.registerKeyBinding(AutoTorchClient.TOGGLE_LIGHT_OVERLAY);
        ClientCommandHandler.instance.registerCommand(new LegacyAutoTorchClientCommand());
        MinecraftForge.EVENT_BUS.register(this);
    }
    static void initialize() { new AutoTorchForgeClient(); }

    @SubscribeEvent public void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            client.tick();
            if (!Minecraft.getMinecraft().gameSettings.keyBindAttack.isKeyDown()) selectionClickPos = null;
        }
    }
    @SubscribeEvent public void onLeftClick(PlayerInteractEvent.LeftClickBlock event) {
        boolean start = selectionClickPos == null || !selectionClickPos.equals(event.getPos());
        if (event.getEntityPlayer() != null && event.getEntityPlayer().world instanceof WorldClient) {
            LightOverlayState.markBlockDirty((WorldClient) event.getEntityPlayer().world, event.getPos());
            if (client.onLeftClick((WorldClient) event.getEntityPlayer().world, event.getEntityPlayer().getHeldItem(event.getHand()), event.getPos(), start)) {
                selectionClickPos = event.getPos(); event.setCanceled(true);
            }
        }
    }
    @SubscribeEvent public void onRightClick(PlayerInteractEvent.RightClickBlock event) {
        if (event.getEntityPlayer() != null && event.getEntityPlayer().world instanceof WorldClient && client.onRightClick((WorldClient) event.getEntityPlayer().world, event.getHand(), event.getEntityPlayer().getHeldItem(event.getHand()), event.getPos())) {
            event.setCanceled(true);
        }
    }
    @SubscribeEvent public void onRender(RenderWorldLastEvent event) {
        Minecraft minecraft = Minecraft.getMinecraft(); if (minecraft.world == null) return;
        LightOverlayState.tick(minecraft);
        Entity view = minecraft.getRenderViewEntity(); if (view == null) return;
        float partial = event.getPartialTicks();
        Vec3d origin = new Vec3d(view.lastTickPosX + (view.posX - view.lastTickPosX) * partial, view.lastTickPosY + (view.posY - view.lastTickPosY) * partial, view.lastTickPosZ + (view.posZ - view.lastTickPosZ) * partial);
        Vec3d camera = ActiveRenderInfo.projectViewFromEntity(view, partial);
        BlockPos cameraPos = new BlockPos(camera);
        SelectionRenderer.extract(cameraPos); LightOverlayRenderer.extract();
        SelectionRenderer.render(origin); LightOverlayRenderer.render(origin);
    }
}
