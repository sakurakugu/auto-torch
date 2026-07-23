package com.sakurakugu.autotorch.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/** 使用明确语义色的普通按钮，保留原版按钮的输入、焦点和旁白行为。 */
final class ColoredButton extends Button {
    private final int backgroundColor;
    private final int hoveredColor;

    ColoredButton(int x, int y, int width, int height, Component message, OnPress onPress, int backgroundColor, int hoveredColor) {
        super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
        this.backgroundColor = backgroundColor;
        this.hoveredColor = hoveredColor;
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int color = isHoveredOrFocused() ? hoveredColor : backgroundColor;
        if (!active) {
            color = 0xCC555555;
        }
        graphics.fill(getX() + 1, getY() + 1, getRight() - 1, getBottom() - 1, color);
        graphics.renderOutline(getX(), getY(), getWidth(), getHeight(), isHoveredOrFocused() ? 0xFFFFFFFF : 0xFFB0B0B0);
        graphics.drawCenteredString(Minecraft.getInstance().font, getMessage(), getX() + getWidth() / 2, getY() + 6,
                active ? 0xFFFFFFFF : 0xFFA0A0A0);
    }
}
