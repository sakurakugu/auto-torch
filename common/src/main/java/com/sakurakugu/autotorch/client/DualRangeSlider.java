package com.sakurakugu.autotorch.client;

import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/** 可复用的双端点范围滑动条。 */
public class DualRangeSlider extends Button {
    private static final ResourceLocation SLIDER_LOCATION = ResourceLocation.tryBuild("autotorch", "textures/gui/slider.png");
    private final int minValue;
    private final int maxValue;
    private final int maxSpan;
    private final BiFunction<Integer, Integer, Component> messageFactory;
    private final BiConsumer<Integer, Integer> changeListener;
    private int lowerValue;
    private int upperValue;
    private int draggingThumb;

    public DualRangeSlider(int x, int y, int width, int height, int minValue, int maxValue,
            int maxSpan, int lowerValue, int upperValue,
            BiFunction<Integer, Integer, Component> messageFactory,
            BiConsumer<Integer, Integer> changeListener) {
        super(x, y, width, height, Component.empty(), button -> { });
        if (minValue >= maxValue || maxSpan < 0) throw new IllegalArgumentException("Invalid slider range");
        this.minValue = minValue;
        this.maxValue = maxValue;
        this.maxSpan = maxSpan;
        this.messageFactory = messageFactory;
        this.changeListener = changeListener;
        setValues(lowerValue, upperValue, false);
    }

    public int lowerValue() { return lowerValue; }
    public int upperValue() { return upperValue; }

    public void setValues(int lowerValue, int upperValue) {
        setValues(lowerValue, upperValue, true);
    }

    private void setValues(int lowerValue, int upperValue, boolean notify) {
        this.lowerValue = clamp(lowerValue);
        this.upperValue = clamp(upperValue);
        if (this.lowerValue > this.upperValue) this.lowerValue = this.upperValue;
        if (this.upperValue - this.lowerValue > maxSpan) this.upperValue = clamp(this.lowerValue + maxSpan);
        setMessage(messageFactory.apply(this.lowerValue, this.upperValue));
        if (notify) changeListener.accept(this.lowerValue, this.upperValue);
    }

    @Override
    public void renderButton(PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
        int lowX = position(lowerValue);
        int highX = position(upperValue);
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0, SLIDER_LOCATION);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        drawNineSliced(poseStack, x, y, getWidth(), getHeight(), 20, 4, 200, 20, 0, isFocused() ? 20 : 0);
        fill(poseStack, lowX, y + 1, highX, y + getHeight() - 1, 0xFF3A5F8A);
        drawThumb(poseStack, lowX, draggingThumb == 1 || isThumbHovered(mouseX, mouseY, lowX));
        drawThumb(poseStack, highX, draggingThumb == 2 || isThumbHovered(mouseX, mouseY, highX));
        drawCenteredString(poseStack, Minecraft.getInstance().font, getMessage(),
                x + getWidth() / 2, y + 5, 0xFFFFFFFF);
    }

    private void drawThumb(PoseStack poseStack, int thumbX, boolean highlighted) {
        drawNineSliced(poseStack, thumbX - 4, y, 8, getHeight(), 20, 4, 200, 20, 0, highlighted ? 60 : 40);
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

    private boolean isThumbHovered(int mouseX, int mouseY, int x) {
        return mouseY >= y && mouseY < y + getHeight() && Math.abs(mouseX - x) <= 6;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0 || !isMouseOver(mouseX, mouseY)) return false;
        draggingThumb = Math.abs(mouseX - position(lowerValue)) <= Math.abs(mouseX - position(upperValue)) ? 1 : 2;
        update(mouseX);
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (draggingThumb == 0) return false;
        update(mouseX);
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        draggingThumb = 0;
        return true;
    }

    private void update(double mouseX) {
        int value = (int) Math.round((Math.max(x + 4, Math.min(x + getWidth() - 4, mouseX))
                - (x + 4)) * (maxValue - minValue) / (double) (getWidth() - 8)) + minValue;
        if (draggingThumb == 1) {
            int lower = Math.min(value, upperValue);
            int upper = upperValue;
            if (upper - lower > maxSpan) upper = lower + maxSpan;
            setValues(lower, upper);
        } else {
            int lower = lowerValue;
            int upper = Math.max(value, lower);
            if (upper - lower > maxSpan) lower = upper - maxSpan;
            setValues(lower, upper);
        }
    }

    private int position(int value) {
        return x + 4 + (value - minValue) * (getWidth() - 8) / (maxValue - minValue);
    }

    private int clamp(int value) { return Math.max(minValue, Math.min(maxValue, value)); }
}
