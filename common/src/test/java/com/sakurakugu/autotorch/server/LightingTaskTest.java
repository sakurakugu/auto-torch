package com.sakurakugu.autotorch.server;

import net.minecraft.util.IChatComponent;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.EnumChatFormatting;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class LightingTaskTest {
    @Test
    void formatsFirstPassProgressBarForLegacyOverlay() {
        assertEquals(
                EnumChatFormatting.GRAY + "|||||"
                        + EnumChatFormatting.DARK_GRAY + "|||||||||||||||"
                        + EnumChatFormatting.RESET,
                LightingTask.formattedProgressBar(0, 5));
    }

    @Test
    void formatsSecondPassProgressBarForLegacyOverlay() {
        assertEquals(
                EnumChatFormatting.GREEN + "||||||||||||"
                        + EnumChatFormatting.GRAY + "||||||||"
                        + EnumChatFormatting.RESET,
                LightingTask.formattedProgressBar(1, 12));
    }

    @Test
    void preservesFormattingCodesThroughTextComponentSerialization() {
        String bar = LightingTask.formattedProgressBar(1, 12);
        ChatComponentTranslation message = new ChatComponentTranslation(
                "message.autotorch.progress", bar, 75, 16);

        IChatComponent decoded = IChatComponent.Serializer.jsonToComponent(
                IChatComponent.Serializer.componentToJson(message));

        ChatComponentTranslation decodedTranslation = assertInstanceOf(ChatComponentTranslation.class, decoded);
        assertEquals(bar, decodedTranslation.getFormatArgs()[0]);
    }
}
