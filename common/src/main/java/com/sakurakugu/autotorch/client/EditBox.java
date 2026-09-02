package com.sakurakugu.autotorch.client;

import java.util.function.Consumer;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiTextField;

/** 为 1.11.2 文本框提供当前界面使用的命名和布局接口。 */
final class EditBox extends GuiTextField implements Widget {
    public boolean visible = true;
    private Consumer<String> responder = value -> {};
    EditBox(FontRenderer font, int x, int y, int width, int height, String ignored) {
        super(0, font, x, y, width, height);
    }

    void setMaxLength(int length) { setMaxStringLength(length); }
    void setValue(String value) { setText(value); }
    String getValue() { return getText(); }
    void setResponder(Consumer<String> responder) { this.responder = responder; }
    void setX(int x) { this.xPosition = x; }
    void render(int mouseX, int mouseY, float partialTicks) {
        if (visible) drawTextBox();
    }
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!visible) return false;
        super.mouseClicked((int) mouseX, (int) mouseY, button);
        return isMouseOver(mouseX, mouseY);
    }
    @Override public boolean textboxKeyTyped(char typedChar, int keyCode) {
        boolean handled = visible && super.textboxKeyTyped(typedChar, keyCode);
        if (handled) responder.accept(getText());
        return handled;
    }
    @Override public int y() { return yPosition; }
    @Override public void setY(int y) { this.yPosition = y; }
    @Override public boolean isVisible() { return visible; }
    @Override public boolean isMouseOver(double mouseX, double mouseY) {
        return visible && mouseX >= xPosition && mouseY >= yPosition
                && mouseX < xPosition + width && mouseY < yPosition + height;
    }
}
