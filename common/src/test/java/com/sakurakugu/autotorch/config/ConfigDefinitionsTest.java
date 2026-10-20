package com.sakurakugu.autotorch.config;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

    @Test
    void everyValueHasChineseCommentLines() {
        for (ConfigDefinitions.Value definition : allDefinitions()) {
            String[] lines = definition.commentLines();
            assertTrue(lines.length > 0, definition.key());
            for (String line : lines) {
                // TOML 写出器在 # 之后不补空格，说明行正好带一个前导空格、没有多余空白。
                assertTrue(line.startsWith(" "), definition.key());
                assertEquals(" " + line.trim(), line, definition.key());
                assertTrue(containsHanCharacters(line), definition.key());
            }
        }
    }

    @Test
    void forgeCommentsLeaveDefaultsAndRangesToTheLoader() {
        for (ConfigDefinitions.Value definition : allDefinitions()) {
            for (String line : definition.commentLines()) {
                assertFalse(line.contains("默认值"), definition.key());
                assertFalse(line.contains("取值范围"), definition.key());
            }
        }
    }

    @Test
    void nightConfigCommentsAppendDefaultsAndRanges() {
        for (ConfigDefinitions.Value definition : allDefinitions()) {
            String[] lines = definition.fullCommentLines();
            String[] bounds = definition.boundsLines();
            assertEquals(definition.commentLines().length + bounds.length, lines.length, definition.key());
            for (int index = 0; index < bounds.length; index++) {
                assertEquals(bounds[index], lines[lines.length - bounds.length + index], definition.key());
            }
            if (definition instanceof ConfigDefinitions.IntValue) {
                ConfigDefinitions.IntValue value = (ConfigDefinitions.IntValue) definition;
                assertEquals(" 默认值：" + value.defaultValue(), bounds[0], definition.key());
                assertEquals(" 取值范围：" + value.minValue() + " ~ " + value.maxValue() + "，步进：" + value.step(), bounds[1], definition.key());
            } else if (definition instanceof ConfigDefinitions.DoubleValue) {
                ConfigDefinitions.DoubleValue value = (ConfigDefinitions.DoubleValue) definition;
                assertEquals(" 默认值：" + value.defaultValue(), bounds[0], definition.key());
                assertEquals(" 取值范围：" + value.minValue() + " ~ " + value.maxValue(), bounds[1], definition.key());
            } else if (definition instanceof ConfigDefinitions.StringValue) {
                ConfigDefinitions.StringValue value = (ConfigDefinitions.StringValue) definition;
                assertEquals(" 默认值：" + value.defaultValue(), bounds[0], definition.key());
            } else {
                ConfigDefinitions.BooleanValue value = (ConfigDefinitions.BooleanValue) definition;
                assertEquals(1, bounds.length, definition.key());
                assertEquals(" 默认值：" + value.defaultValue(), bounds[0], definition.key());
            }
        }
    }

    private static void assertDefaultWithinRange(ConfigDefinitions.IntValue definition) {
        assertTrue(definition.defaultValue() >= definition.minValue(), definition.key());
        assertTrue(definition.defaultValue() <= definition.maxValue(), definition.key());
    }

    private static List<ConfigDefinitions.Value> allDefinitions() {
        List<ConfigDefinitions.Value> definitions = new java.util.ArrayList<>(ConfigDefinitions.CLIENT);
        definitions.addAll(ConfigDefinitions.SERVER);
        return definitions;
    }

    private static boolean containsHanCharacters(String line) {
        for (int index = 0; index < line.length(); index++) {
            char character = line.charAt(index);
            if (character >= 0x4E00 && character <= 0x9FFF) {
                return true;
            }
        }
        return false;
    }
}
