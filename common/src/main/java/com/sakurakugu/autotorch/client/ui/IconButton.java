package com.sakurakugu.autotorch.client.ui;

import com.sakurakugu.autotorch.client.LegacyText;
import com.sakurakugu.autotorch.client.LegacyTooltips;
import com.sakurakugu.autotorch.client.Button;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.ResourceLocation;

/** 图标保留操作名称、悬停提示和键盘焦点反馈。 */
public final class IconButton extends Button {
    private static final ResourceLocation BUTTON = new ResourceLocation("minecraft", "widget/button");
    private static final ResourceLocation BUTTON_HIGHLIGHTED = new ResourceLocation("minecraft", "widget/button_highlighted");
    private static final ResourceLocation BUTTON_DISABLED = new ResourceLocation("minecraft", "widget/button_disabled");
    private final PixelGlyph glyph;

    public IconButton(int x, int y, int width, int height, PixelGlyph glyph, ITextComponent action, OnPress onPress) {
        super(x, y, width, height, LegacyText.string(action), onPress);
        this.glyph = glyph;
        LegacyTooltips.set(this, action);
    }

    @Override
    public void renderButton(int mouseX, int mouseY, float partialTick) {
        // 背景、悬停与禁用状态沿用原版按钮，只替换中间的图标。
        ResourceLocation sprite = !active ? BUTTON_DISABLED : (isHovered() || isFocused()) ? BUTTON_HIGHLIGHTED : BUTTON;
        LegacyWidgetSprites.blit(sprite, x, y, getWidth(), height);
        glyph.drawCentered(x, y, getWidth(), height, active ? 0xFFFFFFFF : 0xFF777777);
    }
}
