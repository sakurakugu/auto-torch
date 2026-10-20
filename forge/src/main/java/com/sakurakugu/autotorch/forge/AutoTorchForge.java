package com.sakurakugu.autotorch.forge;

import com.sakurakugu.autotorch.AutoTorch;
import com.sakurakugu.autotorch.server.LightingTaskManager;
import com.sakurakugu.autotorch.server.SelectionToolEvents;
import com.sakurakugu.autotorch.server.ServerConfig;
import com.sakurakugu.autotorch.server.ServerConfigNetwork;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.FMLCommonHandler;

/**
 * Forge 1.7.10 平台入口。
 * 1.7.10 的配置文件在 preInit 阶段同步加载，因此这里直接安装配置后端，
 * 不需要 1.13 的 ModConfig 加载/重载事件。
 */
@Mod(modid = AutoTorch.MOD_ID, useMetadata = true,
        acceptedMinecraftVersions = "[1.7.10]", dependencies = "required-after:Forge@[10,)")
public final class AutoTorchForge {
    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        boolean clientSide = event.getSide().isClient();
        ForgeConfigs.init(event.getModConfigurationDirectory(), clientSide);
        ServerConfig.install(ForgeConfigs.SERVER);
        ForgeNetworking.initialize();
        MinecraftForge.EVENT_BUS.register(this);
        // 1.7.10 的 TickEvent 与玩家登录/登出事件都发在 FML 总线上，需要额外注册。
        FMLCommonHandler.instance().bus().register(this);
        if (event.getSide().isClient()) AutoTorchForgeClient.initialize();
    }

    @Mod.EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        // 单机、局域网和专用服务器都注册，管理员才能用 serverconfig 命令或打开服务端配置界面。
        event.registerServerCommand(new LegacyAutoTorchServerCommand());
    }

    @SubscribeEvent public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && FMLCommonHandler.instance().getMinecraftServerInstance() != null) {
            // 收包线程只负责入队，这里在服务器线程统一执行，避免并发触碰世界状态。
            ForgeNetworking.drainServerTasks();
            LightingTaskManager.onServerTick(FMLCommonHandler.instance().getMinecraftServerInstance());
        }
    }

    @SubscribeEvent
    public void onInteract(PlayerInteractEvent event) {
        // 1.7.10 的交互事件用 Action 区分左右键，且没有手部参数。
        if ((event.action == PlayerInteractEvent.Action.LEFT_CLICK_BLOCK
                || event.action == PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK)
                && event.entityPlayer instanceof EntityPlayerMP
                && SelectionToolEvents.handlesInteraction(
                        (EntityPlayerMP) event.entityPlayer, event.entityPlayer.getHeldItem())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.player instanceof EntityPlayerMP) SelectionToolEvents.onLogout((EntityPlayerMP) event.player);
    }

    @SubscribeEvent
    public void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        // 登录后立刻下发权威服务端配置，客户端据此刷新限制与管理员界面。
        if (event.player instanceof EntityPlayerMP) ServerConfigNetwork.sendConfig((EntityPlayerMP) event.player);
    }
}
