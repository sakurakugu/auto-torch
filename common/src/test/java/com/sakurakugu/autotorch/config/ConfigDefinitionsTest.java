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
    void everyClientSettingBelongsToExactlyOneSettingsPage() {
        Set<ConfigDefinitions.Value> grouped = new HashSet<>();
        for (var category : ConfigDefinitions.ClientCategory.values()) {
            assertFalse(category.definitions().isEmpty(), category.name());
            for (var definition : category.definitions()) {
                assertTrue(grouped.add(definition), () -> "配置出现在多个分组：" + definition.key());
            }
        }
        assertEquals(new HashSet<>(ConfigDefinitions.CLIENT), grouped);
        assertTrue(ConfigDefinitions.ClientCategory.LIGHTING_TASK.definitions().contains(ConfigDefinitions.SELECTION_TOOL_ITEM));
        assertTrue(ConfigDefinitions.ClientCategory.LIGHTING_TASK.definitions().contains(ConfigDefinitions.TASK_PROGRESS_INTERVAL));
        assertTrue(ConfigDefinitions.ClientCategory.LIGHT_OVERLAY.definitions().contains(ConfigDefinitions.LIGHT_OVERLAY_NUMBER_ROTATION));
        assertTrue(ConfigDefinitions.ClientCategory.NEARBY_AUTO_TORCH.definitions().contains(ConfigDefinitions.NEARBY_AUTO_TORCH_ITEM));
    }

    @Test
    void allSettingsPagesAlsoShowSettingsEditableOnMainScreens() {
        // “全部设置”页覆盖分类内全部客户端配置；主界面没有对应控件的项也只在这里修改。
        assertTrue(ConfigDefinitions.ClientCategory.LIGHTING_TASK.definitions()
                .contains(ConfigDefinitions.TASK_DEFAULT_MAX_TORCHES));
        assertTrue(ConfigDefinitions.ClientCategory.LIGHTING_TASK.definitions()
                .contains(ConfigDefinitions.SELECTION_TOOL_ENABLED));
        assertTrue(ConfigDefinitions.ClientCategory.LIGHT_OVERLAY.definitions()
                .contains(ConfigDefinitions.LIGHT_OVERLAY_ENABLED));
        assertTrue(ConfigDefinitions.ClientCategory.NEARBY_AUTO_TORCH.definitions()
                .contains(ConfigDefinitions.NEARBY_AUTO_TORCH_ENABLED));
    }

    @Test
    void everyServerSettingBelongsToExactlyOneSettingsPage() {
        Set<ConfigDefinitions.Value> grouped = new HashSet<>();
        for (var category : ConfigDefinitions.ServerCategory.values()) {
            assertFalse(category.definitions().isEmpty(), category.name());
            for (var definition : category.definitions()) {
                assertTrue(grouped.add(definition), () -> "配置出现在多个服务端分组：" + definition.key());
            }
        }
        assertEquals(new HashSet<>(ConfigDefinitions.SERVER), grouped);
        assertTrue(ConfigDefinitions.ServerCategory.RULES.definitions()
                .contains(ConfigDefinitions.LIGHTING_TASK_ENABLED));
        assertTrue(ConfigDefinitions.ServerCategory.LIMITS.definitions()
                .contains(ConfigDefinitions.LIMIT_MAX_SPHERE_RADIUS));
        assertTrue(ConfigDefinitions.ServerCategory.PERFORMANCE.definitions()
                .contains(ConfigDefinitions.PERFORMANCE_SCAN_BUDGET_PER_TASK_TICK));
    }

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
            // 界面用的默认值文字与 TOML 注释共用同一份数据，范围行只含取值范围。
            assertEquals(1 + definition.rangeLines().length, bounds.length, definition.key());
            assertEquals(" 默认值：" + definition.defaultValueText(), bounds[0], definition.key());
            for (int index = 0; index < definition.rangeLines().length; index++) {
                assertEquals(definition.rangeLines()[index], bounds[index + 1], definition.key());
            }
            if (definition instanceof ConfigDefinitions.IntValue) {
                ConfigDefinitions.IntValue value = (ConfigDefinitions.IntValue) definition;
                assertEquals(" 默认值：" + value.defaultValue(), bounds[0], definition.key());
                assertEquals(" 取值范围：" + value.minValue() + " ~ " + value.maxValue() + "，步进：" + value.step(), bounds[1], definition.key());
            } else if (definition instanceof ConfigDefinitions.BooleanValue) {
                ConfigDefinitions.BooleanValue value = (ConfigDefinitions.BooleanValue) definition;
                assertEquals(1, bounds.length, definition.key());
                assertEquals(" 默认值：" + value.defaultValue(), bounds[0], definition.key());
            } else if (definition instanceof ConfigDefinitions.DoubleValue value) {
                assertEquals(" 默认值：" + value.defaultValue(), bounds[0], definition.key());
                assertEquals(" 取值范围：" + value.minValue() + " ~ " + value.maxValue(), bounds[1], definition.key());
            } else if (definition instanceof ConfigDefinitions.StringValue value) {
                assertEquals(" 默认值：" + value.defaultValue(), bounds[0], definition.key());
            }
        }
    }

    @Test
    void performanceSettingsRejectOffStepAndOutOfRangeValues() {
        assertTrue(ConfigDefinitions.NEARBY_AUTO_TORCH_SCAN_INTERVAL.accepts(1));
        assertTrue(ConfigDefinitions.NEARBY_AUTO_TORCH_SCAN_INTERVAL.accepts(40));
        assertFalse(ConfigDefinitions.NEARBY_AUTO_TORCH_SCAN_INTERVAL.accepts(0));
        assertFalse(ConfigDefinitions.NEARBY_AUTO_TORCH_DARK_CONFIRM.accepts(6));
        assertTrue(ConfigDefinitions.NEARBY_AUTO_TORCH_DARK_CONFIRM.accepts(40));
        assertFalse(ConfigDefinitions.NEARBY_AUTO_TORCH_RETRY_DELAY.accepts(15));
        assertTrue(ConfigDefinitions.NEARBY_AUTO_TORCH_RETRY_DELAY.accepts(100));
        assertFalse(ConfigDefinitions.LIGHT_OVERLAY_SCAN_BUDGET.accepts(13000));
        assertTrue(ConfigDefinitions.LIGHT_OVERLAY_SCAN_BUDGET.accepts(48000));
        assertFalse(ConfigDefinitions.LIGHT_OVERLAY_REFRESH_INTERVAL.accepts(101));
        assertTrue(ConfigDefinitions.LIGHT_OVERLAY_REFRESH_INTERVAL.accepts(400));
        assertFalse(ConfigDefinitions.SELECTION_OVERLAY_SPHERE_LATITUDE_SEGMENTS.accepts(14));
        assertTrue(ConfigDefinitions.SELECTION_OVERLAY_SPHERE_LATITUDE_SEGMENTS.accepts(48));
        assertFalse(ConfigDefinitions.TASK_PROGRESS_INTERVAL.accepts(15));
        assertTrue(ConfigDefinitions.TASK_PROGRESS_INTERVAL.accepts(40));
    }

    @Test
    void defaultMaxTorchesUsesStepOf128() {
        assertEquals(128, ConfigDefinitions.TASK_DEFAULT_MAX_TORCHES.step());
        assertTrue(ConfigDefinitions.TASK_DEFAULT_MAX_TORCHES.accepts(0));
        assertTrue(ConfigDefinitions.TASK_DEFAULT_MAX_TORCHES.accepts(128));
        assertTrue(ConfigDefinitions.TASK_DEFAULT_MAX_TORCHES.accepts(4096));
        assertFalse(ConfigDefinitions.TASK_DEFAULT_MAX_TORCHES.accepts(100));
    }

    @Test
    void clampingOffStepTorchLimitsNeverTurnsPositiveValuesIntoUnlimited() {
        // 0 表示不限；旧配置里的非步进正值应就近对齐，至少保留一个步进。
        assertEquals(0, ConfigDefinitions.TASK_DEFAULT_MAX_TORCHES.clamp(0));
        assertEquals(0, ConfigDefinitions.TASK_DEFAULT_MAX_TORCHES.clamp(-5));
        assertEquals(128, ConfigDefinitions.TASK_DEFAULT_MAX_TORCHES.clamp(1));
        assertEquals(128, ConfigDefinitions.TASK_DEFAULT_MAX_TORCHES.clamp(64));
        assertEquals(128, ConfigDefinitions.TASK_DEFAULT_MAX_TORCHES.clamp(100));
        assertEquals(256, ConfigDefinitions.TASK_DEFAULT_MAX_TORCHES.clamp(200));
        assertEquals(4096, ConfigDefinitions.TASK_DEFAULT_MAX_TORCHES.clamp(4096));
        assertEquals(4096, ConfigDefinitions.TASK_DEFAULT_MAX_TORCHES.clamp(9999));
    }

    @Test
    void taskIncludeSkyLightUsesPositiveKeyAndDefaultsOff() {
        assertEquals("lightingTaskDefaults.includeSkyLight", ConfigDefinitions.TASK_DEFAULT_INCLUDE_SKY_LIGHT.key());
        assertFalse(ConfigDefinitions.TASK_DEFAULT_INCLUDE_SKY_LIGHT.defaultValue());
        assertTrue(ConfigDefinitions.CLIENT.contains(ConfigDefinitions.TASK_DEFAULT_INCLUDE_SKY_LIGHT));
    }

    @Test
    void symbolButtonsBelongToLightingTaskPageAndDefaultToText() {
        assertEquals("selectionOverlay.symbolButtons", ConfigDefinitions.SELECTION_OVERLAY_SYMBOL_BUTTONS.key());
        assertFalse(ConfigDefinitions.SELECTION_OVERLAY_SYMBOL_BUTTONS.defaultValue());
        assertTrue(ConfigDefinitions.CLIENT.contains(ConfigDefinitions.SELECTION_OVERLAY_SYMBOL_BUTTONS));
        assertTrue(ConfigDefinitions.ClientCategory.LIGHTING_TASK.definitions()
                .contains(ConfigDefinitions.SELECTION_OVERLAY_SYMBOL_BUTTONS));
    }

    @Test
    void colorsUseSixDigitRgbAndWidthsRepairInvalidNumbers() {
        assertTrue(ConfigDefinitions.LIGHT_OVERLAY_ALWAYS_RISK_COLOR.accepts("a0B1c2"));
        assertFalse(ConfigDefinitions.LIGHT_OVERLAY_ALWAYS_RISK_COLOR.accepts("#FF3030"));
        assertFalse(ConfigDefinitions.LIGHT_OVERLAY_ALWAYS_RISK_COLOR.accepts("FF3030FF"));
        assertFalse(ConfigDefinitions.LIGHT_OVERLAY_ALWAYS_RISK_COLOR.accepts("GG3030"));
        assertEquals(0.75, ConfigDefinitions.LIGHT_OVERLAY_FAR_LINE_WIDTH.clamp(0.5));
        assertEquals(2.5, ConfigDefinitions.LIGHT_OVERLAY_FAR_LINE_WIDTH.clamp(10.0));
        assertEquals(0.75, ConfigDefinitions.LIGHT_OVERLAY_FAR_LINE_WIDTH.clamp(Double.NaN));
    }

    private static void assertDefaultWithinRange(ConfigDefinitions.IntValue definition) {
        assertTrue(definition.accepts(definition.defaultValue()), definition.key());
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
