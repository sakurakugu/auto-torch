package com.sakurakugu.autotorch.client;

import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/** 可复用的双端点范围滑动条。 */
final class DualRangeSlider extends Button {
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
        fill(x, y + height / 2 - 1, x + width, y + height / 2 + 1, 0xFF8A8A8A);
        int lowX = position(lowerValue);
        int highX = position(upperValue);
        fill(lowX, y + height / 2 - 2, highX, y + height / 2 + 2, 0xFF3A5F8A);
        drawThumb(lowX, draggingThumb == 1 || isThumbHovered(mouseX, mouseY, lowX));
        drawThumb(highX, draggingThumb == 2 || isThumbHovered(mouseX, mouseY, highX));
        drawCenteredString(Minecraft.getInstance().font, getMessage(), x + width / 2, y + 6, 0xFFFFFFFF);
    }

    private void drawThumb(int thumbX, boolean highlighted) {
        int color = highlighted ? 0xFFFFFFFF : 0xFFB0B0B0;
        fill(thumbX - 3, y + 2, thumbX + 4, y + height - 2, color);
        fill(thumbX - 2, y + 3, thumbX + 3, y + height - 3, 0xFF606060);
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
