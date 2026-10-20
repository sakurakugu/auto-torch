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
        // 1.9.4 的 .lang 把换行保存为字面量 \n，先还原再交给字体折行。
        return font.listFormattedStringToWidth(LegacyText.string(tooltip), Math.max(1, Math.max(leftWidth, rightWidth)));
    }
}
