package com.sakurakugu.autotorch.client;

import net.minecraft.client.Minecraft;
import net.minecraft.util.IChatComponent;

/** 使用明确语义色的普通按钮，保留原版按钮的输入、焦点和旁白行为。 */
final class ColoredButton extends Button {
    private final int backgroundColor;
    private final int hoveredColor;

    ColoredButton(int x, int y, int width, int height, IChatComponent message, OnPress onPress, int backgroundColor, int hoveredColor) {
        super(x, y, width, height, message.getFormattedText(), onPress);
        this.backgroundColor = backgroundColor;
        this.hoveredColor = hoveredColor;
    }

    @Override
    public void renderButton(int mouseX, int mouseY, float partialTick) {
        boolean highlighted = isHovered() || isFocused();
        int color = highlighted ? hoveredColor : backgroundColor;
        if (!active) {
            color = 0xCC555555;
        }
        fill(xPosition + 1, yPosition + 1,
                xPosition + getButtonWidth() - 1, yPosition + height - 1, color);
        int outlineColor = highlighted ? 0xFFFFFFFF : 0xFFB0B0B0;
        fill(xPosition, yPosition, xPosition + getButtonWidth(), yPosition + 1, outlineColor);
        fill(xPosition, yPosition + height - 1, xPosition + getButtonWidth(), yPosition + height, outlineColor);
        fill(xPosition, yPosition + 1, xPosition + 1, yPosition + height - 1, outlineColor);
        fill(xPosition + getButtonWidth() - 1, yPosition + 1,
                xPosition + getButtonWidth(), yPosition + height - 1, outlineColor);
        drawCenteredString(Minecraft.getMinecraft().fontRenderer, getMessage(),
                xPosition + getButtonWidth() / 2, yPosition + 6,
                active ? 0xFFFFFFFF : 0xFFA0A0A0);
    }
}
