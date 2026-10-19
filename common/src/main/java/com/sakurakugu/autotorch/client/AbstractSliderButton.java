package com.sakurakugu.autotorch.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.input.Keyboard;

/** 使用新版按钮输入实现离散数值滑块。 */
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
        int handleLeft = x + (int) (value * (width - 8));
        int textureY = isHovered() ? 86 : 66;
        drawModalRectWithCustomSizedTexture(handleLeft, y, 0, textureY, 4, height, 256, 256);
        drawModalRectWithCustomSizedTexture(handleLeft + 4, y, 196, textureY, 4, height, 256, 256);
        drawCenteredString(Minecraft.getMinecraft().fontRenderer, getMessage(),
                x + width / 2, y + (height - 8) / 2, 0xFFFFFFFF);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // 1.12.2 是 LWJGL 2 键码：203/205 对应左右方向键。
        if (keyCode == Keyboard.KEY_LEFT || keyCode == Keyboard.KEY_RIGHT) {
            value = clamp(value + (keyCode == Keyboard.KEY_LEFT ? -1.0 : 1.0) / Math.max(1, width - 8));
            applyValue();
            updateMessage();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
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
