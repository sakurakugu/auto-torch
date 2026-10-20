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
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.util.IChatComponent;
import org.lwjgl.input.Mouse;

/** 集中适配 1.8.9 GuiScreen 与界面业务使用的控件生命周期。 */
abstract class Screen extends GuiScreen {
    protected Minecraft minecraft;
    protected FontRenderer font;
    protected final IChatComponent title;
    protected final List<Object> children = new ArrayList<>();
    private int lastMouseX, lastMouseY;
    /** 1.13 的焦点控件；1.8.9 用最近一次点击的控件模拟，供键盘操作滑块。 */
    private Object focusedWidget;
    /** 1.8.9 的 GuiScreen 没有可用的 selectedButton，这里自行记录按住的按钮。 */
    private Button pressedButton;

    Screen(IChatComponent title) { this.title = title; }

    protected abstract void init();

    @Override
    public final void initGui() {
        minecraft = mc;
        font = fontRendererObj;
        buttonList.clear();
        children.clear();
        focusedWidget = null;
        pressedButton = null;
        init();
    }

    /** 新版界面注册控件时同时加入绘制列表与旧版输入列表。 */
    protected <T extends GuiButton> T addButton(T button) {
        children.add(button);
        buttonList.add(button);
        return button;
    }

    /** 文本框不进入 buttonList，由界面在 render 中手动绘制。 */
    protected EditBox addButton(EditBox box) {
        children.add(box);
        return box;
    }

    protected List<Object> children() { return children; }

    /** 1.13 的界面在 render 开头调用本方法；1.8.9 必须自己绘制默认背景。 */
    protected void renderBackground() { drawDefaultBackground(); }

    protected void renderTooltip(String text, int mouseX, int mouseY) {
        // 旧版 .lang 无法直接保存多行值，构建时会把换行写成字面量 \n。
        drawHoveringText(Arrays.asList(text.replace("\\n", "\n").split("\n")), mouseX, mouseY);
        restoreGuiRenderState();
    }

    protected void renderTooltip(List<String> lines, int mouseX, int mouseY) {
        drawHoveringText(lines, mouseX, mouseY);
        restoreGuiRenderState();
    }

    /**
     * 1.8.9 的 Forge 提示框绘制结束时会重新开启标准物品光照与深度测试，
     * 而新版界面会在提示框之后继续画文字和图标，必须恢复 GUI 的 2D 状态，
     * 否则后续文字会被光照压暗（鼠标悬停时更明显）。
     */
    private static void restoreGuiRenderState() {
        RenderHelper.disableStandardItemLighting();
        GlStateManager.disableLighting();
        GlStateManager.disableDepth();
        GlStateManager.enableTexture2D();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }

    protected void setInitialFocus(EditBox box) { box.setFocused(true); }

    /** 新版界面的焦点查询；文本框优先，其次为最近点击的按钮。 */
    public Object getFocused() { return focusedWidget; }

    /** 1.13 的界面 tick 在 1.8.9 对应 updateScreen。 */
    @Override
    public void updateScreen() {
        super.updateScreen();
        tick();
    }

    public void tick() {
        // 1.8.9 不会自动更新子文本框，统一维护输入光标闪烁。
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
        pressedButton = null;
        if (button == 0) {
            for (GuiButton candidate : buttonList) {
                if (candidate instanceof Button && ((Button) candidate).isMouseOver(mouseX, mouseY)) {
                    pressedButton = (Button) candidate;
                    break;
                }
            }
        }
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
        // 先通知按住的按钮，滑块等控件需要在这里结束拖动状态。
        if (pressedButton != null) pressedButton.mouseReleased(mouseX, mouseY, button);
        super.mouseReleased((int) mouseX, (int) mouseY, button);
        pressedButton = null;
        return true;
    }

    @Override
    protected final void mouseReleased(int mouseX, int mouseY, int button) { mouseReleased((double) mouseX, mouseY, button); }

    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (pressedButton != null) {
            return pressedButton.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
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

    /** 新版界面覆盖的按键入口；由 1.8.9 的 keyTyped 转发。 */
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) { return false; }

    /** 记录最近一次点击的控件，供键盘操作使用。 */
    private void refreshFocus() {
        for (Object child : children) {
            if (child instanceof EditBox && ((EditBox) child).isFocused()) {
                focusedWidget = child;
                return;
            }
        }
        if (pressedButton != null) focusedWidget = pressedButton;
    }

    /** 旧版没有 mainWindow，按当前 GUI 缩放因子换算像素坐标。 */
    protected double guiScaleFactor() { return new ScaledResolution(minecraft).getScaleFactor(); }

    /** 旧版帧缓冲高度。 */
    protected int framebufferHeight() { return minecraft.displayHeight; }

    public void render(int mouseX, int mouseY, float partialTicks) {
        lastMouseX = mouseX;
        lastMouseY = mouseY;
        // 1.8.9 的按钮绘制、世界渲染与提示框都可能留下光照/深度状态，
        // 进入 2D 界面绘制前统一关闭，避免文字或控件被压暗、被深度测试遮挡。
        RenderHelper.disableStandardItemLighting();
        GlStateManager.disableLighting();
        GlStateManager.disableDepth();
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    public final void drawScreen(int mouseX, int mouseY, float partialTicks) { render(mouseX, mouseY, partialTicks); }

    protected static void fill(int left, int top, int right, int bottom, int color) { drawRect(left, top, right, bottom, color); }
}
