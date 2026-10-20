package com.sakurakugu.autotorch.client;

import com.sakurakugu.autotorch.client.Button;
import net.minecraft.util.IChatComponent;

/** 旧版没有 Button.Builder，用简单构造器补足 bounds/tooltip 链式写法。 */
final class ButtonBuilder {
    private final IChatComponent message;
    private final Button.OnPress onPress;
    private int x;
    private int y;
    private int width;
    private int height;
    private IChatComponent tooltip;

    private ButtonBuilder(IChatComponent message, Button.OnPress onPress) {
        this.message = message;
        this.onPress = onPress;
    }

    static ButtonBuilder builder(IChatComponent message, Button.OnPress onPress) {
        return new ButtonBuilder(message, onPress);
    }

    ButtonBuilder bounds(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        return this;
    }

    ButtonBuilder tooltip(IChatComponent tooltip) {
        this.tooltip = tooltip;
        return this;
    }

    Button build() {
        Button button = new ScrollingButton(x, y, width, height, LegacyText.string(message), onPress);
        if (tooltip != null) {
            LegacyTooltips.set(button, tooltip);
        }
        return button;
    }
}
