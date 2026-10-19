package com.sakurakugu.autotorch.client.ui;

import com.sakurakugu.autotorch.client.LegacyTooltips;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/** 图标保留操作名称、悬停提示和键盘焦点反馈。 */
public final class IconButton extends Button {
    private static final ResourceLocation BUTTON = new ResourceLocation("minecraft", "widget/button");
    private static final ResourceLocation BUTTON_HIGHLIGHTED = new ResourceLocation("minecraft", "widget/button_highlighted");
    private static final ResourceLocation BUTTON_DISABLED = new ResourceLocation("minecraft", "widget/button_disabled");
    private final PixelGlyph glyph;

    public IconButton(int x, int y, int width, int height, PixelGlyph glyph, Component action, OnPress onPress) {
        super(x, y, width, height, action, onPress);
        this.glyph = glyph;
        LegacyTooltips.set(this, action);
    }

    @Override
    public void renderButton(PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
        // 背景、悬停与禁用状态沿用原版按钮，只替换中间的图标。
        ResourceLocation sprite = !active ? BUTTON_DISABLED : isHoveredOrFocused() ? BUTTON_HIGHLIGHTED : BUTTON;
        LegacyWidgetSprites.blit(poseStack, sprite, x, y, getWidth(), getHeight());
        glyph.drawCentered(poseStack, x, y, getWidth(), getHeight(), active ? 0xFFFFFFFF : 0xFF777777);
    }
}
