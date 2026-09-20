package com.sakurakugu.autotorch.client;

import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/** 为旧版界面的工具提示按屏幕可用宽度拆行。 */
final class TooltipRenderer {
    private TooltipRenderer() {
    }

    static List<FormattedCharSequence> split(Font font, Component tooltip, int screenWidth, int mouseX) {
        int leftWidth = mouseX - 20;
        int rightWidth = screenWidth - mouseX - 16;
        return font.split(tooltip, Math.max(1, Math.max(leftWidth, rightWidth)));
    }
}
