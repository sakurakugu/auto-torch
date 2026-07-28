package com.sakurakugu.autotorch.forge;

import com.sakurakugu.autotorch.AutoTorch;
import com.sakurakugu.autotorch.network.ServerConfigPayload;
import com.sakurakugu.autotorch.server.LightingTaskManager;
import com.sakurakugu.autotorch.server.SelectionToolEvents;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.EnumHand;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.FMLCommonHandler;

@Mod(modid = AutoTorch.MOD_ID, useMetadata = true,
        acceptedMinecraftVersions = "[1.9.4]", dependencies = "required-after:Forge@[12,)")
public final class AutoTorchForge {
    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        ForgeConfigs.init(event.getModConfigurationDirectory());
        com.sakurakugu.autotorch.server.ServerConfig.install(ForgeConfigs.SERVER);
        com.sakurakugu.autotorch.client.ClientConfig.install(ForgeConfigs.CLIENT);
        ForgeNetworking.initialize();
        MinecraftForge.EVENT_BUS.register(this);
        if (event.getSide().isClient()) AutoTorchForgeClient.initialize();
    }

    @SubscribeEvent public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && FMLCommonHandler.instance().getMinecraftServerInstance() != null) LightingTaskManager.onServerTick(FMLCommonHandler.instance().getMinecraftServerInstance());
    }
    @SubscribeEvent public void onLeftClick(PlayerInteractEvent.LeftClickBlock event) {
        if (event.getEntityPlayer() instanceof EntityPlayerMP && SelectionToolEvents.handlesInteraction((EntityPlayerMP) event.getEntityPlayer(), event.getEntityPlayer().getHeldItem(event.getHand()))) event.setCanceled(true);
    }
    @SubscribeEvent public void onRightClick(PlayerInteractEvent.RightClickBlock event) {
        if (event.getHand() == EnumHand.MAIN_HAND && event.getEntityPlayer() instanceof EntityPlayerMP && SelectionToolEvents.handlesInteraction((EntityPlayerMP) event.getEntityPlayer(), event.getEntityPlayer().getHeldItem(event.getHand()))) {
            event.setCanceled(true);
        }
    }
    @SubscribeEvent public void onLogout(PlayerEvent.PlayerLoggedOutEvent event) { if (event.player instanceof EntityPlayerMP) SelectionToolEvents.onLogout((EntityPlayerMP) event.player); }
    @SubscribeEvent public void onLogin(PlayerEvent.PlayerLoggedInEvent event) { if (event.player instanceof EntityPlayerMP) ForgeNetworking.sendToPlayer((EntityPlayerMP) event.player, ServerConfigPayload.current()); }
}
