package com.sakurakugu.autotorch.client;

import org.lwjgl.glfw.GLFW;

import com.mojang.blaze3d.platform.InputConstants;

import com.sakurakugu.autotorch.network.AreaShape;
import com.sakurakugu.autotorch.network.AreaZone;
import com.sakurakugu.autotorch.network.PlatformNetworking;
import com.sakurakugu.autotorch.network.SetSelectionToolPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;

/** 客户端入口，处理快捷键、选区交互以及选区边框的渲染事件。 */
public final class AutoTorchClient {
    private ClientLevel selectionToolSyncedLevel;
    public static final String CATEGORY = "key.category.autotorch.main";
    public static final KeyMapping OPEN_SCREEN = new KeyMapping(
            "key.autotorch.open_screen",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_G,
            CATEGORY
    );
    public static final KeyMapping TOGGLE_LIGHT_OVERLAY = new KeyMapping(
            "key.autotorch.toggle_light_overlay",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_F7,
            CATEGORY
    );

    public void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        BlockPos currentPosition = minecraft.player == null ? BlockPos.ZERO : minecraft.player.blockPosition();
        // 切换世界或退出存档时重置选区，避免把旧维度坐标带入新世界。
        SelectionState.updateLevel(minecraft.level, currentPosition);
        LightOverlayState.tick(minecraft);
        NearbyAutoTorch.tick(minecraft);
        syncSelectionToolSetting(minecraft);
        while (OPEN_SCREEN.consumeClick()) {
            if (minecraft.player != null && minecraft.screen == null) {
                minecraft.setScreen(new LightingScreen());
            }
        }
        while (TOGGLE_LIGHT_OVERLAY.consumeClick()) {
            if (minecraft.player != null) {
                boolean enabled = LightOverlayState.toggle();
                minecraft.gui.setOverlayMessage(Component.translatable(enabled
                        ? "message.autotorch.light_overlay_on" : "message.autotorch.light_overlay_off"), false);
            }
        }
    }

    public boolean onLeftClick(ClientLevel level, ItemStack stack, BlockPos pos, boolean start) {
        if (!ClientConfig.isWoodenAxeSelectionEnabled()
                || !level.isClientSide()
                || !stack.is(Items.WOODEN_AXE)) {
            return false;
        }
        // 长按破坏方块会连续触发事件，只在 START 阶段记录一次 A 点。
        if (start) {
            SelectionState.setFirst(pos);
            Minecraft.getInstance().gui.setOverlayMessage(
                    Component.translatable(SelectionState.shape() == AreaShape.SPHERE
                                    ? "message.autotorch.selected_center" : "message.autotorch.selected_a",
                            formatPosition(pos)), false
            );
        }
        return true;
    }

    public boolean onRightClick(ClientLevel level, InteractionHand hand, ItemStack stack, BlockPos pos) {
        if (!ClientConfig.isWoodenAxeSelectionEnabled()
                || !level.isClientSide()
                || hand != InteractionHand.MAIN_HAND
                || !stack.is(Items.WOODEN_AXE)) {
            return false;
        }
        SelectionState.setSecond(pos);
        if (SelectionState.shape() == AreaShape.SPHERE) {
            AreaZone draft = SelectionState.draft(pos);
            long maxRadiusSquared = (long) AreaZone.MAX_SPHERE_RADIUS * AreaZone.MAX_SPHERE_RADIUS;
            if (draft.radiusSquared() > maxRadiusSquared) {
                Minecraft.getInstance().gui.setOverlayMessage(
                        Component.translatable("message.autotorch.sphere_radius_too_large",
                                AreaZone.MAX_SPHERE_RADIUS).withStyle(ChatFormatting.RED), false
                );
                return true;
            }
        }
        Minecraft.getInstance().gui.setOverlayMessage(
                Component.translatable(SelectionState.shape() == AreaShape.SPHERE
                                ? "message.autotorch.selected_radius" : "message.autotorch.selected_b",
                        formatPosition(pos)), false
        );
        return true;
    }

    private static String formatPosition(BlockPos pos) {
        return pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
    }

    private void syncSelectionToolSetting(Minecraft minecraft) {
        if (minecraft.level == null) {
            selectionToolSyncedLevel = null;
        } else if (minecraft.player != null && minecraft.level != selectionToolSyncedLevel) {
            PlatformNetworking.sendToServer(
                    new SetSelectionToolPayload(ClientConfig.isWoodenAxeSelectionEnabled()));
            selectionToolSyncedLevel = minecraft.level;
        }
    }
}
