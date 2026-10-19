package com.sakurakugu.autotorch.client;

import java.util.List;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.util.text.ITextComponent;

/** 为旧版界面的工具提示按屏幕可用宽度拆行。 */
final class TooltipRenderer {
    private TooltipRenderer() {
    }

    static List<String> split(FontRenderer font, ITextComponent tooltip, int screenWidth, int mouseX) {
        int leftWidth = mouseX - 20;
        int rightWidth = screenWidth - mouseX - 16;
        return font.listFormattedStringToWidth(tooltip.getFormattedText(), Math.max(1, Math.max(leftWidth, rightWidth)));
    }
}
