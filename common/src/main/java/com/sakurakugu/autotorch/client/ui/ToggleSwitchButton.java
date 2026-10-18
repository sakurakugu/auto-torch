package com.sakurakugu.autotorch.client.ui;

import java.util.function.BooleanSupplier;
import net.minecraft.client.gui.GuiGraphics;
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
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // 1.20.1 的 AbstractButton 会在 renderWidget 里绘制按钮底图；这里只画滑动条风格的开关本体，
        // 不再额外自绘底图，避免开关外面多出一圈按钮框。
        refreshMessage();
        PixelControls.toggle(graphics, getX() + (getWidth() - 24) / 2, getY() + (getHeight() - 12) / 2,
                enabled.getAsBoolean(), isHovered(), isFocused(), active);
    }
}
