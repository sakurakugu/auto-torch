package com.sakurakugu.autotorch.forge;

import com.mojang.brigadier.CommandDispatcher;
import com.sakurakugu.autotorch.client.AutoTorchClient;
import com.sakurakugu.autotorch.client.AutoTorchClientCommands;
import com.sakurakugu.autotorch.client.ClientConfig;
import com.sakurakugu.autotorch.client.LightOverlayRenderer;
import com.sakurakugu.autotorch.client.LightOverlayState;
import com.sakurakugu.autotorch.client.SelectionRenderer;
import com.sakurakugu.autotorch.network.PlatformNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ActiveRenderInfo;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.command.ISuggestionProvider;
import net.minecraft.entity.Entity;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.client.event.ClientChatEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

final class AutoTorchForgeClient {
    private final AutoTorchClient client = new AutoTorchClient();
    private CommandDispatcher<ISuggestionProvider> suggestionCommands;
    private BlockPos selectionClickPos;

    private AutoTorchForgeClient(FMLJavaModLoadingContext context) {
        ClientConfig.install(ForgeConfigs.CLIENT);
        PlatformNetworking.installSender(ForgeNetworking::sendToServer);
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, ForgeConfigs.CLIENT.spec());

        context.getModEventBus().addListener(this::registerKeys);
        MinecraftForge.EVENT_BUS.addListener(this::onClientChat);
        MinecraftForge.EVENT_BUS.addListener(this::onRender);
        MinecraftForge.EVENT_BUS.addListener(this::onTick);
        MinecraftForge.EVENT_BUS.addListener(this::onLeftClick);
        MinecraftForge.EVENT_BUS.addListener(this::onRightClick);
    }

    static void initialize(FMLJavaModLoadingContext context) {
        new AutoTorchForgeClient(context);
    }

    private void registerKeys(FMLClientSetupEvent event) {
        ClientRegistry.registerKeyBinding(AutoTorchClient.OPEN_SCREEN);
        ClientRegistry.registerKeyBinding(AutoTorchClient.TOGGLE_LIGHT_OVERLAY);
    }

    private void onClientChat(ClientChatEvent event) {
        event.setCanceled(AutoTorchClientCommands.tryExecute(event.getMessage()));
    }

    private void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            client.tick();
            updateCommandSuggestions();
            if (!Minecraft.getInstance().gameSettings.keyBindAttack.isKeyDown()) {
                selectionClickPos = null;
            }
        }
    }

    private void updateCommandSuggestions() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            suggestionCommands = null;
            return;
        }
        // Forge 1.13.2 没有客户端命令注册事件，直接合并到聊天框使用的命令树以提供本地补全。
        CommandDispatcher<ISuggestionProvider> commands = minecraft.player.connection.func_195515_i();
        if (commands != suggestionCommands) {
            AutoTorchClientCommands.register(commands);
            suggestionCommands = commands;
        }
    }

    private void onLeftClick(PlayerInteractEvent.LeftClickBlock event) {
        boolean start = selectionClickPos == null || !selectionClickPos.equals(event.getPos());
        if (event.getEntity().world instanceof WorldClient) {
            // Forge 破坏方块路径不一定触发客户端世界的方块 dirty 通知，提前标记以便渲染阶段复核。
            LightOverlayState.markBlockDirty((WorldClient) event.getEntity().world, event.getPos());
            if (!client.onLeftClick((WorldClient) event.getEntity().world, event.getItemStack(), event.getPos(),
                    start)) {
                return;
            }
            // 1.18.2 及其以下的事件没有 START 阶段，取消破坏后还会在长按期间重复触发。
            selectionClickPos = event.getPos().toImmutable();
            event.setCanceled(true);
        }
    }

    private void onRightClick(PlayerInteractEvent.RightClickBlock event) {
        if (event.getEntity().world instanceof WorldClient) {
            // Forge 放置方块时不总会及时触发客户端世界的方块 dirty 通知，提前标记以便渲染阶段复核。
            LightOverlayState.markBlockDirty((WorldClient) event.getEntity().world, event.getPos());
            if (client.onRightClick((WorldClient) event.getEntity().world,
                    event.getHand(), event.getItemStack(), event.getPos())) {
                event.setCancellationResult(EnumActionResult.SUCCESS);
                event.setCanceled(true);
            }
        }
    }

    private void onRender(RenderWorldLastEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.world == null) return;
        // Forge 客户端 tick 可能早于原版光照传播，在渲染阶段再次复核已完成的更新。
        LightOverlayState.tick(minecraft);
        Entity viewEntity = minecraft.getRenderViewEntity();
        if (viewEntity == null) return;
        double partialTicks = event.getPartialTicks();
        // 1.13.2 的世界渲染器以观察实体的插值坐标平移顶点；真实相机坐标仅用于查询和射线检测。
        Vec3d renderOrigin = new Vec3d(
                viewEntity.lastTickPosX + (viewEntity.posX - viewEntity.lastTickPosX) * partialTicks,
                viewEntity.lastTickPosY + (viewEntity.posY - viewEntity.lastTickPosY) * partialTicks,
                viewEntity.lastTickPosZ + (viewEntity.posZ - viewEntity.lastTickPosZ) * partialTicks
        );
        Vec3d camera = ActiveRenderInfo.projectViewFromEntity(viewEntity, partialTicks);
        BlockPos cameraPos = new BlockPos(camera);
        SelectionRenderer.extract(cameraPos);
        LightOverlayRenderer.extract();

        SelectionRenderer.render(renderOrigin);
        LightOverlayRenderer.render(renderOrigin);
        if (minecraft.world.getFluidState(cameraPos).isEmpty()) {
            LightOverlayRenderer.renderWaterVisible(
                    renderOrigin, target ->
                            minecraft.world.rayTraceBlocks(camera, target) == null);
        }
    }
}
