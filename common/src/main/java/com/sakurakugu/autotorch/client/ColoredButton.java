package com.sakurakugu.autotorch.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/** 使用明确语义色的普通按钮，保留原版按钮的输入、焦点和旁白行为。 */
final class ColoredButton extends Button {
    private static final ResourceLocation WIDGETS_LOCATION = ResourceLocation.tryBuild("minecraft", "textures/gui/widgets.png");
    private final int backgroundColor;
    private final int hoveredColor;

    ColoredButton(int x, int y, int width, int height, Component message, OnPress onPress, int backgroundColor, int hoveredColor) {
        super(x, y, width, height, message, onPress);
        this.backgroundColor = backgroundColor;
        this.hoveredColor = hoveredColor;
    }

    @Override
    public void renderButton(PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
        boolean highlighted = isHoveredOrFocused();
        int textureY = !active ? 46 : highlighted ? 86 : 66;
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0, WIDGETS_LOCATION);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        drawNineSliced(poseStack, x, y, getWidth(), getHeight(), 20, 4, 200, 20, 0, textureY);
        int color = !active ? 0xCC555555 : highlighted ? hoveredColor : backgroundColor;
        fill(poseStack, x + 2, y + 2, x + getWidth() - 2, y + getHeight() - 2, color);
        drawColoredBevel(poseStack, color);
        drawCenteredString(poseStack, Minecraft.getInstance().font, getMessage(),
                x + getWidth() / 2, y + 6, active ? 0xFFFFFFFF : 0xFFA0A0A0);
    }

    private static void drawNineSliced(PoseStack poseStack, int x, int y, int width, int height,
            int cornerWidth, int cornerHeight, int textureWidth, int textureHeight, int u, int v) {
        int left = Math.min(cornerWidth, width / 2);
        int right = Math.min(cornerWidth, width - left);
        int top = Math.min(cornerHeight, height / 2);
        int bottom = Math.min(cornerHeight, height - top);
        int centerWidth = width - left - right;
        int centerHeight = height - top - bottom;
        int sourceCenterWidth = Math.max(1, textureWidth - cornerWidth * 2);
        int sourceCenterHeight = Math.max(1, textureHeight - cornerHeight * 2);

        blitPatch(poseStack, x, y, left, top, u, v, left, top);
        blitPatch(poseStack, x + width - right, y, right, top,
                u + textureWidth - right, v, right, top);
        blitPatch(poseStack, x, y + height - bottom, left, bottom,
                u, v + textureHeight - bottom, left, bottom);
        blitPatch(poseStack, x + width - right, y + height - bottom, right, bottom,
                u + textureWidth - right, v + textureHeight - bottom, right, bottom);
        if (centerWidth > 0) {
            blitRepeatingPatch(poseStack, x + left, y, centerWidth, top,
                    u + cornerWidth, v, sourceCenterWidth, top);
            blitRepeatingPatch(poseStack, x + left, y + height - bottom, centerWidth, bottom,
                    u + cornerWidth, v + textureHeight - bottom, sourceCenterWidth, bottom);
        }
        if (centerHeight > 0) {
            blitRepeatingPatch(poseStack, x, y + top, left, centerHeight,
                    u, v + cornerHeight, left, sourceCenterHeight);
            blitRepeatingPatch(poseStack, x + width - right, y + top, right, centerHeight,
                    u + textureWidth - right, v + cornerHeight, right, sourceCenterHeight);
        }
        if (centerWidth > 0 && centerHeight > 0) {
            blitRepeatingPatch(poseStack, x + left, y + top, centerWidth, centerHeight,
                    u + cornerWidth, v + cornerHeight, sourceCenterWidth, sourceCenterHeight);
        }
    }

    private static void blitPatch(PoseStack poseStack, int x, int y, int width, int height,
            int u, int v, int sourceWidth, int sourceHeight) {
        if (width > 0 && height > 0) {
            blit(poseStack, x, y, width, height, u, v, sourceWidth, sourceHeight, 256, 256);
        }
    }

    private static void blitRepeatingPatch(PoseStack poseStack, int x, int y, int width, int height,
            int u, int v, int sourceWidth, int sourceHeight) {
        if (width <= 0 || height <= 0 || sourceWidth <= 0 || sourceHeight <= 0) return;
        for (int offsetY = 0; offsetY < height; offsetY += sourceHeight) {
            int tileHeight = Math.min(sourceHeight, height - offsetY);
            for (int offsetX = 0; offsetX < width; offsetX += sourceWidth) {
                int tileWidth = Math.min(sourceWidth, width - offsetX);
                blitPatch(poseStack, x + offsetX, y + offsetY, tileWidth, tileHeight,
                        u, v, tileWidth, tileHeight);
            }
        }
    }

    private void drawColoredBevel(PoseStack poseStack, int color) {
        int highlight = shade(color, 1.35f);
        int shadow = shade(color, 0.55f);
        int corner = shade(color, 0.78f);
        int left = x + 1;
        int top = y + 1;
        int right = x + getWidth() - 2;
        int bottom = y + getHeight() - 2;
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
