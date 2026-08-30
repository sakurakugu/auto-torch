package com.sakurakugu.autotorch.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.ResourceLocation;

/** 使用明确语义色的普通按钮，保留原版按钮的输入、焦点和旁白行为。 */
final class ColoredButton extends Button {
    private static final ResourceLocation WIDGETS_LOCATION = new ResourceLocation("minecraft", "textures/gui/widgets.png");
    private final int backgroundColor;
    private final int hoveredColor;

    ColoredButton(int x, int y, int width, int height, ITextComponent message, OnPress onPress, int backgroundColor, int hoveredColor) {
        super(x, y, width, height, message.getFormattedText(), onPress);
        this.backgroundColor = backgroundColor;
        this.hoveredColor = hoveredColor;
    }

    @Override
    public void renderButton(int mouseX, int mouseY, float partialTick) {
        boolean highlighted = isHovered() || isFocused();
        Minecraft.getMinecraft().getTextureManager().bindTexture(WIDGETS_LOCATION);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.enableBlend();
        drawModalRectWithCustomSizedTexture(x, y, 0, !active ? 46 : highlighted ? 86 : 66, getButtonWidth(), height, 256, 256);
        int color = highlighted ? hoveredColor : backgroundColor;
        if (!active) {
            color = 0xCC555555;
        }
        fill(x + 1, y + 1,
                x + getButtonWidth() - 1, y + height - 1, color);
        int outlineColor = highlighted ? 0xFFFFFFFF : 0xFFB0B0B0;
        fill(x, y, x + getButtonWidth(), y + 1, outlineColor);
        fill(x, y + height - 1, x + getButtonWidth(), y + height, outlineColor);
        fill(x, y + 1, x + 1, y + height - 1, outlineColor);
        fill(x + getButtonWidth() - 1, y + 1, x + getButtonWidth(), y + height - 1, outlineColor);
        drawCenteredString(Minecraft.getMinecraft().fontRenderer, getMessage(),
                x + getButtonWidth() / 2, y + 6,
                active ? 0xFFFFFFFF : 0xFFA0A0A0);
    }
}
