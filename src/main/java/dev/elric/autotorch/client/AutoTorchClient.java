package dev.sakurakugu.autotorch.client;

import org.lwjgl.glfw.GLFW;

import com.mojang.blaze3d.platform.InputConstants;

import dev.elric.autotorch.AutoTorchMod;
import dev.elric.autotorch.network.AreaShape;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Items;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ExtractLevelRenderStateEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/** 客户端入口，处理快捷键、选区交互以及选区边框的渲染事件。 */
@Mod(value = AutoTorchMod.MOD_ID, dist = Dist.CLIENT)
public final class AutoTorchClient {
    private static final KeyMapping.Category CATEGORY = new KeyMapping.Category(
            Identifier.fromNamespaceAndPath(AutoTorchMod.MOD_ID, "main")
    );
    private static final KeyMapping OPEN_SCREEN = new KeyMapping(
            "key.autotorch.open_screen",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_G,
            CATEGORY
    );

    public AutoTorchClient(IEventBus modBus) {
        modBus.addListener(this::registerKeyMappings);
        NeoForge.EVENT_BUS.addListener(this::onClientTick);
        NeoForge.EVENT_BUS.addListener(this::onLeftClick);
        NeoForge.EVENT_BUS.addListener(this::onRightClick);
        NeoForge.EVENT_BUS.addListener(this::onExtractRenderState);
        NeoForge.EVENT_BUS.addListener(this::onSubmitGeometry);
    }

    private void registerKeyMappings(RegisterKeyMappingsEvent event) {
        event.registerCategory(CATEGORY);
        event.register(OPEN_SCREEN);
    }

    private void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        BlockPos currentPosition = minecraft.player == null ? BlockPos.ZERO : minecraft.player.blockPosition();
        // 切换世界或退出存档时重置选区，避免把旧维度坐标带入新世界。
        SelectionState.updateLevel(minecraft.level, currentPosition);
        while (OPEN_SCREEN.consumeClick()) {
            if (minecraft.player != null && minecraft.screen == null) {
                minecraft.setScreen(new LightingScreen());
            }
        }
    }

    private void onLeftClick(PlayerInteractEvent.LeftClickBlock event) {
        if (!event.getLevel().isClientSide() || !event.getItemStack().is(Items.WOODEN_AXE)) {
            return;
        }
        event.setCanceled(true);
        // 长按破坏方块会连续触发事件，只在 START 阶段记录一次 A 点。
        if (event.getAction() == PlayerInteractEvent.LeftClickBlock.Action.START) {
            SelectionState.setFirst(event.getPos());
            Minecraft.getInstance().gui.setOverlayMessage(
                    Component.translatable(SelectionState.shape() == AreaShape.SPHERE
                                    ? "message.autotorch.selected_center" : "message.autotorch.selected_a",
                            formatPosition(event.getPos())), false
            );
        }
    }

    private void onRightClick(PlayerInteractEvent.RightClickBlock event) {
        if (!event.getLevel().isClientSide()
                || event.getHand() != InteractionHand.MAIN_HAND
                || !event.getItemStack().is(Items.WOODEN_AXE)) {
            return;
        }
        SelectionState.setSecond(event.getPos());
        event.setCancellationResult(InteractionResult.SUCCESS);
        event.setCanceled(true);
        Minecraft.getInstance().gui.setOverlayMessage(
                Component.translatable(SelectionState.shape() == AreaShape.SPHERE
                                ? "message.autotorch.selected_radius" : "message.autotorch.selected_b",
                        formatPosition(event.getPos())), false
        );
    }

    private void onSubmitGeometry(SubmitCustomGeometryEvent event) {
        if (Minecraft.getInstance().level != null) {
            SelectionRenderer.submit(event);
        }
    }

    private void onExtractRenderState(ExtractLevelRenderStateEvent event) {
        SelectionRenderer.extract(event);
    }

    private static String formatPosition(BlockPos pos) {
        return pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
    }
}
