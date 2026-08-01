package com.sakurakugu.autotorch.forge;

import com.sakurakugu.autotorch.client.AutoTorchClient;
import com.sakurakugu.autotorch.client.LightOverlayRenderer;
import com.sakurakugu.autotorch.client.SelectionRenderer;
import com.sakurakugu.autotorch.network.PlatformNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ActiveRenderInfo;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import com.sakurakugu.autotorch.compat.BlockPos;
import net.minecraft.util.Vec3;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import cpw.mods.fml.client.registry.ClientRegistry;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

public final class AutoTorchForgeClient {
    private final AutoTorchClient client = new AutoTorchClient();
    private BlockPos selectionClickPos;
    private AutoTorchForgeClient() {
        PlatformNetworking.installSender(ForgeNetworking::sendToServer);
        ClientRegistry.registerKeyBinding(AutoTorchClient.OPEN_SCREEN);
        ClientRegistry.registerKeyBinding(AutoTorchClient.TOGGLE_LIGHT_OVERLAY);
        MinecraftForge.EVENT_BUS.register(this);
        FMLCommonHandler.instance().bus().register(this);
    }
    static void initialize() { new AutoTorchForgeClient(); }
    @SubscribeEvent public void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            ForgeNetworking.drainClientTasks();
            client.tick();
            int attackKey = Minecraft.getMinecraft().gameSettings.keyBindAttack.getKeyCode();
            boolean attacking = attackKey < 0
                    ? Mouse.isButtonDown(attackKey + 100) : Keyboard.isKeyDown(attackKey);
            if (!attacking) selectionClickPos = null;
        }
    }
    @SubscribeEvent public void onInteract(PlayerInteractEvent event) {
        if (event.entityPlayer == null || !(event.entityPlayer.worldObj instanceof WorldClient)) return;
        BlockPos pos = new BlockPos(event.x, event.y, event.z);
        if (event.action == PlayerInteractEvent.Action.LEFT_CLICK_BLOCK) {
            boolean start = selectionClickPos == null || !selectionClickPos.equals(pos);
            if (client.onLeftClick((WorldClient) event.entityPlayer.worldObj,
                    event.entityPlayer.getHeldItem(), pos, start)) {
                selectionClickPos = pos;
                event.setCanceled(true);
            }
        } else if (event.action == PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK
                && client.onRightClick((WorldClient) event.entityPlayer.worldObj,
                        event.entityPlayer.getHeldItem(), pos)) {
            event.setCanceled(true);
        }
    }
    @SubscribeEvent public void onRender(RenderWorldLastEvent event) {
        Minecraft minecraft = Minecraft.getMinecraft(); if (minecraft.theWorld == null) return;
        EntityLivingBase view = minecraft.renderViewEntity; if (view == null) return;
        float partial = event.partialTicks;
        Vec3 origin = Vec3.createVectorHelper(view.lastTickPosX + (view.posX - view.lastTickPosX) * partial, view.lastTickPosY + (view.posY - view.lastTickPosY) * partial, view.lastTickPosZ + (view.posZ - view.lastTickPosZ) * partial);
        Vec3 camera = ActiveRenderInfo.projectViewFromEntity(view, partial);
        BlockPos cameraPos = new BlockPos(camera);
        SelectionRenderer.extract(cameraPos); LightOverlayRenderer.extract();
        SelectionRenderer.render(origin); LightOverlayRenderer.render(origin);
    }
}
