package com.sakurakugu.autotorch.client;

import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import com.mojang.blaze3d.platform.GlStateManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/** 可复用的双端点范围滑动条。 */
final class DualRangeSlider extends Button {
    private static final ResourceLocation SLIDER_LOCATION = new ResourceLocation("autotorch", "textures/gui/slider.png");
    private final int minValue;
    private final int maxValue;
    private final int maxSpan;
    private final BiFunction<Integer, Integer, Component> messageFactory;
    private final BiConsumer<Integer, Integer> changeListener;
    private int lowerValue;
    private int upperValue;
    private int draggingThumb;

    DualRangeSlider(int x, int y, int width, int height, int minValue, int maxValue,
            int maxSpan, int lowerValue, int upperValue,
            BiFunction<Integer, Integer, Component> messageFactory,
            BiConsumer<Integer, Integer> changeListener) {
        super(x, y, width, height, "", button -> { });
        if (minValue >= maxValue || maxSpan < 0) throw new IllegalArgumentException("Invalid slider range");
        this.minValue = minValue;
        this.maxValue = maxValue;
        this.maxSpan = maxSpan;
        this.messageFactory = messageFactory;
        this.changeListener = changeListener;
        setValues(lowerValue, upperValue, false);
    }

    private void setValues(int lowerValue, int upperValue, boolean notify) {
        this.lowerValue = clamp(lowerValue);
        this.upperValue = clamp(upperValue);
        if (this.lowerValue > this.upperValue) this.lowerValue = this.upperValue;
        if (this.upperValue - this.lowerValue > maxSpan) this.upperValue = clamp(this.lowerValue + maxSpan);
        setMessage(messageFactory.apply(this.lowerValue, this.upperValue).getString());
        if (notify) changeListener.accept(this.lowerValue, this.upperValue);
    }

    @Override
    public void renderButton(int mouseX, int mouseY, float partialTick) {
        int lowX = position(lowerValue);
        int highX = position(upperValue);
        Minecraft.getInstance().getTextureManager().bind(SLIDER_LOCATION);
        GlStateManager.color4f(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.enableBlend();
        GlStateManager.enableDepthTest();
        drawNineSliced(x, y, width, height, 20, 4, 200, 20, 0, isFocused() ? 20 : 0);
        fill(lowX, y + 1, highX, y + height - 1, 0xFF3A5F8A);
        // fill 会改变 OpenGL 颜色状态，纹理滑块必须恢复白色避免被区间颜色染色。
        GlStateManager.color4f(1.0F, 1.0F, 1.0F, 1.0F);
        drawThumb(lowX, draggingThumb == 1 || isThumbHovered(mouseX, mouseY, lowX));
        drawThumb(highX, draggingThumb == 2 || isThumbHovered(mouseX, mouseY, highX));
        drawCenteredString(Minecraft.getInstance().font, getMessage(), x + width / 2, y + 5, 0xFFFFFFFF);
    }

    private void drawThumb(int thumbX, boolean highlighted) {
        drawNineSliced(thumbX - 4, y, 8, height, 20, 4, 200, 20, 0, highlighted ? 60 : 40);
    }

    private static void drawNineSliced(int x, int y, int width, int height, int cw, int ch,
            int tw, int th, int u, int v) {
        int l = Math.min(cw, width / 2);
        int r = Math.min(cw, width - l);
        int t = Math.min(ch, height / 2);
        int b = Math.min(ch, height - t);
        int mw = width - l - r;
        int mh = height - t - b;
        int sw = Math.max(1, tw - cw * 2);
        int sh = Math.max(1, th - ch * 2);
        blitPatch(x, y, l, t, u, v);
        blitPatch(x + width - r, y, r, t, u + tw - r, v);
        blitPatch(x, y + height - b, l, b, u, v + th - b);
        blitPatch(x + width - r, y + height - b, r, b, u + tw - r, v + th - b);
        if (mw > 0) {
            blitRepeat(x + l, y, mw, t, u + cw, v, sw, t);
            blitRepeat(x + l, y + height - b, mw, b, u + cw, v + th - b, sw, b);
        }
        if (mh > 0) {
            blitRepeat(x, y + t, l, mh, u, v + ch, l, sh);
            blitRepeat(x + width - r, y + t, r, mh, u + tw - r, v + ch, r, sh);
        }
        if (mw > 0 && mh > 0) {
            blitRepeat(x + l, y + t, mw, mh, u + cw, v + ch, sw, sh);
        }
    }

    private static void blitPatch(int x, int y, int width, int height, int u, int v) {
        if (width > 0 && height > 0) {
            GuiComponent.blit(x, y, 0, (float) u, (float) v, width, height, 256, 256);
        }
    }

    private static void blitRepeat(int x, int y, int width, int height, int u, int v,
            int sourceWidth, int sourceHeight) {
        if (width <= 0 || height <= 0) {
            return;
        }
        for (int oy = 0; oy < height; oy += sourceHeight) {
            for (int ox = 0; ox < width; ox += sourceWidth) {
                int patchWidth = Math.min(sourceWidth, width - ox);
                int patchHeight = Math.min(sourceHeight, height - oy);
                blitPatch(x + ox, y + oy, patchWidth, patchHeight, u, v);
            }
        }
    }

    private boolean isThumbHovered(int mouseX, int mouseY, int thumbX) {
        return mouseY >= y && mouseY < y + height && Math.abs(mouseX - thumbX) <= 6;
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
        int value = (int) Math.round((Math.max(x + 4, Math.min(x + width - 4, mouseX)) - (x + 4))
                * (maxValue - minValue) / (double) (width - 8)) + minValue;
        if (draggingThumb == 1) {
            int lower = Math.min(value, upperValue);
            int upper = upperValue;
            if (upper - lower > maxSpan) upper = lower + maxSpan;
            setValues(lower, upper, true);
        } else {
            int lower = lowerValue;
            int upper = Math.max(value, lower);
            if (upper - lower > maxSpan) lower = upper - maxSpan;
            setValues(lower, upper, true);
        }
    }

    private int position(int value) {
        return x + 4 + (value - minValue) * (width - 8) / (maxValue - minValue);
    }

    private int clamp(int value) {
        return Math.max(minValue, Math.min(maxValue, value));
    }
}
