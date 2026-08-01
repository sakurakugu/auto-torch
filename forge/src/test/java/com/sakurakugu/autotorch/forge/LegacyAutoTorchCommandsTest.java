package com.sakurakugu.autotorch.forge;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegacyAutoTorchCommandsTest {
    @Test
    void exposesClientCommandAndSuggestions() {
        LegacyAutoTorchClientCommand command = new LegacyAutoTorchClientCommand();

        assertEquals("autotorch", command.getCommandName());
        assertEquals(0, command.getRequiredPermissionLevel());
        List<String> suggestions = command.addTabCompletionOptions(
                null, new String[] {"overlay", ""}, null);
        assertTrue(suggestions.contains("range"));
    }

    @Test
    void exposesOperatorServerConfigCommandAndSuggestions() {
        LegacyAutoTorchServerCommand command = new LegacyAutoTorchServerCommand();

        assertEquals("autotorch", command.getCommandName());
        assertEquals(2, command.getRequiredPermissionLevel());
        List<String> suggestions = command.addTabCompletionOptions(
                null, new String[] {"serverconfig", ""}, null);
        assertTrue(suggestions.contains("get"));
        assertTrue(suggestions.contains("set"));
        assertTrue(suggestions.contains("defaults"));
    }
}
