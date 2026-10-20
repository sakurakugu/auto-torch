package com.sakurakugu.autotorch.client;

import java.util.List;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.util.IChatComponent;

/** 为旧版界面的工具提示按屏幕可用宽度拆行。 */
final class TooltipRenderer {
    private TooltipRenderer() {
    }

    // 1.7.10 的 FontRenderer.listFormattedStringToWidth 返回原始类型 List，转换为 List<String> 必然触发未检查告警
    @SuppressWarnings("unchecked")
    static List<String> split(FontRenderer font, IChatComponent tooltip, int screenWidth, int mouseX) {
        int leftWidth = mouseX - 20;
        int rightWidth = screenWidth - mouseX - 16;
        // 1.7.10 的 .lang 把换行保存为字面量 \n，先还原再交给字体折行。
        return font.listFormattedStringToWidth(LegacyText.string(tooltip), Math.max(1, Math.max(leftWidth, rightWidth)));
    }
}
