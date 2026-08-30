package com.sakurakugu.autotorch.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/** 使用明确语义色的普通按钮，保留原版按钮的输入、焦点和旁白行为。 */
final class ColoredButton extends Button {
    private final int backgroundColor;
    private final int hoveredColor;

    ColoredButton(int x, int y, int width, int height, Component message, OnPress onPress, int backgroundColor, int hoveredColor) {
        super(x, y, width, height, message.getString(), onPress);
        this.backgroundColor = backgroundColor;
        this.hoveredColor = hoveredColor;
    }

    @Override
    public void renderButton(int mouseX, int mouseY, float partialTick) {
        int color = !active ? 0xCC555555 : isHovered() || isFocused() ? hoveredColor : backgroundColor;
        fill(x + 2, y + 2, x + getWidth() - 2, y + height - 2, color);
        drawColoredBevel(color);
        int outlineColor = isHovered() || isFocused() ? 0xFFFFFFFF : 0xFFB0B0B0;
        fill(x, y, x + getWidth(), y + 1, outlineColor);
        fill(x, y + height - 1, x + getWidth(), y + height, outlineColor);
        fill(x, y + 1, x + 1, y + height - 1, outlineColor);
        fill(x + getWidth() - 1, y + 1, x + getWidth(), y + height - 1, outlineColor);
        drawCenteredString(Minecraft.getInstance().font, getMessage(),
                x + getWidth() / 2, y + 6, active ? 0xFFFFFFFF : 0xFFA0A0A0);
    }

    private void drawColoredBevel(int color) {
        int highlight = shade(color, 1.35f);
        int shadow = shade(color, 0.55f);
        int corner = shade(color, 0.78f);
        int left = x + 1;
        int top = y + 1;
        int right = x + getWidth() - 2;
        int bottom = y + height - 2;
        fill(left + 1, top, right, top + 1, highlight);
        fill(left, top + 1, left + 1, bottom, highlight);
        fill(left, top, left + 1, top + 1, highlight);
        fill(left + 1, bottom - 1, right, bottom + 1, shadow);
        fill(right, top + 1, right + 1, bottom, shadow);
        fill(right, bottom, right + 1, bottom + 1, shadow);
        fill(right, top, right + 1, top + 1, corner);
        fill(left, bottom, left + 1, bottom + 1, corner);
    }

    private static int shade(int color, float factor) {
        int red = Math.min(255, Math.round(((color >> 16) & 0xFF) * factor));
        int green = Math.min(255, Math.round(((color >> 8) & 0xFF) * factor));
        int blue = Math.min(255, Math.round((color & 0xFF) * factor));
        return 0xFF000000 | red << 16 | green << 8 | blue;
    }
}
