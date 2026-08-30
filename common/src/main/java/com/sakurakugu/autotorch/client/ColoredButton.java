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
        int color = !active ? 0xCC555555 : isHoveredOrFocused() ? hoveredColor : backgroundColor;
        fill(poseStack, getX() + 2, getY() + 2,
                getX() + getWidth() - 2, getY() + getHeight() - 2, color);
        drawColoredBevel(poseStack, color);
        renderOutline(poseStack, getX(), getY(), getWidth(), getHeight(),
                isHoveredOrFocused() ? 0xFFFFFFFF : 0xFFB0B0B0);
        drawCenteredString(poseStack, Minecraft.getInstance().font, getMessage(),
                getX() + getWidth() / 2, getY() + 6,
                active ? 0xFFFFFFFF : 0xFFA0A0A0);
    }

    private void drawColoredBevel(PoseStack poseStack, int color) {
        int highlight = shade(color, 1.35f);
        int shadow = shade(color, 0.55f);
        int corner = shade(color, 0.78f);
        int left = getX() + 1;
        int top = getY() + 1;
        int right = getX() + getWidth() - 2;
        int bottom = getY() + getHeight() - 2;
        fill(poseStack, left + 1, top, right, top + 1, highlight);
        fill(poseStack, left, top + 1, left + 1, bottom, highlight);
        fill(poseStack, left, top, left + 1, top + 1, highlight);
        fill(poseStack, left + 1, bottom - 1, right, bottom + 1, shadow);
        fill(poseStack, right, top + 1, right + 1, bottom, shadow);
        fill(poseStack, right, bottom, right + 1, bottom + 1, shadow);
        fill(poseStack, right, top, right + 1, top + 1, corner);
        fill(poseStack, left, bottom, left + 1, bottom + 1, corner);
    }

    private static int shade(int color, float factor) {
        int red = Math.min(255, Math.round(((color >> 16) & 0xFF) * factor));
        int green = Math.min(255, Math.round(((color >> 8) & 0xFF) * factor));
        int blue = Math.min(255, Math.round((color & 0xFF) * factor));
        return 0xFF000000 | red << 16 | green << 8 | blue;
    }
}
