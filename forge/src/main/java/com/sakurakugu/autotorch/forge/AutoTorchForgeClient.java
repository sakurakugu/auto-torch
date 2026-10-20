package com.sakurakugu.autotorch.forge;

import com.sakurakugu.autotorch.client.AutoTorchClient;
import com.sakurakugu.autotorch.client.AutoTorchClientCommands;
import com.sakurakugu.autotorch.client.ClientConfig;
import com.sakurakugu.autotorch.client.LightOverlayRenderer;
import com.sakurakugu.autotorch.client.LightOverlayState;
import com.sakurakugu.autotorch.client.SelectionRenderer;
import com.sakurakugu.autotorch.network.PlatformNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ActiveRenderInfo;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.entity.EntityLivingBase;
import com.sakurakugu.autotorch.compat.BlockPos;
import net.minecraft.util.Vec3;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.client.ClientCommandHandler;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import cpw.mods.fml.client.registry.ClientRegistry;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

/**
 * Forge 1.7.10 客户端入口。
 * 1.7.10 没有客户端配置加载事件，TOML 在 preInit 已同步加载，这里只负责安装配置后端、网络出口、按键与渲染事件。
 */
public final class AutoTorchForgeClient {
    private final AutoTorchClient client = new AutoTorchClient();
    private BlockPos selectionClickPos;

    private AutoTorchForgeClient() {
        // 1.7.10 的 TOML 已在 preInit 同步加载，这里直接安装即可发布第一份客户端配置快照。
        ClientConfig.install(ForgeConfigs.CLIENT);
        PlatformNetworking.installSender(ForgeNetworking::sendToServer);
        ClientRegistry.registerKeyBinding(AutoTorchClient.OPEN_SCREEN);
        ClientRegistry.registerKeyBinding(AutoTorchClient.TOGGLE_LIGHT_OVERLAY);
        ClientRegistry.registerKeyBinding(AutoTorchClient.TOGGLE_NEARBY_AUTO_TORCH);
        ClientCommandHandler.instance.registerCommand(new LegacyAutoTorchClientCommand());
        MinecraftForge.EVENT_BUS.register(this);
        FMLCommonHandler.instance().bus().register(this);
    }

    static void initialize() { new AutoTorchForgeClient(); }
    @SubscribeEvent public void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            // 收包线程只负责入队，这里在客户端线程统一执行。
            ForgeNetworking.drainClientTasks();
            Minecraft minecraft = Minecraft.getMinecraft();
            EntityLivingBase view = minecraft.renderViewEntity;
            client.tick(view == null ? BlockPos.ORIGIN
                    : new BlockPos(ActiveRenderInfo.projectViewFromEntity(view, 1.0F)));
            int attackKey = Minecraft.getMinecraft().gameSettings.keyBindAttack.getKeyCode();
            boolean attacking = attackKey < 0
                    ? Mouse.isButtonDown(attackKey + 100) : Keyboard.isKeyDown(attackKey);
            if (!attacking) selectionClickPos = null;
        }
    }

    @SubscribeEvent public void onInteract(PlayerInteractEvent event) {
        // 1.7.10 的交互事件用 Action 区分左右键，且没有手部参数。
        if (event.entityPlayer == null || !(event.entityPlayer.worldObj instanceof WorldClient)) return;
        BlockPos pos = new BlockPos(event.x, event.y, event.z);
        if (event.action == PlayerInteractEvent.Action.LEFT_CLICK_BLOCK) {
            LightOverlayState.markBlockDirty((WorldClient) event.entityPlayer.worldObj, pos);
            boolean start = selectionClickPos == null || !selectionClickPos.equals(pos);
            if (client.onLeftClick((WorldClient) event.entityPlayer.worldObj,
                    event.entityPlayer.getHeldItem(), pos, start)) {
                selectionClickPos = pos;
                event.setCanceled(true);
            }
        } else if (event.action == PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK) {
            // Forge 放置方块时不总会及时触发客户端世界的方块 dirty 通知，提前标记以便渲染阶段复核。
            LightOverlayState.markBlockDirty((WorldClient) event.entityPlayer.worldObj, pos);
            if (client.onRightClick((WorldClient) event.entityPlayer.worldObj,
                    event.entityPlayer.getHeldItem(), pos)) {
                event.setCanceled(true);
            }
        }
    }

    @SubscribeEvent public void onRender(RenderWorldLastEvent event) {
        Minecraft minecraft = Minecraft.getMinecraft(); if (minecraft.theWorld == null) return;
        EntityLivingBase view = minecraft.renderViewEntity; if (view == null) return;
        float partial = event.partialTicks;
        Vec3 origin = Vec3.createVectorHelper(view.lastTickPosX + (view.posX - view.lastTickPosX) * partial, view.lastTickPosY + (view.posY - view.lastTickPosY) * partial, view.lastTickPosZ + (view.posZ - view.lastTickPosZ) * partial);
        Vec3 camera = ActiveRenderInfo.projectViewFromEntity(view, partial);
        BlockPos cameraPos = new BlockPos(camera);
        // Forge 客户端 tick 可能早于原版光照传播，在渲染阶段再次复核已完成的更新。
        LightOverlayState.tick(minecraft, cameraPos);
        SelectionRenderer.extract(cameraPos); LightOverlayRenderer.extract();
        SelectionRenderer.render(origin); LightOverlayRenderer.render(origin);
    }
}
