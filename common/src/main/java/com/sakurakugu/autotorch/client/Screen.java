package com.sakurakugu.autotorch.client;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.text.ITextComponent;
import org.lwjgl.input.Mouse;

/** 集中适配 1.11.2 GuiScreen 与界面业务使用的控件生命周期。 */
abstract class Screen extends GuiScreen {
    protected Minecraft minecraft;
    protected FontRenderer font;
    protected final ITextComponent title;
    protected final List<Object> children = new ArrayList<>();
    private Button pressedButton;
    Screen(ITextComponent title) { this.title = title; }
    protected abstract void init();
    @Override public final void initGui() { minecraft = mc; font = fontRendererObj; buttonList.clear(); children.clear(); init(); }
    public void onClose() { minecraft.displayGuiScreen(null); }
    public final void close() { onClose(); }
    public boolean isPauseScreen() { return true; }
    @Override public boolean doesGuiPauseGame() { return isPauseScreen(); }
    protected List<?> children() { return children; }
    protected void renderTooltip(String text, int mouseX, int mouseY) {
        drawHoveringText(Collections.singletonList(text), mouseX, mouseY);
    }
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) { return false; }
    @Override public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel != 0) mouseScrolled(0.0, 0.0, wheel > 0 ? 1.0 : -1.0);
    }
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        pressedButton = null;
        if (button == 0) {
            for (net.minecraft.client.gui.GuiButton candidate : buttonList) {
                if (candidate instanceof Button && ((Button) candidate).isMouseOver(mouseX, mouseY)) {
                    pressedButton = (Button) candidate;
                    break;
                }
            }
        }
        try { super.mouseClicked((int) mouseX, (int) mouseY, button); } catch (IOException e) { throw new RuntimeException(e); }
        for (Object child : children) if (child instanceof EditBox) ((EditBox) child).mouseClicked(mouseX, mouseY, button);
        return true;
    }
    @Override protected final void mouseClicked(int mouseX, int mouseY, int button) { mouseClicked((double) mouseX, mouseY, button); }
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        super.mouseReleased((int) mouseX, (int) mouseY, button);
        pressedButton = null;
        return true;
    }
    @Override protected final void mouseReleased(int mouseX, int mouseY, int button) { mouseReleased((double) mouseX, mouseY, button); }
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
        if (pressedButton != null) pressedButton.drag((int) mouseX, (int) mouseY);
        return pressedButton != null;
    }
    @Override protected final void mouseClickMove(int mouseX, int mouseY, int button, long elapsed) { mouseDragged(mouseX, mouseY, button, 0.0, 0.0); }
    @Override protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == 1) { onClose(); return; }
        for (Object child : children) if (child instanceof EditBox && ((EditBox) child).textboxKeyTyped(typedChar, keyCode)) return;
        super.keyTyped(typedChar, keyCode);
    }
    public void render(int mouseX, int mouseY, float partialTicks) { super.drawScreen(mouseX, mouseY, partialTicks); }
    @Override public final void drawScreen(int mouseX, int mouseY, float partialTicks) { render(mouseX, mouseY, partialTicks); }
    protected static void fill(int left, int top, int right, int bottom, int color) { drawRect(left, top, right, bottom, color); }
}
