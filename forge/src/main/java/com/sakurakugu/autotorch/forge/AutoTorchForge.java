package com.sakurakugu.autotorch.forge;

import com.sakurakugu.autotorch.AutoTorch;
import com.sakurakugu.autotorch.network.WorldSeedPayload;
import com.sakurakugu.autotorch.server.LightingTaskManager;
import com.sakurakugu.autotorch.server.SelectionToolEvents;
import com.sakurakugu.autotorch.server.ServerConfig;
import com.sakurakugu.autotorch.server.ServerConfigNetwork;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.EnumHand;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * Forge 1.11.2 平台入口。
 * 1.11.2 的配置文件在 preInit 阶段同步加载，因此这里直接安装配置后端，
 * 不需要 1.13 的 ModConfig 加载/重载事件。
 */
@Mod(modid = AutoTorch.MOD_ID, useMetadata = true,
        acceptedMinecraftVersions = "[1.11.2]", dependencies = "required-after:forge@[13,)")
public final class AutoTorchForge {
    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        boolean clientSide = event.getSide().isClient();
        ForgeConfigs.init(event.getModConfigurationDirectory(), clientSide);
        ServerConfig.install(ForgeConfigs.SERVER);
        ForgeNetworking.initialize();
        MinecraftForge.EVENT_BUS.register(this);
        if (clientSide) AutoTorchForgeClient.initialize();
    }

    @Mod.EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        // 单机、局域网和专用服务器都注册，管理员才能用 serverconfig 命令或打开服务端配置界面。
        event.registerServerCommand(new LegacyAutoTorchServerCommand());
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && FMLCommonHandler.instance().getMinecraftServerInstance() != null) {
            LightingTaskManager.onServerTick(FMLCommonHandler.instance().getMinecraftServerInstance());
        }
    }

    @SubscribeEvent
    public void onLeftClick(PlayerInteractEvent.LeftClickBlock event) {
        if (event.getEntityPlayer() instanceof EntityPlayerMP
                && SelectionToolEvents.handlesInteraction((EntityPlayerMP) event.getEntityPlayer(),
                        event.getEntityPlayer().getHeldItem(event.getHand()))) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onRightClick(PlayerInteractEvent.RightClickBlock event) {
        if (event.getHand() == EnumHand.MAIN_HAND
                && event.getEntityPlayer() instanceof EntityPlayerMP
                && SelectionToolEvents.handlesInteraction((EntityPlayerMP) event.getEntityPlayer(),
                        event.getEntityPlayer().getHeldItem(event.getHand()))) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.player instanceof EntityPlayerMP) SelectionToolEvents.onLogout((EntityPlayerMP) event.player);
    }

    @SubscribeEvent
    public void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        // 登录后立刻下发权威服务端配置，并同步世界种子供客户端判定史莱姆区块。
        if (event.player instanceof EntityPlayerMP) {
            ServerConfigNetwork.sendConfig((EntityPlayerMP) event.player);
            ForgeNetworking.sendToPlayer((EntityPlayerMP) event.player,
                    new WorldSeedPayload(event.player.world.getSeed()));
        }
    }
}
