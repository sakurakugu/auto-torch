package com.sakurakugu.autotorch.client.ui;

import java.util.function.BooleanSupplier;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/** 只绘制中间的开关轨道与滑块；配置名称与状态保留在读屏信息中。 */
public final class ToggleSwitchButton extends Button {
    private static final ResourceLocation BUTTON = ResourceLocation.withDefaultNamespace("widget/button");
    private static final ResourceLocation BUTTON_HIGHLIGHTED = ResourceLocation.withDefaultNamespace("widget/button_highlighted");
    private static final ResourceLocation BUTTON_DISABLED = ResourceLocation.withDefaultNamespace("widget/button_disabled");
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
        // 1.21.1 没有单独的“内容”渲染钩子，需要自己补上按钮底图。
        ResourceLocation sprite = !active ? BUTTON_DISABLED : isHoveredOrFocused() ? BUTTON_HIGHLIGHTED : BUTTON;
        graphics.blitSprite(sprite, getX(), getY(), getWidth(), getHeight());
        refreshMessage();
        PixelControls.toggle(graphics, getX() + (getWidth() - 24) / 2, getY() + (getHeight() - 12) / 2,
                enabled.getAsBoolean(), isHovered(), isFocused(), active);
    }
}
