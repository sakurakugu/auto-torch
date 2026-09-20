package com.sakurakugu.autotorch.client;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import org.lwjgl.opengl.GL11;

/** 为旧版界面补充新版控件使用的长文本往返滚动效果。 */
final class ScrollingText extends Gui {
    private static final ScrollingText INSTANCE = new ScrollingText();
    // LWJGL 2 的状态查询会强制检查至少 16 个整数的容量，即使剪裁框只返回 4 个整数。
    private static final IntBuffer SCISSOR_BOX = ByteBuffer.allocateDirect(16 * Integer.BYTES)
            .order(ByteOrder.nativeOrder()).asIntBuffer();
    private static final int[] PREVIOUS_SCISSOR = new int[4];

    private ScrollingText() {
    }

    static void render(FontRenderer font, String message, int left, int top, int right, int bottom, int color) {
        int textWidth = font.getStringWidth(message);
        int availableWidth = right - left;
        int textY = (top + bottom - 9) / 2 + 1;
        if (textWidth <= availableWidth) {
            INSTANCE.drawCenteredString(font, message, (left + right) / 2, textY, color);
            return;
        }

        int overflow = textWidth - availableWidth;
        double seconds = System.currentTimeMillis() / 1000.0D;
        double period = Math.max(overflow * 0.5D, 3.0D);
        double progress = Math.sin(Math.PI / 2.0D
                * Math.cos(Math.PI * 2.0D * seconds / period)) / 2.0D + 0.5D;
        int offset = (int) (progress * overflow);

        boolean hadScissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        if (hadScissor) {
            // LWJGL 2 只有 glGetInteger(int, IntBuffer)，没有 LWJGL 3 的 glGetIntegerv。
            SCISSOR_BOX.clear();
            GL11.glGetInteger(GL11.GL_SCISSOR_BOX, SCISSOR_BOX);
            SCISSOR_BOX.position(0);
            SCISSOR_BOX.get(PREVIOUS_SCISSOR);
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
        Minecraft minecraft = Minecraft.getMinecraft();
        double scale = new ScaledResolution(minecraft, minecraft.displayWidth, minecraft.displayHeight)
                .getScaleFactor();
        int x = (int) (left * scale);
        int y = (int) (minecraft.displayHeight - bottom * scale);
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
