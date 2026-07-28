package com.sakurakugu.autotorch.server;

import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.TextFormatting;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class LightingTaskTest {
    @Test
    void formatsFirstPassProgressBarForLegacyOverlay() {
        assertEquals(
                TextFormatting.GRAY + "|||||"
                        + TextFormatting.DARK_GRAY + "|||||||||||||||"
                        + TextFormatting.RESET,
                LightingTask.formattedProgressBar(0, 5));
    }

    @Test
    void formatsSecondPassProgressBarForLegacyOverlay() {
        assertEquals(
                TextFormatting.GREEN + "||||||||||||"
                        + TextFormatting.GRAY + "||||||||"
                        + TextFormatting.RESET,
                LightingTask.formattedProgressBar(1, 12));
    }

    @Test
    void preservesFormattingCodesThroughTextComponentSerialization() {
        String bar = LightingTask.formattedProgressBar(1, 12);
        TextComponentTranslation message = new TextComponentTranslation(
                "message.autotorch.progress", bar, 75, 16);

        ITextComponent decoded = ITextComponent.Serializer.jsonToComponent(
                ITextComponent.Serializer.componentToJson(message));

        TextComponentTranslation decodedTranslation = assertInstanceOf(TextComponentTranslation.class, decoded);
        assertEquals(bar, decodedTranslation.getFormatArgs()[0]);
    }
}
