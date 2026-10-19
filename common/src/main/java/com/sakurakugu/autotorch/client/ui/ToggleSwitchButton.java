package com.sakurakugu.autotorch.client.ui;

import com.sakurakugu.autotorch.client.LegacyText;
import java.util.function.BooleanSupplier;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/** 只绘制中间的开关轨道与滑块；配置名称与状态保留在读屏信息中。 */
public final class ToggleSwitchButton extends Button {
    private final Component label;
    private final BooleanSupplier enabled;

    public ToggleSwitchButton(int x, int y, int width, int height, Component label,
                               BooleanSupplier enabled, OnPress onPress) {
        super(x, y, width, height, LegacyText.string(label), button -> {
            onPress.onPress(button);
            ((ToggleSwitchButton) button).refreshMessage();
        });
        this.label = label;
        this.enabled = enabled;
        refreshMessage();
    }

    private void refreshMessage() {
        setMessage(LegacyText.string(label.copy().append(": ").append(new net.minecraft.network.chat.TranslatableComponent(enabled.getAsBoolean()
                ? "command.autotorch.on" : "command.autotorch.off"))));
    }

    @Override
    public void renderButton(int mouseX, int mouseY, float partialTick) {
        // 原版 AbstractButton 会在 renderButton 里绘制按钮底图；这里只画滑动条风格的开关本体，
        // 不再额外自绘底图，避免开关外面多出一圈按钮框。
        refreshMessage();
        PixelControls.toggle(x + (getWidth() - 24) / 2, y + (height - 12) / 2,
                enabled.getAsBoolean(), (isHovered() || isFocused()), isFocused(), active);
    }
}
