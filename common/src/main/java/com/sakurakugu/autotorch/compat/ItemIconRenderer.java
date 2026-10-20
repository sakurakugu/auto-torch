package com.sakurakugu.autotorch.compat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.item.ItemStack;

/** 1.7.10 的物品图标渲染需要显式传入字体与纹理管理器，并自行开启 GUI 物品光照。 */
public final class ItemIconRenderer {
    private static final RenderItem RENDER_ITEM = new RenderItem();

    private ItemIconRenderer() {
    }

    public static void render(ItemStack stack, int x, int y) {
        Minecraft minecraft = Minecraft.getMinecraft();
        RenderHelper.enableGUIStandardItemLighting();
        RENDER_ITEM.renderItemAndEffectIntoGUI(
                minecraft.fontRenderer, minecraft.getTextureManager(), stack, x, y);
        RenderHelper.disableStandardItemLighting();
    }

    /** 与新版 RenderItem#zLevel 对应的绘制深度，供弹出层抬高图标使用。 */
    public static float zLevel() {
        return RENDER_ITEM.zLevel;
    }

    public static void setZLevel(float zLevel) {
        RENDER_ITEM.zLevel = zLevel;
    }
}
