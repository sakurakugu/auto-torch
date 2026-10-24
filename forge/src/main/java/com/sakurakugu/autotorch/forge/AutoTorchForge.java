package com.sakurakugu.autotorch.forge;

import com.sakurakugu.autotorch.AutoTorch;
import com.sakurakugu.autotorch.network.WorldSeedPayload;
import com.sakurakugu.autotorch.server.LightingTaskManager;
import com.sakurakugu.autotorch.server.AutoTorchServerCommands;
import com.sakurakugu.autotorch.server.SelectionToolEvents;
import com.sakurakugu.autotorch.server.ServerConfig;
import com.sakurakugu.autotorch.server.ServerConfigNetwork;
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
        context.getModEventBus().addListener(this::onConfigReloading);

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
        // 单机、局域网和专用服务器都注册，客户端才能拿到服务端命令树并补全 serverconfig。
        AutoTorchServerCommands.register(event.getCommandDispatcher(), server ->
                server.getPlayerList().getPlayers().forEach(player ->
                        ForgeNetworking.sendToPlayer(player, ServerConfigPayload.current())));
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
        // 登录后立刻下发权威服务端配置，并同步世界种子供客户端判定史莱姆区块。
        if (event.getPlayer() instanceof EntityPlayerMP) {
            ForgeNetworking.sendToPlayer((EntityPlayerMP) event.getPlayer(), ServerConfigPayload.current());
            ForgeNetworking.sendToPlayer((EntityPlayerMP) event.getPlayer(),
                    new WorldSeedPayload(event.getPlayer().world.getSeed()));
        }
    }

    private void onConfigLoading(ModConfig.Loading event) {
        updateServerConfig(event.getConfig());
    }

    private void onConfigReloading(ModConfig.ConfigReloading event) {
        updateServerConfig(event.getConfig());
    }

    private void updateServerConfig(ModConfig config) {
        if (config.getSpec() == ForgeConfigs.SERVER.spec()) {
            ForgeConfigs.SERVER.attach(config);
            net.minecraft.server.MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            if (server != null) server.addScheduledTask(() -> ServerConfigNetwork.broadcast(server));
        }
    }
}
