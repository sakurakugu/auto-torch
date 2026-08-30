package com.sakurakugu.autotorch.client;

import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/** 可复用的双端点范围滑动条。 */
public class DualRangeSlider extends Button {
    private static final ResourceLocation SLIDER_LOCATION = ResourceLocation.tryBuild("minecraft", "textures/gui/slider.png");
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
        super(x, y, width, height, Component.empty(), button -> { }, DEFAULT_NARRATION);
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
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int lowX = position(lowerValue);
        int highX = position(upperValue);
        graphics.blitNineSliced(SLIDER_LOCATION, getX(), getY(), getWidth(), getHeight(),
                20, 4, 200, 20, 0, isFocused() ? 20 : 0);
        graphics.fill(lowX, getY() + 1, highX, getY() + getHeight() - 1, 0xFF3A5F8A);
        drawThumb(graphics, lowX, draggingThumb == 1 || isThumbHovered(mouseX, mouseY, lowX));
        drawThumb(graphics, highX, draggingThumb == 2 || isThumbHovered(mouseX, mouseY, highX));
        graphics.drawCenteredString(Minecraft.getInstance().font, getMessage(),
                getX() + getWidth() / 2, getY() + 5, 0xFFFFFFFF);
    }

    private void drawThumb(GuiGraphics graphics, int x, boolean highlighted) {
        graphics.blitNineSliced(SLIDER_LOCATION, x - 4, getY(), 8, getHeight(),
                20, 4, 200, 20, 0, highlighted ? 60 : 40);
    }

    private boolean isThumbHovered(int mouseX, int mouseY, int x) {
        return mouseY >= getY() && mouseY < getY() + getHeight() && Math.abs(mouseX - x) <= 6;
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
        int value = (int) Math.round((Math.max(getX() + 4, Math.min(getX() + getWidth() - 4, mouseX))
                - (getX() + 4)) * (maxValue - minValue) / (double) (getWidth() - 8)) + minValue;
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
        return getX() + 4 + (value - minValue) * (getWidth() - 8) / (maxValue - minValue);
    }

    private int clamp(int value) { return Math.max(minValue, Math.min(maxValue, value)); }
}
