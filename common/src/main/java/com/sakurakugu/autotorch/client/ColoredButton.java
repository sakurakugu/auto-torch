package com.sakurakugu.autotorch.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/** 使用明确语义色的普通按钮，保留原版按钮的输入、焦点和旁白行为。 */
final class ColoredButton extends Button {
    private static final ResourceLocation WIDGETS_LOCATION = ResourceLocation.tryBuild("minecraft", "textures/gui/widgets.png");
    private final int backgroundColor;
    private final int hoveredColor;

    ColoredButton(int x, int y, int width, int height, Component message, OnPress onPress, int backgroundColor, int hoveredColor) {
        super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
        this.backgroundColor = backgroundColor;
        this.hoveredColor = hoveredColor;
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        boolean highlighted = isHoveredOrFocused();
        int textureY = !active ? 46 : highlighted ? 86 : 66;
        graphics.blitNineSliced(WIDGETS_LOCATION, getX(), getY(), getWidth(), getHeight(),
                20, 4, 200, 20, 0, textureY);
        int color = !active ? 0xCC555555 : highlighted ? hoveredColor : backgroundColor;
        graphics.fill(getX() + 2, getY() + 2, getX() + getWidth() - 2, getY() + getHeight() - 2, color);
        drawColoredBevel(graphics, color);
        graphics.drawCenteredString(Minecraft.getInstance().font, getMessage(), getX() + getWidth() / 2, getY() + 6,
                active ? 0xFFFFFFFF : 0xFFA0A0A0);
    }

    private void drawColoredBevel(GuiGraphics graphics, int color) {
        int highlight = shade(color, 1.35f);
        int shadow = shade(color, 0.55f);
        int corner = shade(color, 0.78f);
        int left = getX() + 1;
        int top = getY() + 1;
        int right = getX() + getWidth() - 2;
        int bottom = getY() + getHeight() - 2;
        graphics.fill(left + 1, top, right, top + 1, highlight);
        graphics.fill(left, top + 1, left + 1, bottom, highlight);
        graphics.fill(left, top, left + 1, top + 1, highlight);
        graphics.fill(left + 1, bottom - 1, right, bottom + 1, shadow);
        graphics.fill(right, top + 1, right + 1, bottom, shadow);
        graphics.fill(right, bottom, right + 1, bottom + 1, shadow);
        graphics.fill(right, top, right + 1, top + 1, corner);
        graphics.fill(left, bottom, left + 1, bottom + 1, corner);
    }

    private static int shade(int color, float factor) {
        int red = Math.min(255, Math.round(((color >> 16) & 0xFF) * factor));
        int green = Math.min(255, Math.round(((color >> 8) & 0xFF) * factor));
        int blue = Math.min(255, Math.round((color & 0xFF) * factor));
        return 0xFF000000 | red << 16 | green << 8 | blue;
    }
}
