package com.sakurakugu.autotorch.client.ui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/** 开关轨道与滑块沿用原版滑动条的贴图，保证背景、边框和手柄风格一致。 */
final class PixelControls {
    private static final ResourceLocation SLIDER = ResourceLocation.withDefaultNamespace("widget/slider");
    private static final ResourceLocation SLIDER_HIGHLIGHTED = ResourceLocation.withDefaultNamespace("widget/slider_highlighted");
    private static final ResourceLocation HANDLE = ResourceLocation.withDefaultNamespace("widget/slider_handle");
    private static final ResourceLocation HANDLE_HIGHLIGHTED = ResourceLocation.withDefaultNamespace("widget/slider_handle_highlighted");
    private static final int WIDTH = 24;
    private static final int HEIGHT = 12;
    private static final int HANDLE_WIDTH = 8;
    /** 开启状态的内底颜色，带透明度让原版深色轨道纹理透出来，与彩色按钮的 0xDD 底色保持一致。 */
    private static final int ON_COLOR = 0xDD36B54A;

    private PixelControls() { }

    static void toggle(GuiGraphics graphics, int x, int y, boolean enabled,
                       boolean hovered, boolean focused, boolean active) {
        int right = x + WIDTH, bottom = y + HEIGHT;
        // 轨道背景和 1 像素边框直接使用原版滑动条贴图，只在键盘聚焦时换成高亮版本，悬停时保持深色。
        graphics.blitSprite(active && focused ? SLIDER_HIGHLIGHTED : SLIDER, x, y, WIDTH, HEIGHT);
        // 开启时只在边框内部填色，保留原版边框；关闭时展示原版深色背景。
        if (active && enabled) {
            graphics.fill(x + 1, y + 1, right - 1, bottom - 1, ON_COLOR);
        }
        // 滑块使用原版手柄贴图：开启靠右、关闭靠左，悬停或聚焦时高亮。
        int handleX = enabled ? right - HANDLE_WIDTH : x;
        graphics.blitSprite(active && (hovered || focused) ? HANDLE_HIGHLIGHTED : HANDLE,
                handleX, y, HANDLE_WIDTH, HEIGHT);
    }
}
