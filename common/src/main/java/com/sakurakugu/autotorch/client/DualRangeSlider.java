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
        Minecraft.getInstance().getTextureManager().bindTexture(SLIDER_LOCATION);
        GlStateManager.color4f(1.0F, 1.0F, 1.0F, 1.0F); GlStateManager.enableBlend();
        drawModalRectWithCustomSizedTexture(x, y, 0, isFocused() ? 20 : 0, width, height, 256, 256);
        fill(x, y + height / 2 - 1, x + width, y + height / 2 + 1, 0xFF8A8A8A);
        int lowX = position(lowerValue);
        int highX = position(upperValue);
        fill(lowX, y + height / 2 - 2, highX, y + height / 2 + 2, 0xFF3A5F8A);
        thumb(lowX, draggingThumb == 1 || Math.abs(mouseX - lowX) <= 6);
        thumb(highX, draggingThumb == 2 || Math.abs(mouseX - highX) <= 6);
        drawCenteredString(Minecraft.getInstance().fontRenderer, getMessage(), x + width / 2, y + 6, 0xFFFFFFFF);
    }

    private void thumb(int thumbX, boolean hovered) {
        fill(thumbX - 3, y + 2, thumbX + 4, y + height - 2, hovered ? 0xFFFFFFFF : 0xFFB0B0B0);
        fill(thumbX - 2, y + 3, thumbX + 3, y + height - 3, 0xFF606060);
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
