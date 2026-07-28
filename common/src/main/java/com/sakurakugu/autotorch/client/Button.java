package com.sakurakugu.autotorch.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;

/** 为 1.13 的 GuiButton 补齐界面业务使用的控件接口。 */
class Button extends GuiButton implements Widget {
    interface OnPress { void onPress(Button button); }

    private final OnPress onPress;
    boolean active = true;

    Button(int x, int y, int width, int height, String message, OnPress onPress) {
        super(0, x, y, width, height, message);
        this.onPress = onPress;
    }

    public void onClick(double mouseX, double mouseY) {
        if (active) onPress.onPress(this);
    }

    public void render(int mouseX, int mouseY, float partialTicks) {
        enabled = active;
        renderButton(mouseX, mouseY, partialTicks);
    }

    protected void renderButton(int mouseX, int mouseY, float partialTicks) {
        super.drawButton(Minecraft.getMinecraft(), mouseX, mouseY, partialTicks);
    }

    @Override public void drawButton(Minecraft minecraft, int mouseX, int mouseY, float partialTicks) { render(mouseX, mouseY, partialTicks); }
    @Override public boolean mousePressed(Minecraft minecraft, int mouseX, int mouseY) {
        boolean pressed = super.mousePressed(minecraft, mouseX, mouseY);
        if (pressed) onClick(mouseX, mouseY);
        return pressed;
    }
    void drag(int mouseX, int mouseY) { mouseDragged(Minecraft.getMinecraft(), mouseX, mouseY); }

    void setMessage(String message) { displayString = message; }
    String getMessage() { return displayString; }
    boolean isHovered() { return hovered; }
    boolean isFocused() { return false; }
    static void fill(int left, int top, int right, int bottom, int color) {
        drawRect(left, top, right, bottom, color);
    }
    @Override public int y() { return y; }
    @Override public void setY(int y) { this.y = y; }
    @Override public boolean isVisible() { return visible; }
    @Override public boolean isMouseOver(double mouseX, double mouseY) {
        return visible && mouseX >= x && mouseY >= y && mouseX < x + width && mouseY < y + height;
    }
}
