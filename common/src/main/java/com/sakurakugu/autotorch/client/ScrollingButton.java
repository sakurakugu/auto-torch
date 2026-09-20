package com.sakurakugu.autotorch.client;

import com.mojang.blaze3d.platform.GlStateManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.Button;
import net.minecraft.util.Mth;

/** 保留旧版按钮行为，并为超出可用宽度的文字补充横向滚动。 */
final class ScrollingButton extends Button {
    ScrollingButton(int x, int y, int width, int height, String message, OnPress onPress) {
        super(x, y, width, height, message, onPress);
    }

    @Override
    public void renderButton(int mouseX, int mouseY, float partialTick) {
        Minecraft minecraft = Minecraft.getInstance();
        Font font = minecraft.font;
        minecraft.getTextureManager().bind(WIDGETS_LOCATION);
        GlStateManager.color4f(1.0F, 1.0F, 1.0F, alpha);
        int textureY = getYImage(isHovered() || isFocused());
        GlStateManager.enableBlend();
        GlStateManager.enableDepthTest();
        blit(x, y, 0, 46 + textureY * 20, width / 2, height);
        blit(x + width / 2, y, 200 - width / 2, 46 + textureY * 20, width / 2, height);
        int color = (active ? 0xFFFFFF : 0xA0A0A0) | Mth.ceil(alpha * 255.0F) << 24;
        ScrollingText.render(font, getMessage(), x + 2, y, x + width - 2, y + height, color);
    }
}
