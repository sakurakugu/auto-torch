package com.sakurakugu.autotorch.client;

import net.minecraft.util.IChatComponent;
import org.lwjgl.opengl.GL11;

/** 适配 1.15.2 只接收字符串的界面 API。 */
public final class LegacyText {
    private LegacyText() { }

    /**
     * 1.7.10 的 .lang 无法直接保存换行，构建时会把换行写成字面量 \n，
     * 这里还原成真实换行，保持与 1.13.2 及以上版本的显示一致。
     */
    public static String normalizeNewlines(String text) {
        return text == null ? null : text.replace("\\n", "\n");
    }

    public static String string(IChatComponent text) { return normalizeNewlines(text.getFormattedText()); }
    public static String string(String text) { return normalizeNewlines(text); }

    /** 旧版 RenderSystem 尚未提供裁剪封装。 */
    public static void enableScissor(int x, int y, int width, int height) {
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(x, y, width, height);
    }

    public static void disableScissor() {
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
    }
}
