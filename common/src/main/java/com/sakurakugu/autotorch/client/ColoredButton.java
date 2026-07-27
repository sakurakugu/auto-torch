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
        super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
        this.backgroundColor = backgroundColor;
        this.hoveredColor = hoveredColor;
    }

    @Override
    public void renderWidget(PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
        int color = isHoveredOrFocused() ? hoveredColor : backgroundColor;
        if (!active) {
            color = 0xCC555555;
        }
        fill(poseStack, getX() + 1, getY() + 1,
                getX() + getWidth() - 1, getY() + getHeight() - 1, color);
        renderOutline(poseStack, getX(), getY(), getWidth(), getHeight(),
                isHoveredOrFocused() ? 0xFFFFFFFF : 0xFFB0B0B0);
        drawCenteredString(poseStack, Minecraft.getInstance().font, getMessage(),
                getX() + getWidth() / 2, getY() + 6,
                active ? 0xFFFFFFFF : 0xFFA0A0A0);
    }
}
