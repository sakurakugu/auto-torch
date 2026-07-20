package com.sakurakugu.autotorch.neoforge;

import com.sakurakugu.autotorch.AutoTorch;
import com.sakurakugu.autotorch.client.AutoTorchClient;
import com.sakurakugu.autotorch.client.ClientConfig;
import com.sakurakugu.autotorch.client.LightOverlayRenderer;
import com.sakurakugu.autotorch.client.SelectionRenderer;
import com.sakurakugu.autotorch.network.PlatformNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ExtractLevelRenderStateEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

@Mod(value = AutoTorch.MOD_ID, dist = Dist.CLIENT)
public final class AutoTorchNeoForgeClient {
    private final AutoTorchClient client = new AutoTorchClient();

    public AutoTorchNeoForgeClient(IEventBus modBus, ModContainer container) {
        ClientConfig.install(NeoForgeConfigs.CLIENT);
        PlatformNetworking.installSender(ClientPacketDistributor::sendToServer);
        container.registerConfig(ModConfig.Type.CLIENT, NeoForgeConfigs.CLIENT.spec());
        modBus.addListener(this::registerKeys);
        NeoForge.EVENT_BUS.addListener(this::onTick);
        NeoForge.EVENT_BUS.addListener(this::onLeftClick);
        NeoForge.EVENT_BUS.addListener(this::onRightClick);
        NeoForge.EVENT_BUS.addListener(this::onExtract);
        NeoForge.EVENT_BUS.addListener(this::onSubmit);
    }

    private void registerKeys(RegisterKeyMappingsEvent event) {
        event.registerCategory(AutoTorchClient.CATEGORY);
        event.register(AutoTorchClient.OPEN_SCREEN);
        event.register(AutoTorchClient.TOGGLE_LIGHT_OVERLAY);
    }

    private void onTick(ClientTickEvent.Post event) { client.tick(); }

    private void onLeftClick(PlayerInteractEvent.LeftClickBlock event) {
        if (event.getLevel() instanceof net.minecraft.client.multiplayer.ClientLevel clientLevel
                && client.onLeftClick(clientLevel,
                event.getItemStack(), event.getPos(),
                event.getAction() == PlayerInteractEvent.LeftClickBlock.Action.START)) {
            event.setCanceled(true);
        }
    }

    private void onRightClick(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel() instanceof net.minecraft.client.multiplayer.ClientLevel clientLevel
                && client.onRightClick(clientLevel,
                event.getHand(), event.getItemStack(), event.getPos())) {
            event.setCancellationResult(InteractionResult.SUCCESS);
            event.setCanceled(true);
        }
    }

    private void onExtract(ExtractLevelRenderStateEvent event) {
        SelectionRenderer.extract(event.getCamera().blockPosition());
        LightOverlayRenderer.extract();
    }

    private void onSubmit(SubmitCustomGeometryEvent event) {
        if (Minecraft.getInstance().level == null) return;
        SelectionRenderer.submit(event.getLevelRenderState().cameraRenderState.pos,
                event.getPoseStack(), event.getSubmitNodeCollector());
        LightOverlayRenderer.submit(event.getLevelRenderState().cameraRenderState.pos,
                event.getPoseStack(), event.getSubmitNodeCollector());
    }
}
