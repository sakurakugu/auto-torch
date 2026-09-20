package com.sakurakugu.autotorch.client;

import com.mojang.blaze3d.platform.Window;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.util.Mth;
import org.lwjgl.opengl.GL11;

/** 为旧版界面补充新版控件使用的长文本往返滚动效果。 */
final class ScrollingText extends GuiComponent {
    private static final ScrollingText INSTANCE = new ScrollingText();
    private static final int[] PREVIOUS_SCISSOR = new int[4];

    private ScrollingText() {
    }

    static void render(Font font, String message, int left, int top, int right, int bottom, int color) {
        int textWidth = font.width(message);
        int availableWidth = right - left;
        int textY = (top + bottom - 9) / 2 + 1;
        if (textWidth <= availableWidth) {
            INSTANCE.drawCenteredString(font, message, (left + right) / 2, textY, color);
            return;
        }

        int overflow = textWidth - availableWidth;
        double seconds = Util.getMillis() / 1000.0D;
        double period = Math.max(overflow * 0.5D, 3.0D);
        double progress = Math.sin(Math.PI / 2.0D
                * Math.cos(Math.PI * 2.0D * seconds / period)) / 2.0D + 0.5D;
        int offset = (int) Mth.lerp(progress, 0.0D, overflow);

        boolean hadScissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        if (hadScissor) {
            GL11.glGetIntegerv(GL11.GL_SCISSOR_BOX, PREVIOUS_SCISSOR);
        }
        enableIntersectedScissor(left, top, right, bottom, hadScissor);
        INSTANCE.drawString(font, message, left - offset, textY, color);
        if (hadScissor) {
            GL11.glScissor(PREVIOUS_SCISSOR[0], PREVIOUS_SCISSOR[1],
                    PREVIOUS_SCISSOR[2], PREVIOUS_SCISSOR[3]);
        } else {
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
        }
    }

    private static void enableIntersectedScissor(int left, int top, int right, int bottom,
            boolean hadScissor) {
        Window window = Minecraft.getInstance().getWindow();
        double scale = window.getGuiScale();
        int x = (int) (left * scale);
        int y = (int) (window.getHeight() - bottom * scale);
        int width = Math.max(0, (int) ((right - left) * scale));
        int height = Math.max(0, (int) ((bottom - top) * scale));
        if (hadScissor) {
            int clippedRight = Math.min(x + width, PREVIOUS_SCISSOR[0] + PREVIOUS_SCISSOR[2]);
            int clippedTop = Math.min(y + height, PREVIOUS_SCISSOR[1] + PREVIOUS_SCISSOR[3]);
            x = Math.max(x, PREVIOUS_SCISSOR[0]);
            y = Math.max(y, PREVIOUS_SCISSOR[1]);
            width = Math.max(0, clippedRight - x);
            height = Math.max(0, clippedTop - y);
        }
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(x, y, width, height);
    }
}
