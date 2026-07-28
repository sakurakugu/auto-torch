package com.sakurakugu.autotorch.client;

import net.minecraft.client.Minecraft;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/** 使用明确语义色的普通按钮，保留原版按钮的输入、焦点和旁白行为。 */
final class ColoredButton extends Button {
    private final int backgroundColor;
    private final int hoveredColor;

    ColoredButton(int x, int y, int width, int height, Component message, OnPress onPress, int backgroundColor, int hoveredColor) {
        super(x, y, width, height, message, onPress);
        this.backgroundColor = backgroundColor;
        this.hoveredColor = hoveredColor;
    }

    @Override
    public void renderButton(PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
        boolean highlighted = isHovered() || isFocused();
        int color = highlighted ? hoveredColor : backgroundColor;
        if (!active) {
            color = 0xCC555555;
        }
        fill(poseStack, x + 1, y + 1,
                x + getWidth() - 1, y + getHeight() - 1, color);
        int outlineColor = highlighted ? 0xFFFFFFFF : 0xFFB0B0B0;
        fill(poseStack, x, y, x + getWidth(), y + 1, outlineColor);
        fill(poseStack, x, y + getHeight() - 1, x + getWidth(), y + getHeight(), outlineColor);
        fill(poseStack, x, y + 1, x + 1, y + getHeight() - 1, outlineColor);
        fill(poseStack, x + getWidth() - 1, y + 1, x + getWidth(), y + getHeight() - 1, outlineColor);
        drawCenteredString(poseStack, Minecraft.getInstance().font, getMessage(),
                x + getWidth() / 2, y + 6,
                active ? 0xFFFFFFFF : 0xFFA0A0A0);
    }
}
