package com.sakurakugu.autotorch.client.ui;

import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.Minecraft;
import net.minecraft.util.ResourceLocation;
import net.minecraft.client.gui.Gui;

/**
 * 将新版控件贴图名称映射到旧版的原版整张贴图。
 * 旧版没有 {@code blitNineSliced} 与 {@code renderOutline}，这里用同样的九宫格切图和描边补足。
 */
public final class LegacyWidgetSprites {
    private static final ResourceLocation WIDGETS = new ResourceLocation("minecraft", "textures/gui/widgets.png");
    private static final ResourceLocation SLIDER = new ResourceLocation("autotorch", "textures/gui/slider.png");

    private LegacyWidgetSprites() { }

    public static void blit(ResourceLocation sprite, int x, int y, int width, int height) {
        String name = sprite.getResourcePath();
        boolean highlighted = name.endsWith("_highlighted");
        boolean slider = name.startsWith("widget/slider");
        int textureY = slider
                ? (name.contains("handle") ? 40 : 0) + (highlighted ? 20 : 0)
                : name.endsWith("_disabled") ? 46 : highlighted ? 86 : 66;
        Minecraft.getMinecraft().getTextureManager().bindTexture(slider ? SLIDER : WIDGETS);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.enableDepth();
        drawNineSliced(x, y, width, height, 20, 4, 200, 20, 0, textureY);
    }

    /** 旧版没有 renderOutline，用四条 1 像素线绘制同样的边框。 */
    public static void outline(int x, int y, int width, int height, int color) {
        Gui.drawRect(x, y, x + width, y + 1, color);
        Gui.drawRect(x, y + height - 1, x + width, y + height, color);
        Gui.drawRect(x, y + 1, x + 1, y + height - 1, color);
        Gui.drawRect(x + width - 1, y + 1, x + width, y + height - 1, color);
    }

    /** 按九宫格切分并平铺原版整张贴图。 */
    private static void drawNineSliced(int x, int y, int width, int height,
            int cornerWidth, int cornerHeight, int textureWidth, int textureHeight, int u, int v) {
        int left = Math.min(cornerWidth, width / 2);
        int right = Math.min(cornerWidth, width - left);
        int top = Math.min(cornerHeight, height / 2);
        int bottom = Math.min(cornerHeight, height - top);
        int centerWidth = width - left - right;
        int centerHeight = height - top - bottom;
        int sourceCenterWidth = Math.max(1, textureWidth - cornerWidth * 2);
        int sourceCenterHeight = Math.max(1, textureHeight - cornerHeight * 2);

        blitPatch(x, y, left, top, u, v, left, top);
        blitPatch(x + width - right, y, right, top,
                u + textureWidth - right, v, right, top);
        blitPatch(x, y + height - bottom, left, bottom,
                u, v + textureHeight - bottom, left, bottom);
        blitPatch(x + width - right, y + height - bottom, right, bottom,
                u + textureWidth - right, v + textureHeight - bottom, right, bottom);
        if (centerWidth > 0) {
            blitRepeatingPatch(x + left, y, centerWidth, top,
                    u + cornerWidth, v, sourceCenterWidth, top);
            blitRepeatingPatch(x + left, y + height - bottom, centerWidth, bottom,
                    u + cornerWidth, v + textureHeight - bottom, sourceCenterWidth, bottom);
        }
        if (centerHeight > 0) {
            blitRepeatingPatch(x, y + top, left, centerHeight,
                    u, v + cornerHeight, left, sourceCenterHeight);
            blitRepeatingPatch(x + width - right, y + top, right, centerHeight,
                    u + textureWidth - right, v + cornerHeight, right, sourceCenterHeight);
        }
        if (centerWidth > 0 && centerHeight > 0) {
            blitRepeatingPatch(x + left, y + top, centerWidth, centerHeight,
                    u + cornerWidth, v + cornerHeight, sourceCenterWidth, sourceCenterHeight);
        }
    }

    private static void blitPatch(int x, int y, int width, int height,
            int u, int v, int sourceWidth, int sourceHeight) {
        if (width > 0 && height > 0) {
            Gui.drawModalRectWithCustomSizedTexture(x, y, (float) u, (float) v, width, height, 256, 256);
        }
    }

    private static void blitRepeatingPatch(int x, int y, int width, int height,
            int u, int v, int sourceWidth, int sourceHeight) {
        if (width <= 0 || height <= 0 || sourceWidth <= 0 || sourceHeight <= 0) return;
        for (int offsetY = 0; offsetY < height; offsetY += sourceHeight) {
            int tileHeight = Math.min(sourceHeight, height - offsetY);
            for (int offsetX = 0; offsetX < width; offsetX += sourceWidth) {
                int tileWidth = Math.min(sourceWidth, width - offsetX);
                blitPatch(x + offsetX, y + offsetY, tileWidth, tileHeight,
                        u, v, tileWidth, tileHeight);
            }
        }
    }
}

