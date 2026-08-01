package com.sakurakugu.autotorch.config;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigDefinitionsTest {
    @Test
    void configurationKeysAreUnique() {
        List<ConfigDefinitions.Value> definitions = new java.util.ArrayList<>(ConfigDefinitions.CLIENT);
        definitions.addAll(ConfigDefinitions.SERVER);

        Set<String> keys = new HashSet<>();
        definitions.forEach(definition -> assertTrue(keys.add(definition.key()),
                () -> "Duplicate configuration key: " + definition.key()));
        assertEquals(definitions.size(), keys.size());
    }

    @Test
    void integerDefaultsAreWithinTheirRanges() {
        ConfigDefinitions.CLIENT.stream()
                .filter(ConfigDefinitions.IntValue.class::isInstance)
                .map(ConfigDefinitions.IntValue.class::cast)
                .forEach(ConfigDefinitionsTest::assertDefaultWithinRange);
        ConfigDefinitions.SERVER.stream()
                .filter(ConfigDefinitions.IntValue.class::isInstance)
                .map(ConfigDefinitions.IntValue.class::cast)
                .forEach(ConfigDefinitionsTest::assertDefaultWithinRange);
    }

    @Test
    void areaLightThresholdKeepsLegacyDefaultAndFullLightRange() {
        assertEquals(0, ConfigDefinitions.TASK_DEFAULT_LIGHT_THRESHOLD.defaultValue());
        assertEquals(0, ConfigDefinitions.TASK_DEFAULT_LIGHT_THRESHOLD.minValue());
        assertEquals(15, ConfigDefinitions.TASK_DEFAULT_LIGHT_THRESHOLD.maxValue());
    }

    @Test
    void lightingTaskIsEnabledByDefault() {
        assertTrue(ConfigDefinitions.SERVER.contains(ConfigDefinitions.LIGHTING_TASK_ENABLED));
        assertTrue(ConfigDefinitions.LIGHTING_TASK_ENABLED.defaultValue());
    }

    private static void assertDefaultWithinRange(ConfigDefinitions.IntValue definition) {
        assertTrue(definition.defaultValue() >= definition.minValue(), definition.key());
        assertTrue(definition.defaultValue() <= definition.maxValue(), definition.key());
    }
}
