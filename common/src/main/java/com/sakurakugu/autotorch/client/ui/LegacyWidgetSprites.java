package com.sakurakugu.autotorch.client.ui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/** 将新版控件贴图名称映射到 1.20.1 的原版整张贴图。 */
public final class LegacyWidgetSprites {
    private static final ResourceLocation WIDGETS = ResourceLocation.tryBuild("minecraft", "textures/gui/widgets.png");
    private static final ResourceLocation SLIDER = ResourceLocation.tryBuild("minecraft", "textures/gui/slider.png");

    private LegacyWidgetSprites() { }

    public static void blit(GuiGraphics graphics, ResourceLocation sprite, int x, int y, int width, int height) {
        String name = sprite.getPath();
        boolean highlighted = name.endsWith("_highlighted");
        boolean slider = name.startsWith("widget/slider");
        int textureY = slider
                ? (name.contains("handle") ? 40 : 0) + (highlighted ? 20 : 0)
                : name.endsWith("_disabled") ? 46 : highlighted ? 86 : 66;
        graphics.blitNineSliced(slider ? SLIDER : WIDGETS, x, y, width, height,
                20, 4, 200, 20, 0, textureY);
    }
}
