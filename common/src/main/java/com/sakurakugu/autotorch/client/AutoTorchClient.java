package com.sakurakugu.autotorch.client;

import com.sakurakugu.autotorch.network.AreaShape;
import com.sakurakugu.autotorch.network.AreaZone;
import com.sakurakugu.autotorch.network.PlatformNetworking;
import com.sakurakugu.autotorch.network.SetSelectionToolPayload;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.client.Minecraft;
import net.minecraft.world.World;
import net.minecraft.util.BlockPos;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import org.lwjgl.input.Keyboard;

/** 客户端入口，处理快捷键、选区交互以及选区边框的渲染事件。 */
public final class AutoTorchClient {
    private static boolean openScreenRequested;
    private static boolean openServerConfigRequested;
    private World selectionToolSyncedLevel;
    private boolean syncedToolEnabled;
    private String syncedToolItem;
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
    public static final KeyBinding TOGGLE_NEARBY_AUTO_TORCH = new KeyBinding(
            "key.autotorch.toggle_nearby_auto_torch",
            Keyboard.KEY_NONE,
            CATEGORY
    );
    public void tick(BlockPos lightOverlayCenter) {
        Minecraft minecraft = Minecraft.getMinecraft();
        // 配置文件可能由平台层在其它线程重载，这里只在客户端 tick 中应用新快照。
        ClientConfig.applyPendingReload();
        if (openServerConfigRequested) {
            openServerConfigRequested = false;
            if (minecraft.thePlayer != null) minecraft.displayGuiScreen(new ServerConfigScreen());
        }
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
        LightOverlayState.tick(minecraft, lightOverlayCenter);
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
                minecraft.ingameGUI.setRecordPlaying(new ChatComponentTranslation(enabled
                        ? "message.autotorch.light_overlay_on" : "message.autotorch.light_overlay_off"), false);
            }
        }
        while (TOGGLE_NEARBY_AUTO_TORCH.isPressed()) {
            if (minecraft.thePlayer != null) {
                boolean enabled = !ClientConfig.isNearbyAutoTorchEnabled();
                ClientConfig.setNearbyAutoTorchEnabled(enabled);
                minecraft.ingameGUI.setRecordPlaying(new ChatComponentTranslation(enabled
                        ? "message.autotorch.nearby_auto_torch_on" : "message.autotorch.nearby_auto_torch_off"), false);
            }
        }
    }

    public static void requestOpenServerConfigScreen() { openServerConfigRequested = true; }

    public static void requestOpenScreen() {
        openScreenRequested = true;
    }

    public boolean onLeftClick(World level, ItemStack stack, BlockPos pos, boolean start) {
        if (!ClientConfig.isWoodenAxeSelectionEnabled()
                || !level.isRemote
                || stack == null
                || stack.getItem() != ClientConfig.selectionToolItem()) {
            return false;
        }
        // 长按破坏方块会连续触发事件，只在 START 阶段记录一次 A 点。
        if (start) {
            SelectionState.setFirst(pos);
            Minecraft.getMinecraft().ingameGUI.setRecordPlaying(
                    new ChatComponentTranslation(SelectionState.shape() == AreaShape.SPHERE
                                    ? "message.autotorch.selected_center" : "message.autotorch.selected_a",
                            formatPosition(pos)), false
            );
        }
        return true;
    }

    public boolean onRightClick(World level, ItemStack stack, BlockPos pos) {
        if (!ClientConfig.isWoodenAxeSelectionEnabled()
                || !level.isRemote
                || stack == null
                || stack.getItem() != ClientConfig.selectionToolItem()) {
            return false;
        }
        SelectionState.setSecond(pos);
        if (SelectionState.shape() == AreaShape.SPHERE) {
            AreaZone draft = SelectionState.draft(pos);
            long maxRadiusSquared = (long) AreaZone.MAX_SPHERE_RADIUS * AreaZone.MAX_SPHERE_RADIUS;
            if (draft.radiusSquared() > maxRadiusSquared) {
                Minecraft.getMinecraft().ingameGUI.setRecordPlaying(
                        new ChatComponentTranslation("message.autotorch.sphere_radius_too_large",
                                AreaZone.MAX_SPHERE_RADIUS).setChatStyle(new net.minecraft.util.ChatStyle().setColor(EnumChatFormatting.RED)), false
                );
                return true;
            }
        }
        Minecraft.getMinecraft().ingameGUI.setRecordPlaying(
                new ChatComponentTranslation(SelectionState.shape() == AreaShape.SPHERE
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
        } else if (minecraft.thePlayer != null && (minecraft.theWorld != selectionToolSyncedLevel
                || syncedToolEnabled != ClientConfig.isSelectionToolEnabled()
                || !ClientConfig.snapshot().selectionToolItem.equals(syncedToolItem))) {
            PlatformNetworking.sendToServer(
                    new SetSelectionToolPayload(ClientConfig.isSelectionToolEnabled(), ClientConfig.stringValue(com.sakurakugu.autotorch.config.ConfigDefinitions.SELECTION_TOOL_ITEM)));
            selectionToolSyncedLevel = minecraft.theWorld;
            syncedToolEnabled = ClientConfig.isSelectionToolEnabled();
            syncedToolItem = ClientConfig.snapshot().selectionToolItem;
        }
    }
}

