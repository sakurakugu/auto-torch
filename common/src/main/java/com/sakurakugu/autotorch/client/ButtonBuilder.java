package com.sakurakugu.autotorch.client;

import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/** 旧版没有 Button.Builder，用简单构造器补足 bounds/tooltip 链式写法。 */
final class ButtonBuilder {
    private final Component message;
    private final Button.OnPress onPress;
    private int x;
    private int y;
    private int width;
    private int height;
    private Component tooltip;

    private ButtonBuilder(Component message, Button.OnPress onPress) {
        this.message = message;
        this.onPress = onPress;
    }

    static ButtonBuilder builder(Component message, Button.OnPress onPress) {
        return new ButtonBuilder(message, onPress);
    }

    ButtonBuilder bounds(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        return this;
    }

    ButtonBuilder tooltip(Component tooltip) {
        this.tooltip = tooltip;
        return this;
    }

    Button build() {
        Button button = new ScrollingButton(x, y, width, height, message, onPress);
        if (tooltip != null) {
            LegacyTooltips.set(button, tooltip);
        }
        return button;
    }
}
