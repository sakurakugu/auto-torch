package com.sakurakugu.autotorch.client;

import net.minecraft.client.Minecraft;

/** 使用 1.13 按钮输入实现离散数值滑块。 */
abstract class AbstractSliderButton extends Button {
    protected double value;

    AbstractSliderButton(int x, int y, int width, int height, double value) {
        super(x, y, width, height, "", button -> { });
        this.value = clamp(value);
    }

    @Override
    public void onClick(double mouseX, double mouseY) {
        updateFromMouse(mouseX);
    }

    @Override
    protected void onDrag(double mouseX, double mouseY, double deltaX, double deltaY) {
        updateFromMouse(mouseX);
    }

    @Override
    protected void renderButton(int mouseX, int mouseY, float partialTicks) {
        // 只借用禁用按钮的灰色背景，滑动条本身仍保持可交互。
        boolean enabledBeforeRender = enabled;
        enabled = false;
        super.renderButton(mouseX, mouseY, partialTicks);
        enabled = enabledBeforeRender;

        // 补画旧版 GuiButton 缺少的滑块头。
        int trackLeft = x + 4;
        int trackRight = x + width - 4;

        int handleCenter = trackLeft + (int) Math.round(value * (trackRight - trackLeft));
        int handleColor = isHovered() ? 0xFFFFFFFF : 0xFFD0D0D0;
        fill(handleCenter - 3, y + 2, handleCenter + 3, y + height - 2, 0xFF606060);
        fill(handleCenter - 2, y + 3, handleCenter + 2, y + height - 3, handleColor);
        drawCenteredString(Minecraft.getInstance().fontRenderer, getMessage(),
                x + width / 2, y + (height - 8) / 2, 0xFFFFFFFF);
    }

    private void updateFromMouse(double mouseX) {
        value = clamp((mouseX - (x + 4)) / (width - 8.0));
        applyValue();
        updateMessage();
    }

    private static double clamp(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    protected abstract void updateMessage();
    protected abstract void applyValue();
}
