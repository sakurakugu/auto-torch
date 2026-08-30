package com.sakurakugu.autotorch.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.ResourceLocation;

/** 使用 1.9.4 按钮输入实现离散数值滑块。 */
abstract class AbstractSliderButton extends Button {
    private static final ResourceLocation WIDGETS_LOCATION = new ResourceLocation("minecraft", "textures/gui/widgets.png");
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
        // 1.9.4 绘制按钮时也会调用此方法，只有按住滑块后才更新数值。
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
        // 借用禁用按钮底图，再叠加带悬停状态的纹理滑块头。
        boolean enabledBeforeRender = enabled;
        String messageBeforeRender = displayString;
        enabled = false;
        displayString = "";
        super.renderButton(mouseX, mouseY, partialTicks);
        displayString = messageBeforeRender;
        enabled = enabledBeforeRender;

        Minecraft.getMinecraft().getTextureManager().bindTexture(WIDGETS_LOCATION);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.enableBlend();
        int handleLeft = xPosition + (int) (value * (width - 8));
        int textureY = isHovered() ? 86 : 66;
        drawModalRectWithCustomSizedTexture(handleLeft, yPosition, 0, textureY, 4, height, 256, 256);
        drawModalRectWithCustomSizedTexture(handleLeft + 4, yPosition, 196, textureY, 4, height, 256, 256);
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
