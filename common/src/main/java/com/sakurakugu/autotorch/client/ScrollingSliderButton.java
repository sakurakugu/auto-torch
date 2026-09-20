package com.sakurakugu.autotorch.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.util.Mth;

/** 为旧版原版样式滑块补充长文本横向滚动。 */
abstract class ScrollingSliderButton extends AbstractSliderButton {
    protected ScrollingSliderButton(int x, int y, int width, int height, double value) {
        super(x, y, width, height, value);
    }

    @Override
    public void renderButton(int mouseX, int mouseY, float partialTick) {
        Minecraft minecraft = Minecraft.getInstance();
        Font font = minecraft.font;
        minecraft.getTextureManager().bind(WIDGETS_LOCATION);
        RenderSystem.color4f(1.0F, 1.0F, 1.0F, alpha);
        int textureY = getYImage(isHovered() || isFocused());
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();
        blit(x, y, 0, 46 + textureY * 20, width / 2, height);
        blit(x + width / 2, y, 200 - width / 2, 46 + textureY * 20, width / 2, height);
        renderBg(minecraft, mouseX, mouseY);
        int color = (active ? 0xFFFFFF : 0xA0A0A0) | Mth.ceil(alpha * 255.0F) << 24;
        ScrollingText.render(font, getMessage(), x + 2, y, x + width - 2, y + height, color);
    }
}
