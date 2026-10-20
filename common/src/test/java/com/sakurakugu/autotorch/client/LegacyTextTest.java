package com.sakurakugu.autotorch.client;

import net.minecraft.util.text.TextComponentString;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 校验 1.9.4 的 .lang 字面量换行会被还原，保证全部设置页面的标签与提示和 1.13.2 一致。 */
class LegacyTextTest {
    @Test
    void normalizesEscapedNewlines() {
        assertEquals("第一行\n第二行", LegacyText.normalizeNewlines("第一行\\n第二行"));
        assertEquals("没有换行", LegacyText.normalizeNewlines("没有换行"));
    }

    @Test
    void componentStringNormalizesEscapedNewlines() {
        // 组件的 getFormattedText 可能带结尾的格式重置码，这里只校验换行已被还原。
        String text = LegacyText.string(new TextComponentString("标签\\n详情"));
        assertTrue(text.startsWith("标签\n详情"), text);
    }

    @Test
    void stringOverloadKeepsRealNewlines() {
        assertEquals("A\nB", LegacyText.string("A\nB"));
    }
}
