package com.sakurakugu.autotorch.forge;

import com.sakurakugu.autotorch.AutoTorch;
import com.sakurakugu.autotorch.server.LightingTaskManager;
import com.sakurakugu.autotorch.server.SelectionToolEvents;
import com.sakurakugu.autotorch.server.ServerConfig;
import com.sakurakugu.autotorch.network.ServerConfigPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;

@Mod(AutoTorch.MOD_ID)
public final class AutoTorchForge {
    public AutoTorchForge(FMLJavaModLoadingContext context) {
        ServerConfig.install(ForgeConfigs.SERVER);
        context.registerConfig(ModConfig.Type.SERVER, ForgeConfigs.SERVER.spec());
        ForgeNetworking.initialize();

        TickEvent.ServerTickEvent.Post.BUS.addListener(this::onServerTick);
        PlayerInteractEvent.LeftClickBlock.BUS.addListener(this::onLeftClick);
        PlayerInteractEvent.RightClickBlock.BUS.addListener(this::onRightClick);
        PlayerEvent.PlayerLoggedOutEvent.BUS.addListener(this::onLogout);
        PlayerEvent.PlayerLoggedInEvent.BUS.addListener(this::onLogin);

        if (FMLEnvironment.dist == Dist.CLIENT) {
            AutoTorchForgeClient.initialize(context);
        }
    }

    private void onServerTick(TickEvent.ServerTickEvent.Post event) {
        LightingTaskManager.onServerTick(event.server());
    }

    private boolean onLeftClick(PlayerInteractEvent.LeftClickBlock event) {
        if (event.getEntity() instanceof ServerPlayer player
                && SelectionToolEvents.handlesInteraction(player, event.getItemStack())) {
            return true;
        }
        return false;
    }

    private boolean onRightClick(PlayerInteractEvent.RightClickBlock event) {
        if (event.getHand() == InteractionHand.MAIN_HAND
                && event.getEntity() instanceof ServerPlayer player
                && SelectionToolEvents.handlesInteraction(player, event.getItemStack())) {
            event.setCancellationResult(InteractionResult.SUCCESS_SERVER);
            return true;
        }
        return false;
    }

    private void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) SelectionToolEvents.onLogout(player);
    }

    private void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ForgeNetworking.sendToPlayer(player, ServerConfigPayload.current());
        }
    }
}
