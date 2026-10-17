package com.sakurakugu.autotorch.client.ui;

import java.util.function.BooleanSupplier;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/** 只绘制中间的开关轨道与滑块；配置名称与状态保留在读屏信息中。 */
public final class ToggleSwitchButton extends Button {
    private final Component label;
    private final BooleanSupplier enabled;

    public ToggleSwitchButton(int x, int y, int width, int height, Component label,
                               BooleanSupplier enabled, OnPress onPress) {
        super(x, y, width, height, label, button -> {
            onPress.onPress(button);
            ((ToggleSwitchButton) button).refreshMessage();
        }, DEFAULT_NARRATION);
        this.label = label;
        this.enabled = enabled;
        refreshMessage();
    }

    private void refreshMessage() {
        setMessage(label.copy().append(": ").append(Component.translatable(enabled.getAsBoolean()
                ? "command.autotorch.on" : "command.autotorch.off")));
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        refreshMessage();
        PixelControls.toggle(graphics, getX() + (getWidth() - 24) / 2, getY() + (getHeight() - 12) / 2,
                enabled.getAsBoolean(), isHovered(), isFocused(), active);
    }
}
