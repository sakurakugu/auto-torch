package com.sakurakugu.autotorch.client;

import net.minecraft.client.Minecraft;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

/** 使用明确语义色的普通按钮，保留原版按钮的输入、焦点和旁白行为。 */
final class ColoredButton extends Button {
    private static final ResourceLocation WIDGETS_LOCATION = new ResourceLocation("minecraft", "textures/gui/widgets.png");
    private final int backgroundColor;
    private final int hoveredColor;

    ColoredButton(int x, int y, int width, int height, IChatComponent message, OnPress onPress, int backgroundColor, int hoveredColor) {
        super(x, y, width, height, message.getFormattedText(), onPress);
        this.backgroundColor = backgroundColor;
        this.hoveredColor = hoveredColor;
    }

    @Override
    public void renderButton(int mouseX, int mouseY, float partialTick) {
        boolean highlighted = isHovered() || isFocused();
        Minecraft.getMinecraft().renderEngine.bindTexture(WIDGETS_LOCATION);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        GL11.glEnable(GL11.GL_BLEND);
        int textureY = !active ? 46 : highlighted ? 86 : 66;
        drawNineSliced(xPosition, yPosition, getButtonWidth(), height, 20, 4, 200, 20, 0, textureY);
        int color = !active ? 0xCC555555 : highlighted ? hoveredColor : backgroundColor;
        fill(xPosition + 2, yPosition + 2,
                xPosition + getButtonWidth() - 2, yPosition + height - 2, color);
        drawColoredBevel(color);
        drawCenteredString(Minecraft.getMinecraft().fontRenderer, getMessage(),
                xPosition + getButtonWidth() / 2, yPosition + 6,
                active ? 0xFFFFFFFF : 0xFFA0A0A0);
    }

    private void drawNineSliced(int x, int y, int width, int height, int cw, int ch,
            int tw, int th, int u, int v) {
        int l = Math.min(cw, width / 2);
        int r = Math.min(cw, width - l);
        int t = Math.min(ch, height / 2);
        int b = Math.min(ch, height - t);
        int mw = width - l - r;
        int mh = height - t - b;
        int sw = Math.max(1, tw - cw * 2);
        int sh = Math.max(1, th - ch * 2);
        blitPatch(x, y, l, t, u, v);
        blitPatch(x + width - r, y, r, t, u + tw - r, v);
        blitPatch(x, y + height - b, l, b, u, v + th - b);
        blitPatch(x + width - r, y + height - b, r, b, u + tw - r, v + th - b);
        if (mw > 0) {
            blitRepeat(x + l, y, mw, t, u + cw, v, sw, t);
            blitRepeat(x + l, y + height - b, mw, b, u + cw, v + th - b, sw, b);
        }
        if (mh > 0) {
            blitRepeat(x, y + t, l, mh, u, v + ch, l, sh);
            blitRepeat(x + width - r, y + t, r, mh, u + tw - r, v + ch, r, sh);
        }
        if (mw > 0 && mh > 0) {
            blitRepeat(x + l, y + t, mw, mh, u + cw, v + ch, sw, sh);
        }
    }

    private void blitPatch(int x, int y, int width, int height, int u, int v) {
        if (width > 0 && height > 0) {
            drawTexturedModalRect(x, y, u, v, width, height);
        }
    }

    private void blitRepeat(int x, int y, int width, int height, int u, int v,
            int sourceWidth, int sourceHeight) {
        if (width <= 0 || height <= 0) return;
        for (int oy = 0; oy < height; oy += sourceHeight) {
            for (int ox = 0; ox < width; ox += sourceWidth) {
                blitPatch(x + ox, y + oy, Math.min(sourceWidth, width - ox),
                        Math.min(sourceHeight, height - oy), u, v);
            }
        }
    }

    private void drawColoredBevel(int color) {
        int highlight = shade(color, 1.35F);
        int shadow = shade(color, 0.55F);
        int corner = shade(color, 0.78F);
        int left = xPosition + 1;
        int top = yPosition + 1;
        int right = xPosition + getButtonWidth() - 2;
        int bottom = yPosition + height - 2;
        fill(left + 1, top, right, top + 1, highlight);
        fill(left, top + 1, left + 1, bottom, highlight);
        fill(left, top, left + 1, top + 1, highlight);
        fill(left + 1, bottom - 1, right, bottom + 1, shadow);
        fill(right, top + 1, right + 1, bottom, shadow);
        fill(right, bottom, right + 1, bottom + 1, shadow);
        fill(right, top, right + 1, top + 1, corner);
        fill(left, bottom, left + 1, bottom + 1, corner);
    }

    private static int shade(int color, float factor) {
        int red = Math.min(255, Math.round(((color >> 16) & 0xFF) * factor));
        int green = Math.min(255, Math.round(((color >> 8) & 0xFF) * factor));
        int blue = Math.min(255, Math.round((color & 0xFF) * factor));
        return 0xFF000000 | red << 16 | green << 8 | blue;
    }
}
