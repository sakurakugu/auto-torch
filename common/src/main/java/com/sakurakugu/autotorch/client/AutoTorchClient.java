package com.sakurakugu.autotorch.client;

import com.sakurakugu.autotorch.network.AreaShape;
import com.sakurakugu.autotorch.network.AreaZone;
import com.sakurakugu.autotorch.network.PlatformNetworking;
import com.sakurakugu.autotorch.network.SetSelectionToolPayload;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.client.Minecraft;
import net.minecraft.world.World;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.EnumHand;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import org.lwjgl.input.Keyboard;

/** 客户端入口，处理快捷键、选区交互以及选区边框的渲染事件。 */
public final class AutoTorchClient {
    private static boolean openScreenRequested;
    private World selectionToolSyncedLevel;
    public static final String CATEGORY = "key.category.autotorch.main";
    public static final KeyBinding OPEN_SCREEN = new KeyBinding(
            "key.autotorch.open_screen",
            Keyboard.KEY_G,
            CATEGORY
    );
    public static final KeyBinding TOGGLE_LIGHT_OVERLAY = new KeyBinding(
            "key.autotorch.toggle_light_overlay",
            Keyboard.KEY_F7,
            CATEGORY
    );

    public void tick() {
        Minecraft minecraft = Minecraft.getMinecraft();
        // 处理打开选区面板的请求，避免在 tick 中直接打开 GUI 导致的异常。(仅限 Fabric 端的bug)
        if (openScreenRequested) {
            openScreenRequested = false;
            if (minecraft.thePlayer != null && minecraft.currentScreen == null) {
                minecraft.displayGuiScreen(new LightingScreen());
            }
        }
        BlockPos currentPosition = minecraft.thePlayer == null
                ? BlockPos.ORIGIN : minecraft.thePlayer.getPosition();
        // 切换世界或退出存档时重置选区，避免把旧维度坐标带入新世界。
        SelectionState.updateLevel(minecraft.theWorld, currentPosition);
        LightOverlayState.tick(minecraft);
        NearbyAutoTorch.tick(minecraft);
        syncSelectionToolSetting(minecraft);
        while (OPEN_SCREEN.isPressed()) {
            if (minecraft.thePlayer != null && minecraft.currentScreen == null) {
                minecraft.displayGuiScreen(new LightingScreen());
            }
        }
        while (TOGGLE_LIGHT_OVERLAY.isPressed()) {
            if (minecraft.thePlayer != null) {
                boolean enabled = LightOverlayState.toggle();
                minecraft.ingameGUI.setRecordPlaying(new TextComponentTranslation(enabled
                        ? "message.autotorch.light_overlay_on" : "message.autotorch.light_overlay_off"), false);
            }
        }
    }

    public static void requestOpenScreen() {
        openScreenRequested = true;
    }

    public boolean onLeftClick(World level, ItemStack stack, BlockPos pos, boolean start) {
        if (!ClientConfig.isWoodenAxeSelectionEnabled()
                || !level.isRemote
                || stack == null
                || stack.getItem() != Items.WOODEN_AXE) {
            return false;
        }
        // 长按破坏方块会连续触发事件，只在 START 阶段记录一次 A 点。
        if (start) {
            SelectionState.setFirst(pos);
            Minecraft.getMinecraft().ingameGUI.setRecordPlaying(
                    new TextComponentTranslation(SelectionState.shape() == AreaShape.SPHERE
                                    ? "message.autotorch.selected_center" : "message.autotorch.selected_a",
                            formatPosition(pos)), false
            );
        }
        return true;
    }

    public boolean onRightClick(World level, EnumHand hand, ItemStack stack, BlockPos pos) {
        if (!ClientConfig.isWoodenAxeSelectionEnabled()
                || !level.isRemote
                || hand != EnumHand.MAIN_HAND
                || stack == null
                || stack.getItem() != Items.WOODEN_AXE) {
            return false;
        }
        SelectionState.setSecond(pos);
        if (SelectionState.shape() == AreaShape.SPHERE) {
            AreaZone draft = SelectionState.draft(pos);
            long maxRadiusSquared = (long) AreaZone.MAX_SPHERE_RADIUS * AreaZone.MAX_SPHERE_RADIUS;
            if (draft.radiusSquared() > maxRadiusSquared) {
                Minecraft.getMinecraft().ingameGUI.setRecordPlaying(
                        new TextComponentTranslation("message.autotorch.sphere_radius_too_large",
                                AreaZone.MAX_SPHERE_RADIUS).setStyle(new net.minecraft.util.text.Style().setColor(TextFormatting.RED)), false
                );
                return true;
            }
        }
        Minecraft.getMinecraft().ingameGUI.setRecordPlaying(
                new TextComponentTranslation(SelectionState.shape() == AreaShape.SPHERE
                                ? "message.autotorch.selected_radius" : "message.autotorch.selected_b",
                        formatPosition(pos)), false
        );
        return true;
    }

    private static String formatPosition(BlockPos pos) {
        return pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
    }

    private void syncSelectionToolSetting(Minecraft minecraft) {
        if (minecraft.theWorld == null) {
            selectionToolSyncedLevel = null;
        } else if (minecraft.thePlayer != null && minecraft.theWorld != selectionToolSyncedLevel) {
            PlatformNetworking.sendToServer(
                    new SetSelectionToolPayload(ClientConfig.isWoodenAxeSelectionEnabled()));
            selectionToolSyncedLevel = minecraft.theWorld;
        }
    }
}

