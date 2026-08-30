package com.sakurakugu.autotorch.client;

import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** 可复用的双端点范围滑动条。 */
public class DualRangeSlider extends Button {
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
    protected void renderContents(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int trackY = getY() + getHeight() / 2 - 1;
        graphics.fill(getX() + 4, trackY, getRight() - 4, trackY + 3, 0xFF6E6E6E);
        int lowX = position(lowerValue);
        int highX = position(upperValue);
        graphics.fill(lowX, trackY, highX, trackY + 3, 0xFF3A5F8A);
        drawThumb(graphics, lowX, draggingThumb == 1 || isThumbHovered(mouseX, mouseY, lowX));
        drawThumb(graphics, highX, draggingThumb == 2 || isThumbHovered(mouseX, mouseY, highX));
        graphics.drawCenteredString(Minecraft.getInstance().font, getMessage(),
                getX() + getWidth() / 2, getY() + 5, 0xFFFFFFFF);
    }

    private void drawThumb(GuiGraphics graphics, int x, boolean highlighted) {
        int color = highlighted ? 0xFFFFFFFF : 0xFFC0C0C0;
        graphics.fill(x - 3, getY() + 2, x + 4, getBottom() - 2, color);
        graphics.renderOutline(x - 3, getY() + 2, 7, getHeight() - 4, 0xFF404040);
    }

    private boolean isThumbHovered(int mouseX, int mouseY, int x) {
        return mouseY >= getY() && mouseY < getBottom() && Math.abs(mouseX - x) <= 6;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() != 0 || !isMouseOver(event.x(), event.y())) return false;
        draggingThumb = Math.abs(event.x() - position(lowerValue)) <= Math.abs(event.x() - position(upperValue)) ? 1 : 2;
        update(event.x());
        return true;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
        if (draggingThumb == 0) return false;
        update(event.x());
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        draggingThumb = 0;
        return true;
    }

    private void update(double mouseX) {
        int value = (int) Math.round((Math.max(getX() + 4, Math.min(getRight() - 4, mouseX))
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
