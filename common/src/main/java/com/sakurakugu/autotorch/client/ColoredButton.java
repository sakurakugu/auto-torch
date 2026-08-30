package com.sakurakugu.autotorch.client;

import com.mojang.blaze3d.platform.GlStateManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/** 使用明确语义色的普通按钮，保留原版按钮的输入、焦点和旁白行为。 */
final class ColoredButton extends Button {
    private static final ResourceLocation WIDGETS_LOCATION = new ResourceLocation("minecraft", "textures/gui/widgets.png");
    private final int backgroundColor;
    private final int hoveredColor;

    ColoredButton(int x, int y, int width, int height, Component message, OnPress onPress, int backgroundColor, int hoveredColor) {
        super(x, y, width, height, message.getString(), onPress);
        this.backgroundColor = backgroundColor;
        this.hoveredColor = hoveredColor;
    }

    @Override
    public void renderButton(int mouseX, int mouseY, float partialTick) {
        boolean highlighted = isHovered() || isFocused();
        Minecraft.getInstance().getTextureManager().bind(WIDGETS_LOCATION);
        GlStateManager.color4f(1, 1, 1, 1);
        GlStateManager.enableBlend();
        GlStateManager.enableDepthTest();
        GuiComponent.blit(x, y, 0, 0, !active ? 46 : highlighted ? 86 : 66, getWidth(), height, 256, 256);
        int color = highlighted ? hoveredColor : backgroundColor;
        if (!active) {
            color = 0xCC555555;
        }
        fill(x + 2, y + 2, x + getWidth() - 2, y + height - 2, color);
        int outlineColor = highlighted ? 0xFFFFFFFF : 0xFFB0B0B0;
        fill(x, y, x + getWidth(), y + 1, outlineColor);
        fill(x, y + height - 1, x + getWidth(), y + height, outlineColor);
        fill(x, y + 1, x + 1, y + height - 1, outlineColor);
        fill(x + getWidth() - 1, y + 1, x + getWidth(), y + height - 1, outlineColor);
        drawCenteredString(Minecraft.getInstance().font, getMessage(),
                x + getWidth() / 2, y + 6,
                active ? 0xFFFFFFFF : 0xFFA0A0A0);
    }
}
