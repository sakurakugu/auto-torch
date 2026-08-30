package com.sakurakugu.autotorch.client;

import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.Minecraft;
import net.minecraft.util.ResourceLocation;

/** 使用 1.13 按钮输入实现离散数值滑块。 */
abstract class AbstractSliderButton extends Button {
    private static final ResourceLocation WIDGETS_LOCATION = new ResourceLocation("minecraft", "textures/gui/widgets.png");
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
        // 原版 1.14 滑块使用禁用按钮底图，再叠加带悬停状态的纹理滑块头。
        boolean enabledBeforeRender = enabled;
        String messageBeforeRender = displayString;
        enabled = false;
        displayString = "";
        super.renderButton(mouseX, mouseY, partialTicks);
        displayString = messageBeforeRender;
        enabled = enabledBeforeRender;

        Minecraft.getInstance().getTextureManager().bindTexture(WIDGETS_LOCATION);
        GlStateManager.color4f(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.enableBlend();
        int handleLeft = x + (int) (value * (width - 8));
        int textureY = isHovered() ? 86 : 66;
        drawModalRectWithCustomSizedTexture(handleLeft, y, 0, textureY, 4, height, 256, 256);
        drawModalRectWithCustomSizedTexture(handleLeft + 4, y, 196, textureY, 4, height, 256, 256);
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
