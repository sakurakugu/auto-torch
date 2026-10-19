package com.sakurakugu.autotorch.client;

import net.minecraft.util.text.ITextComponent;
import org.lwjgl.opengl.GL11;

/** 适配 1.15.2 只接收字符串的界面 API。 */
public final class LegacyText {
    private LegacyText() { }

    public static String string(ITextComponent text) { return text.getFormattedText(); }
    public static String string(String text) { return text; }

    /** 旧版 RenderSystem 尚未提供裁剪封装。 */
    public static void enableScissor(int x, int y, int width, int height) {
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(x, y, width, height);
    }

    public static void disableScissor() {
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
    }
}
