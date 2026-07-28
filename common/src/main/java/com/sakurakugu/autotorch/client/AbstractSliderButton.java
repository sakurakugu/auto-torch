package com.sakurakugu.autotorch.client;

import net.minecraft.client.Minecraft;

/** 使用 1.10.2 按钮输入实现离散数值滑块。 */
abstract class AbstractSliderButton extends Button {
    protected double value;
    private boolean dragging;

    AbstractSliderButton(int x, int y, int width, int height, double value) {
        super(x, y, width, height, "", button -> { });
        this.value = clamp(value);
    }

    @Override
    public void onClick(double mouseX, double mouseY) {
        dragging = true;
        updateFromMouse(mouseX);
    }

    @Override
    protected void mouseDragged(Minecraft minecraft, int mouseX, int mouseY) {
        // 1.10.2 绘制按钮时也会调用此方法，只有按住滑块后才更新数值。
        if (dragging) {
            updateFromMouse(mouseX);
        }
    }

    @Override
    public void mouseReleased(int mouseX, int mouseY) {
        dragging = false;
    }

    @Override
    protected void renderButton(int mouseX, int mouseY, float partialTicks) {
        // 只借用禁用按钮的灰色背景，滑动条本身仍保持可交互。
        boolean enabledBeforeRender = enabled;
        enabled = false;
        super.renderButton(mouseX, mouseY, partialTicks);
        enabled = enabledBeforeRender;

        // 补画旧版 GuiButton 缺少的滑块头。
        int trackLeft = xPosition + 4;
        int trackRight = xPosition + width - 4;

        int handleCenter = trackLeft + (int) Math.round(value * (trackRight - trackLeft));
        int handleColor = isHovered() ? 0xFFFFFFFF : 0xFFD0D0D0;
        fill(handleCenter - 3, yPosition + 2, handleCenter + 3, yPosition + height - 2, 0xFF606060);
        fill(handleCenter - 2, yPosition + 3, handleCenter + 2, yPosition + height - 3, handleColor);
        drawCenteredString(Minecraft.getMinecraft().fontRendererObj, getMessage(),
                xPosition + width / 2, yPosition + (height - 8) / 2, 0xFFFFFFFF);
    }

    private void updateFromMouse(double mouseX) {
        value = clamp((mouseX - (xPosition + 4)) / (width - 8.0));
        applyValue();
        updateMessage();
    }

    private static double clamp(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    protected abstract void updateMessage();
    protected abstract void applyValue();
}
