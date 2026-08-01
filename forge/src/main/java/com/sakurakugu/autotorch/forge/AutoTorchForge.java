package com.sakurakugu.autotorch.forge;

import com.sakurakugu.autotorch.AutoTorch;
import com.sakurakugu.autotorch.server.LightingTaskManager;
import com.sakurakugu.autotorch.server.AutoTorchServerCommands;
import com.sakurakugu.autotorch.server.SelectionToolEvents;
import com.sakurakugu.autotorch.server.ServerConfig;
import com.sakurakugu.autotorch.network.ServerConfigPayload;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.util.ActionResultType;
import net.minecraft.util.Hand;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
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
        MinecraftForge.EVENT_BUS.addListener((RegisterCommandsEvent event) -> AutoTorchServerCommands.register(event.getDispatcher(),
                server -> server.getPlayerList().getPlayers().forEach(player ->
                        ForgeNetworking.sendToPlayer(player, ServerConfigPayload.current()))));

        MinecraftForge.EVENT_BUS.addListener(this::onServerTick);
        MinecraftForge.EVENT_BUS.addListener(this::onLeftClick);
        MinecraftForge.EVENT_BUS.addListener(this::onRightClick);
        MinecraftForge.EVENT_BUS.addListener(this::onLogout);
        MinecraftForge.EVENT_BUS.addListener(this::onLogin);

        if (FMLEnvironment.dist == Dist.CLIENT) {
            AutoTorchForgeClient.initialize(context);
        }
    }

    private void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            LightingTaskManager.onServerTick(ServerLifecycleHooks.getCurrentServer());
        }
    }

    private void onLeftClick(PlayerInteractEvent.LeftClickBlock event) {
        if (event.getEntity() instanceof ServerPlayerEntity
                && SelectionToolEvents.handlesInteraction((ServerPlayerEntity) event.getEntity(), event.getItemStack())) {
            event.setCanceled(true);
        }
    }

    private void onRightClick(PlayerInteractEvent.RightClickBlock event) {
        if (event.getHand() == Hand.MAIN_HAND
                && event.getEntity() instanceof ServerPlayerEntity
                && SelectionToolEvents.handlesInteraction((ServerPlayerEntity) event.getEntity(), event.getItemStack())) {
            event.setCancellationResult(ActionResultType.SUCCESS);
            event.setCanceled(true);
        }
    }

    private void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayerEntity) {
            SelectionToolEvents.onLogout((ServerPlayerEntity) event.getEntity());
        }
    }

    private void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayerEntity) {
            ForgeNetworking.sendToPlayer((ServerPlayerEntity) event.getEntity(), ServerConfigPayload.current());
        }
    }
}
