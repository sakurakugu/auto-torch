package com.sakurakugu.autotorch.client;

import net.minecraft.util.text.TextComponentString;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class LightingScreenTest {
    @Test
    void ignoresFormattingCodesWhenCheckingForVisibleMessages() {
        TextComponentString empty = new TextComponentString("");

        assertFalse(empty.getFormattedText().isEmpty());
        assertFalse(LightingScreen.hasVisibleText(empty));
        assertTrue(LightingScreen.hasVisibleText(new TextComponentString("message")));
    }
}
