package com.sakurakugu.autotorch.client;

import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.text.ITextComponent;

/** 集中适配 1.13 GuiScreen 与后续版本界面生命周期的差异。 */
public abstract class Screen extends GuiScreen {
    protected Minecraft minecraft;
    protected FontRenderer font;
    protected final ITextComponent title;
    private int lastMouseX, lastMouseY;

    @Override public void render(int mouseX, int mouseY, float partialTick) {
        lastMouseX = mouseX; lastMouseY = mouseY;
        super.render(mouseX, mouseY, partialTick);
    }

    Screen(ITextComponent title) { this.title = title; }

    protected abstract void init();
    protected void renderBackground() { drawDefaultBackground(); }
    protected void renderTooltip(java.util.List<String> lines, int x, int y) { drawHoveringText(lines, x, y); }
    protected EditBox addButton(EditBox box) { children.add(box); return box; }
    protected void setInitialFocus(EditBox box) { setFocused(box); box.setFocused(true); }

    @Override
    public void tick() {
        super.tick();
        // 1.13 不会自动更新子文本框，统一维护输入光标闪烁。
        for (net.minecraft.client.gui.IGuiEventListener child : children()) {
            if (child instanceof EditBox && ((EditBox) child).isVisible()) {
                ((EditBox) child).tick();
            }
        }
    }


    @Override
    protected final void initGui() {
        minecraft = mc;
        font = fontRenderer;
        init();
    }

    public void removed() { }
    @Override public void onGuiClosed() { removed(); }
    public void onClose() { minecraft.displayGuiScreen(null); }
    @Override public final void close() { onClose(); }
    public boolean isPauseScreen() { return true; }
    @Override public boolean doesGuiPauseGame() { return isPauseScreen(); }
    protected List<? extends net.minecraft.client.gui.IGuiEventListener> children() { return getChildren(); }
    protected void renderTooltip(String text, int mouseX, int mouseY) { drawHoveringText(text, mouseX, mouseY); }
    @Override
    public boolean mouseScrolled(double amount) {
        // 将 1.13 的单参数滚轮事件转交给界面使用的新版形式。
        return mouseScrolled(lastMouseX, lastMouseY, amount);
    }
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        return super.mouseScrolled(amount);
    }
    protected static void fill(int left, int top, int right, int bottom, int color) {
        drawRect(left, top, right, bottom, color);
    }
}
