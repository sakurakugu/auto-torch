package com.sakurakugu.autotorch.client;

import net.minecraft.util.ChatComponentText;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class LightingScreenTest {
    @Test
    void ignoresFormattingCodesWhenCheckingForVisibleMessages() {
        ChatComponentText empty = new ChatComponentText("");

        assertFalse(empty.getFormattedText().isEmpty());
        assertFalse(LightingScreen.hasVisibleText(empty));
        assertTrue(LightingScreen.hasVisibleText(new ChatComponentText("message")));
    }
}
