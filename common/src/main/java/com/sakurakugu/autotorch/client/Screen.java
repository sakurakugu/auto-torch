package com.sakurakugu.autotorch.client;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.util.text.ITextComponent;
import org.lwjgl.input.Mouse;

/** 集中适配 1.12.2 GuiScreen 与 1.13 风格界面业务使用的控件生命周期。 */
abstract class Screen extends GuiScreen {
    protected Minecraft minecraft;
    protected FontRenderer font;
    protected final ITextComponent title;
    protected final List<Object> children = new ArrayList<>();
    private int lastMouseX, lastMouseY;
    /** 1.13 的焦点控件；1.12.2 用最近一次点击的控件模拟，供键盘操作滑块。 */
    private Object focusedWidget;

    Screen(ITextComponent title) { this.title = title; }

    protected abstract void init();

    @Override
    public final void initGui() {
        minecraft = mc;
        font = fontRenderer;
        buttonList.clear();
        children.clear();
        focusedWidget = null;
        init();
    }

    /** 新版界面注册控件时同时加入绘制列表与旧版输入列表。 */
    protected <T extends GuiButton> T addButton(T button) {
        children.add(button);
        return super.addButton(button);
    }

    /** 文本框不进入 buttonList，由界面在 render 中手动绘制。 */
    protected EditBox addButton(EditBox box) {
        children.add(box);
        return box;
    }

    protected List<Object> children() { return children; }

    /** 1.12.2 的 drawScreen 会统一绘制背景；保持空实现避免新版界面重复压暗背景。 */
    protected void renderBackground() { }

    protected void renderTooltip(String text, int mouseX, int mouseY) {
        // 旧版 .lang 无法直接保存多行值，构建时会把换行写成字面量 \n。
        drawHoveringText(Arrays.asList(text.replace("\\n", "\n").split("\n")), mouseX, mouseY);
    }

    protected void renderTooltip(List<String> lines, int mouseX, int mouseY) {
        drawHoveringText(lines, mouseX, mouseY);
    }

    protected void setInitialFocus(EditBox box) { box.setFocused(true); }

    /** 新版界面的焦点查询；文本框优先，其次为最近点击的按钮。 */
    public Object getFocused() { return focusedWidget; }

    /** 1.13 的界面 tick 在 1.12.2 对应 updateScreen。 */
    @Override
    public void updateScreen() {
        super.updateScreen();
        tick();
    }

    public void tick() {
        // 1.12.2 不会自动更新子文本框，统一维护输入光标闪烁。
        for (Object child : children) {
            if (child instanceof EditBox && ((EditBox) child).isVisible()) {
                ((EditBox) child).tick();
            }
        }
    }

    public void removed() { }

    @Override
    public void onGuiClosed() { removed(); }

    public void onClose() { minecraft.displayGuiScreen(null); }

    public final void close() { onClose(); }

    public boolean isPauseScreen() { return true; }

    @Override
    public boolean doesGuiPauseGame() { return isPauseScreen(); }

    public boolean mouseScrolled(double mouseX, double mouseY, double amount) { return false; }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel != 0) mouseScrolled(lastMouseX, lastMouseY, wheel > 0 ? 1.0 : -1.0);
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        try {
            super.mouseClicked((int) mouseX, (int) mouseY, button);
        } catch (IOException exception) {
            throw new RuntimeException(exception);
        }
        refreshFocus();
        for (Object child : children) {
            if (child instanceof EditBox) ((EditBox) child).mouseClicked(mouseX, mouseY, button);
        }
        refreshFocus();
        return true;
    }

    @Override
    protected final void mouseClicked(int mouseX, int mouseY, int button) { mouseClicked((double) mouseX, mouseY, button); }

    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (selectedButton instanceof Button) ((Button) selectedButton).mouseReleased(mouseX, mouseY, button);
        selectedButton = null;
        return true;
    }

    @Override
    protected final void mouseReleased(int mouseX, int mouseY, int button) { mouseReleased((double) mouseX, mouseY, button); }

    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (selectedButton instanceof Button) {
            return ((Button) selectedButton).mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
        }
        return false;
    }

    @Override
    protected final void mouseClickMove(int mouseX, int mouseY, int button, long elapsed) {
        mouseDragged(mouseX, mouseY, button, 0.0, 0.0);
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == 1) {
            onClose();
            return;
        }
        if (keyPressed(keyCode, 0, 0)) return;
        // 1.13 会把按键转发给当前焦点控件，这里转发给最近点击的按钮（滑块键盘调整）。
        if (focusedWidget instanceof Button && ((Button) focusedWidget).keyPressed(keyCode, 0, 0)) return;
        for (Object child : children) {
            if (child instanceof EditBox && ((EditBox) child).textboxKeyTyped(typedChar, keyCode)) return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    /** 新版界面覆盖的按键入口；由 1.12.2 的 keyTyped 转发。 */
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) { return false; }

    /** 记录最近一次点击的控件，供键盘操作使用。 */
    private void refreshFocus() {
        for (Object child : children) {
            if (child instanceof EditBox && ((EditBox) child).isFocused()) {
                focusedWidget = child;
                return;
            }
        }
        if (selectedButton instanceof Button) focusedWidget = selectedButton;
    }

    /** 旧版没有 mainWindow，按当前 GUI 缩放因子换算像素坐标。 */
    protected double guiScaleFactor() { return new ScaledResolution(minecraft).getScaleFactor(); }

    /** 旧版帧缓冲高度。 */
    protected int framebufferHeight() { return minecraft.displayHeight; }

    public void render(int mouseX, int mouseY, float partialTicks) {
        lastMouseX = mouseX;
        lastMouseY = mouseY;
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    public final void drawScreen(int mouseX, int mouseY, float partialTicks) { render(mouseX, mouseY, partialTicks); }

    protected static void fill(int left, int top, int right, int bottom, int color) { drawRect(left, top, right, bottom, color); }
}
