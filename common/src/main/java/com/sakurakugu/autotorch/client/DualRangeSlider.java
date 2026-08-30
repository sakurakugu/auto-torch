package com.sakurakugu.autotorch.client;

import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.Minecraft;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.ResourceLocation;

/** 适配旧版 GuiButton 事件模型的双端点范围滑动条。 */
final class DualRangeSlider extends Button {
    private static final ResourceLocation SLIDER_LOCATION = new ResourceLocation("autotorch", "textures/gui/slider.png");
    private final int minValue;
    private final int maxValue;
    private final int maxSpan;
    private final BiFunction<Integer, Integer, ITextComponent> messageFactory;
    private final BiConsumer<Integer, Integer> changeListener;
    private int lowerValue;
    private int upperValue;
    private int draggingThumb;

    DualRangeSlider(int x, int y, int width, int height, int minValue, int maxValue, int maxSpan,
            int lowerValue, int upperValue,
            BiFunction<Integer, Integer, ITextComponent> messageFactory,
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

    @Override
    public void onClick(double mouseX, double mouseY) {
        draggingThumb = Math.abs(mouseX - position(lowerValue)) <= Math.abs(mouseX - position(upperValue)) ? 1 : 2;
        update(mouseX);
    }

    boolean drag(double mouseX) {
        if (draggingThumb == 0) return false;
        update(mouseX);
        return true;
    }

    boolean stopDrag() {
        if (draggingThumb == 0) return false;
        draggingThumb = 0;
        return true;
    }

    @Override
    protected void renderButton(int mouseX, int mouseY, float partialTicks) {
        int lowX = position(lowerValue);
        int highX = position(upperValue);
        Minecraft.getInstance().getTextureManager().bindTexture(SLIDER_LOCATION);
        GlStateManager.color4f(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.enableBlend();
        drawNineSliced(x, y, width, height, 20, 4, 200, 20, 0, isFocused() ? 20 : 0);
        fill(lowX, y + 1, highX, y + height - 1, 0xFF3A5F8A);
        // 填充区间会改变 OpenGL 颜色状态，绘制纹理滑块前恢复白色。
        GlStateManager.color4f(1.0F, 1.0F, 1.0F, 1.0F);
        thumb(lowX, draggingThumb == 1 || Math.abs(mouseX - lowX) <= 6);
        thumb(highX, draggingThumb == 2 || Math.abs(mouseX - highX) <= 6);
        drawCenteredString(Minecraft.getInstance().fontRenderer, getMessage(), x + width / 2, y + 5, 0xFFFFFFFF);
    }

    private void thumb(int thumbX, boolean hovered) {
        drawNineSliced(thumbX - 4, y, 8, height, 20, 4, 200, 20, 0, hovered ? 60 : 40);
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
            drawModalRectWithCustomSizedTexture(x, y, u, v, width, height, 256, 256);
        }
    }

    private static void blitRepeat(int x, int y, int width, int height, int u, int v,
            int sourceWidth, int sourceHeight) {
        if (width <= 0 || height <= 0) return;
        for (int oy = 0; oy < height; oy += sourceHeight) {
            for (int ox = 0; ox < width; ox += sourceWidth) {
                blitPatch(x + ox, y + oy, Math.min(sourceWidth, width - ox),
                        Math.min(sourceHeight, height - oy), u, v);
            }
        }
    }

    private void update(double mouseX) {
        int value = (int) Math.round((Math.max(x + 4, Math.min(x + width - 4, mouseX)) - (x + 4))
                * (maxValue - minValue) / (double) (width - 8)) + minValue;
        int lower = draggingThumb == 1 ? Math.min(value, upperValue) : lowerValue;
        int upper = draggingThumb == 2 ? Math.max(value, lowerValue) : upperValue;
        if (upper - lower > maxSpan) {
            if (draggingThumb == 1) upper = lower + maxSpan;
            else lower = upper - maxSpan;
        }
        setValues(lower, upper, true);
    }

    private void setValues(int lower, int upper, boolean notify) {
        lowerValue = Math.max(minValue, Math.min(maxValue, lower));
        upperValue = Math.max(lowerValue, Math.min(maxValue, upper));
        setMessage(messageFactory.apply(lowerValue, upperValue).getString());
        if (notify) changeListener.accept(lowerValue, upperValue);
    }

    private int position(int value) {
        return x + 4 + (value - minValue) * (width - 8) / (maxValue - minValue);
    }
}
