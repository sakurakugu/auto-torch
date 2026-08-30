package com.sakurakugu.autotorch.client;

import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import net.minecraft.client.Minecraft;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

/** 适配旧版 GuiButton 事件模型的双端点范围滑动条。 */
final class DualRangeSlider extends Button {
    private static final ResourceLocation SLIDER_LOCATION = new ResourceLocation("autotorch", "textures/gui/slider.png");
    private final int minValue, maxValue, maxSpan;
    private final BiFunction<Integer, Integer, IChatComponent> messageFactory;
    private final BiConsumer<Integer, Integer> changeListener;
    private int lowerValue, upperValue, draggingThumb;
    DualRangeSlider(int x, int y, int width, int height, int minValue, int maxValue, int maxSpan,
            int lowerValue, int upperValue, BiFunction<Integer, Integer, IChatComponent> messageFactory,
            BiConsumer<Integer, Integer> changeListener) {
        super(x, y, width, height, "", button -> { });
        this.minValue = minValue; this.maxValue = maxValue; this.maxSpan = maxSpan;
        this.messageFactory = messageFactory; this.changeListener = changeListener;
        setValues(lowerValue, upperValue, false);
    }
    @Override public void onClick(double mouseX, double mouseY) {
        draggingThumb = Math.abs(mouseX - position(lowerValue)) <= Math.abs(mouseX - position(upperValue)) ? 1 : 2;
        update(mouseX);
    }
    boolean drag(double mouseX) { if (draggingThumb == 0) return false; update(mouseX); return true; }
    boolean stopDrag() { if (draggingThumb == 0) return false; draggingThumb = 0; return true; }
    @Override protected void renderButton(int mouseX, int mouseY, float partialTicks) {
        Minecraft.getMinecraft().renderEngine.bindTexture(SLIDER_LOCATION);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        GL11.glEnable(GL11.GL_BLEND);
        drawTexturedModalRect(xPosition, yPosition, 0, isFocused() ? 20 : 0, width, height);
        fill(xPosition, yPosition + height / 2 - 1, xPosition + width, yPosition + height / 2 + 1, 0xFF8A8A8A);
        int lowX = position(lowerValue), highX = position(upperValue);
        fill(lowX, yPosition + height / 2 - 2, highX, yPosition + height / 2 + 2, 0xFF3A5F8A);
        thumb(lowX, draggingThumb == 1 || Math.abs(mouseX - lowX) <= 6);
        thumb(highX, draggingThumb == 2 || Math.abs(mouseX - highX) <= 6);
        drawCenteredString(Minecraft.getMinecraft().fontRenderer, getMessage(), xPosition + width / 2, yPosition + 6, 0xFFFFFFFF);
    }
    private void thumb(int x, boolean hovered) {
        fill(x - 3, yPosition + 2, x + 4, yPosition + height - 2, hovered ? 0xFFFFFFFF : 0xFFB0B0B0);
        fill(x - 2, yPosition + 3, x + 3, yPosition + height - 3, 0xFF606060);
    }
    private void update(double mouseX) {
        int value = (int) Math.round((Math.max(xPosition + 4, Math.min(xPosition + width - 4, mouseX)) - (xPosition + 4))
                * (maxValue - minValue) / (double) (width - 8)) + minValue;
        int lower = draggingThumb == 1 ? Math.min(value, upperValue) : lowerValue;
        int upper = draggingThumb == 2 ? Math.max(value, lowerValue) : upperValue;
        if (upper - lower > maxSpan) { if (draggingThumb == 1) upper = lower + maxSpan; else lower = upper - maxSpan; }
        setValues(lower, upper, true);
    }
    private void setValues(int lower, int upper, boolean notify) {
        lowerValue = Math.max(minValue, Math.min(maxValue, lower));
        upperValue = Math.max(lowerValue, Math.min(maxValue, upper));
        setMessage(messageFactory.apply(lowerValue, upperValue).getFormattedText());
        if (notify) changeListener.accept(lowerValue, upperValue);
    }
    private int position(int value) { return xPosition + 4 + (value - minValue) * (width - 8) / (maxValue - minValue); }
}
