package com.sakurakugu.autotorch.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;

/**
 * 为旧版 GuiButton 补齐新版界面使用的控件接口。
 * 1.10.2 的鼠标事件是整数与 Minecraft 参数，这里统一桥接到 1.13 风格的双精度事件。
 */
public class Button extends GuiButton implements Widget {
    public interface OnPress { void onPress(Button button); }

    private final OnPress onPress;
    public boolean active = true;

    public Button(int x, int y, int width, int height, String message, OnPress onPress) {
        super(0, x, y, width, height, message);
        this.onPress = onPress;
    }

    public void onClick(double mouseX, double mouseY) {
        if (active) onPress.onPress(this);
    }

    /** 新版按钮的松开入口；旧版 GuiButton 没有该回调。 */
    public void onRelease(double mouseX, double mouseY) { }

    /** 新版按钮的拖动入口；旧版 GuiButton 的拖动由 mouseDragged 桥接。 */
    protected void onDrag(double mouseX, double mouseY, double deltaX, double deltaY) { }

    @Override
    public void drawButton(Minecraft minecraft, int mouseX, int mouseY) {
        render(mouseX, mouseY, 0.0F);
    }

    public void render(int mouseX, int mouseY, float partialTicks) {
        enabled = active;
        if (!visible) return;
        hovered = isMouseOver(mouseX, mouseY);
        renderButton(mouseX, mouseY, partialTicks);
    }

    protected void renderButton(int mouseX, int mouseY, float partialTicks) {
        super.drawButton(Minecraft.getMinecraft(), mouseX, mouseY);
    }

    /** 新版鼠标按下入口。 */
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!active || !visible || !isValidClickButton(button) || !isMouseOver(mouseX, mouseY)) return false;
        onClick(mouseX, mouseY);
        return true;
    }

    @Override
    public boolean mousePressed(Minecraft minecraft, int mouseX, int mouseY) {
        return isMouseOver(mouseX, mouseY) && mouseClicked(mouseX, mouseY, 0);
    }

    /** 新版鼠标松开入口。 */
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        onRelease(mouseX, mouseY);
        return true;
    }

    @Override
    public void mouseReleased(int mouseX, int mouseY) { mouseReleased((double) mouseX, mouseY, 0); }

    /** 新版鼠标拖动入口。 */
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        onDrag(mouseX, mouseY, deltaX, deltaY);
        return true;
    }

    @Override
    protected void mouseDragged(Minecraft minecraft, int mouseX, int mouseY) {
        mouseDragged((double) mouseX, mouseY, 0, 0.0, 0.0);
    }

    /** 兼容旧版界面直接调用的拖动入口。 */
    void drag(int mouseX, int mouseY) { mouseDragged((double) mouseX, mouseY, 0, 0.0, 0.0); }

    /** 新版键盘入口；由界面在 keyTyped 中转发。 */
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) { return false; }

    public void setMessage(String message) { displayString = message; }
    public String getMessage() { return displayString; }
    public boolean isHovered() { return hovered; }
    public boolean isFocused() { return false; }
    public static void fill(int left, int top, int right, int bottom, int color) {
        drawRect(left, top, right, bottom, color);
    }
    protected boolean isValidClickButton(int button) { return button == 0; }
    protected void fillGradient(int left, int top, int right, int bottom, int topColor, int bottomColor) {
        drawGradientRect(left, top, right, bottom, topColor, bottomColor);
    }
    public int getWidth() { return width; }
    @Override public int y() { return yPosition; }
    @Override public void setY(int y) { this.yPosition = y; }
    @Override public boolean isVisible() { return visible; }
    @Override public boolean isMouseOver(double mouseX, double mouseY) {
        return visible && mouseX >= xPosition && mouseY >= yPosition
                && mouseX < xPosition + width && mouseY < yPosition + height;
    }
}
