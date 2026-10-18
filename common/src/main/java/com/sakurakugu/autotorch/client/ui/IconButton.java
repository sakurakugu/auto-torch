package com.sakurakugu.autotorch.client.ui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/** 图标保留操作名称、悬停提示和键盘焦点反馈。 */
public final class IconButton extends Button {
    private static final ResourceLocation BUTTON = ResourceLocation.tryBuild("minecraft", "widget/button");
    private static final ResourceLocation BUTTON_HIGHLIGHTED = ResourceLocation.tryBuild("minecraft", "widget/button_highlighted");
    private static final ResourceLocation BUTTON_DISABLED = ResourceLocation.tryBuild("minecraft", "widget/button_disabled");
    private final PixelGlyph glyph;

    public IconButton(int x, int y, int width, int height, PixelGlyph glyph, Component action, OnPress onPress) {
        super(x, y, width, height, action, onPress, DEFAULT_NARRATION);
        this.glyph = glyph;
        setTooltip(Tooltip.create(action));
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // 背景、悬停与禁用状态沿用原版按钮，只替换中间的图标。
        ResourceLocation sprite = !active ? BUTTON_DISABLED : isHoveredOrFocused() ? BUTTON_HIGHLIGHTED : BUTTON;
        LegacyWidgetSprites.blit(graphics, sprite, getX(), getY(), getWidth(), getHeight());
        glyph.drawCentered(graphics, getX(), getY(), getWidth(), getHeight(), active ? 0xFFFFFFFF : 0xFF777777);
    }
}
