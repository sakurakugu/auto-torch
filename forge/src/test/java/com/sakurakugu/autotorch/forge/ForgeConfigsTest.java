package com.sakurakugu.autotorch.forge;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.sakurakugu.autotorch.config.ConfigDefinitions;
import com.sakurakugu.autotorch.config.ConfigDefinitions.BooleanValue;
import com.sakurakugu.autotorch.config.ConfigDefinitions.DoubleValue;
import com.sakurakugu.autotorch.config.ConfigDefinitions.IntValue;
import com.sakurakugu.autotorch.config.ConfigDefinitions.StringValue;
import com.sakurakugu.autotorch.config.ConfigDefinitions.Value;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ForgeConfigsTest {
    @Test
    void allDefinitionsRegisterWithTheirDefaults() {
        assertDefaults(ForgeConfigs.CLIENT, ConfigDefinitions.CLIENT);
        assertDefaults(ForgeConfigs.SERVER, ConfigDefinitions.SERVER);
    }

    private void assertDefaults(ForgeConfigs.Backend backend, Iterable<Value> definitions) {
        assertNotNull(backend.spec());
        for (Value definition : definitions) {
            String key = definition.key();
            assertNotNull(backend.spec().getSpec().get(key), key);
            if (definition instanceof BooleanValue) {
                boolean expected = ((BooleanValue) definition).defaultValue();
                assertEquals(expected, backend.getBoolean(key, !expected), key);
            } else if (definition instanceof IntValue) {
                assertEquals(((IntValue) definition).defaultValue(), backend.getInt(key, -1), key);
            } else if (definition instanceof DoubleValue) {
                assertEquals(((DoubleValue) definition).defaultValue(), backend.getDouble(key, -1), key);
            } else if (definition instanceof StringValue) {
                assertEquals(((StringValue) definition).defaultValue(), backend.getString(key, null), key);
            } else {
                fail("Unsupported config type: " + definition.getClass().getName());
            }
        }
    }

    @Test
    void decimalAndStringValuesRoundTrip() {
        ForgeConfigs.Backend backend = new ForgeConfigs.Backend();
        backend.define(new DoubleValue("overlay.width", 1.5, 0.5, 5.0, "线宽"));
        backend.define(new StringValue("overlay.markerColor", "ffffff", "颜色"));
        backend.define(new StringValue("selection.item", "minecraft:stick", "选区工具"));
        backend.finish();
        CommentedConfig config = CommentedConfig.inMemory();
        backend.spec().correct(config);
        backend.spec().setConfig(config);

        backend.setDouble("overlay.width", 2.75);
        backend.setString("overlay.markerColor", "aBc123");
        backend.setString("selection.item", "minecraft:torch");

        assertEquals(2.75, backend.getDouble("overlay.width", -1));
        assertEquals("aBc123", backend.getString("overlay.markerColor", null));
        assertEquals("minecraft:torch", backend.getString("selection.item", null));
        assertTrue(backend.spec().isCorrect(config));

        config.set("overlay.width", 99.0);
        assertFalse(backend.spec().isCorrect(config));
        config.set("overlay.width", 2.75);
        config.set("overlay.markerColor", "invalid");
        assertFalse(backend.spec().isCorrect(config));
        config.set("overlay.markerColor", 123456);
        assertFalse(backend.spec().isCorrect(config));
    }
}
