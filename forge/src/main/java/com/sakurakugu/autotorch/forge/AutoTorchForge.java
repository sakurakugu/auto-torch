package com.sakurakugu.autotorch.forge;

import com.sakurakugu.autotorch.AutoTorch;
import com.sakurakugu.autotorch.server.LightingTaskManager;
import com.sakurakugu.autotorch.server.AutoTorchServerCommands;
import com.sakurakugu.autotorch.server.SelectionToolEvents;
import com.sakurakugu.autotorch.server.ServerConfig;
import com.sakurakugu.autotorch.network.ServerConfigPayload;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.server.FMLServerStartingEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.server.ServerLifecycleHooks;

@Mod(AutoTorch.MOD_ID)
public final class AutoTorchForge {
    public AutoTorchForge() {
        FMLJavaModLoadingContext context = FMLJavaModLoadingContext.get();
        ServerConfig.install(ForgeConfigs.SERVER);
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, ForgeConfigs.SERVER.spec());
        ForgeNetworking.initialize();
        context.getModEventBus().addListener(this::onConfigLoading);

        MinecraftForge.EVENT_BUS.addListener(this::onServerStarting);
        MinecraftForge.EVENT_BUS.addListener(this::onServerTick);
        MinecraftForge.EVENT_BUS.addListener(this::onLeftClick);
        MinecraftForge.EVENT_BUS.addListener(this::onRightClick);
        MinecraftForge.EVENT_BUS.addListener(this::onLogout);
        MinecraftForge.EVENT_BUS.addListener(this::onLogin);

        if (FMLEnvironment.dist == Dist.CLIENT) {
            AutoTorchForgeClient.initialize(context);
        }
    }

    private void onServerStarting(FMLServerStartingEvent event) {
        if (event.getServer().isDedicatedServer()) {
            AutoTorchServerCommands.register(event.getCommandDispatcher(), server ->
                    server.getPlayerList().getPlayers().forEach(player ->
                            ForgeNetworking.sendToPlayer(player, ServerConfigPayload.current())));
        }
    }

    private void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            LightingTaskManager.onServerTick(ServerLifecycleHooks.getCurrentServer());
        }
    }

    private void onLeftClick(PlayerInteractEvent.LeftClickBlock event) {
        if (event.getEntity() instanceof EntityPlayerMP
                && SelectionToolEvents.handlesInteraction((EntityPlayerMP) event.getEntity(), event.getItemStack())) {
            event.setCanceled(true);
        }
    }

    private void onRightClick(PlayerInteractEvent.RightClickBlock event) {
        if (event.getHand() == EnumHand.MAIN_HAND
                && event.getEntity() instanceof EntityPlayerMP
                && SelectionToolEvents.handlesInteraction((EntityPlayerMP) event.getEntity(), event.getItemStack())) {
            event.setCancellationResult(EnumActionResult.SUCCESS);
            event.setCanceled(true);
        }
    }

    private void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getPlayer() instanceof EntityPlayerMP) {
            SelectionToolEvents.onLogout((EntityPlayerMP) event.getPlayer());
        }
    }

    private void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getPlayer() instanceof EntityPlayerMP) {
            ForgeNetworking.sendToPlayer((EntityPlayerMP) event.getPlayer(), ServerConfigPayload.current());
        }
    }

    private void onConfigLoading(ModConfig.Loading event) {
        if (event.getConfig().getType() == ModConfig.Type.CLIENT) {
            ForgeConfigs.CLIENT.attach(event.getConfig());
        } else if (event.getConfig().getType() == ModConfig.Type.SERVER) {
            ForgeConfigs.SERVER.attach(event.getConfig());
        }
    }
}
