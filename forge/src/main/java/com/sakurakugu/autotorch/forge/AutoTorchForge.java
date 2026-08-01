package com.sakurakugu.autotorch.forge;

import com.sakurakugu.autotorch.AutoTorch;
import com.sakurakugu.autotorch.network.ServerConfigPayload;
import com.sakurakugu.autotorch.server.LightingTaskManager;
import com.sakurakugu.autotorch.server.SelectionToolEvents;
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

@Mod(modid = AutoTorch.MOD_ID, useMetadata = true,
        acceptedMinecraftVersions = "[1.7.10]", dependencies = "required-after:Forge@[10,)")
public final class AutoTorchForge {
    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        ForgeConfigs.init(event.getModConfigurationDirectory());
        com.sakurakugu.autotorch.server.ServerConfig.install(ForgeConfigs.SERVER);
        com.sakurakugu.autotorch.client.ClientConfig.install(ForgeConfigs.CLIENT);
        ForgeNetworking.initialize();
        MinecraftForge.EVENT_BUS.register(this);
        FMLCommonHandler.instance().bus().register(this);
        if (event.getSide().isClient()) AutoTorchForgeClient.initialize();
    }

    @Mod.EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        event.registerServerCommand(new LegacyAutoTorchServerCommand());
    }

    @SubscribeEvent public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && FMLCommonHandler.instance().getMinecraftServerInstance() != null) {
            ForgeNetworking.drainServerTasks();
            LightingTaskManager.onServerTick(FMLCommonHandler.instance().getMinecraftServerInstance());
        }
    }
    @SubscribeEvent public void onInteract(PlayerInteractEvent event) {
        if ((event.action == PlayerInteractEvent.Action.LEFT_CLICK_BLOCK
                || event.action == PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK)
                && event.entityPlayer instanceof EntityPlayerMP
                && SelectionToolEvents.handlesInteraction(
                        (EntityPlayerMP) event.entityPlayer, event.entityPlayer.getHeldItem())) {
            event.setCanceled(true);
        }
    }
    @SubscribeEvent public void onLogout(PlayerEvent.PlayerLoggedOutEvent event) { if (event.player instanceof EntityPlayerMP) SelectionToolEvents.onLogout((EntityPlayerMP) event.player); }
    @SubscribeEvent public void onLogin(PlayerEvent.PlayerLoggedInEvent event) { if (event.player instanceof EntityPlayerMP) ForgeNetworking.sendToPlayer((EntityPlayerMP) event.player, ServerConfigPayload.current()); }
}
