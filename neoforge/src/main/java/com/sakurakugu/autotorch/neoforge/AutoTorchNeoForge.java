package com.sakurakugu.autotorch.neoforge;

import com.sakurakugu.autotorch.AutoTorch;
import com.sakurakugu.autotorch.server.LightingTaskManager;
import com.sakurakugu.autotorch.server.SelectionToolEvents;
import com.sakurakugu.autotorch.server.ServerConfig;
import com.sakurakugu.autotorch.network.ServerConfigPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@Mod(AutoTorch.MOD_ID)
public final class AutoTorchNeoForge {
    public AutoTorchNeoForge(IEventBus modBus, ModContainer container) {
        ServerConfig.install(NeoForgeConfigs.SERVER);
        container.registerConfig(ModConfig.Type.SERVER, NeoForgeConfigs.SERVER.spec());
        modBus.addListener(NeoForgeNetworking::register);
        NeoForge.EVENT_BUS.addListener(this::onServerTick);
        NeoForge.EVENT_BUS.addListener(this::onLeftClick);
        NeoForge.EVENT_BUS.addListener(this::onRightClick);
        NeoForge.EVENT_BUS.addListener(this::onLogout);
        NeoForge.EVENT_BUS.addListener(this::onLogin);
    }

    private void onServerTick(ServerTickEvent.Post event) {
        LightingTaskManager.onServerTick(event.getServer());
    }

    private void onLeftClick(PlayerInteractEvent.LeftClickBlock event) {
        if (event.getEntity() instanceof ServerPlayer player
                && SelectionToolEvents.handlesInteraction(player, event.getItemStack())) {
            event.setCanceled(true);
        }
    }

    private void onRightClick(PlayerInteractEvent.RightClickBlock event) {
        if (event.getHand() == InteractionHand.MAIN_HAND
                && event.getEntity() instanceof ServerPlayer player
                && SelectionToolEvents.handlesInteraction(player, event.getItemStack())) {
            event.setCancellationResult(InteractionResult.SUCCESS_SERVER);
            event.setCanceled(true);
        }
    }

    private void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) SelectionToolEvents.onLogout(player);
    }

    private void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            PacketDistributor.sendToPlayer(player,
                    ServerConfigPayload.current());
        }
    }
}
