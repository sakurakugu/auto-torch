package com.sakurakugu.autotorch.client;

import com.sakurakugu.autotorch.config.ConfigDefinitions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ConfigValueFormatTest {
    /** 5~40、步进 5 的整数设置。 */
    private static final ConfigDefinitions.IntValue STEPPED = ConfigDefinitions.NEARBY_AUTO_TORCH_DARK_CONFIRM;
    /** 0.25~8.0 的小数设置。 */
    private static final ConfigDefinitions.DoubleValue DECIMAL = ConfigDefinitions.LIGHT_OVERLAY_NEAR_LINE_WIDTH;

    @Test
    void alignsIntegerSettingsToStepAndClamps() {
        assertEquals(10.0, ConfigValueFormat.snap(STEPPED, 10), 1e-9);
        assertEquals(25.0, ConfigValueFormat.snap(STEPPED, 22.5), 1e-9);
        assertEquals(5.0, ConfigValueFormat.snap(STEPPED, -100), 1e-9);
        assertEquals(40.0, ConfigValueFormat.snap(STEPPED, 100), 1e-9);
    }

    @Test
    void keepsDecimalSettingsOnQuarterSteps() {
        assertEquals(0.25, ConfigValueFormat.snap(DECIMAL, 0.2), 1e-9);
        assertEquals(4.25, ConfigValueFormat.snap(DECIMAL, 4.125), 1e-9);
        assertEquals(8.0, ConfigValueFormat.snap(DECIMAL, 9.0), 1e-9);
    }

    @Test
    void formatsValuesForSliderLabel() {
        assertEquals("10", ConfigValueFormat.format(ConfigDefinitions.NEARBY_AUTO_TORCH_SCAN_INTERVAL, 10));
        assertEquals("2.5", ConfigValueFormat.format(DECIMAL, 2.5));
        assertEquals("0.75", ConfigValueFormat.format(ConfigDefinitions.LIGHT_OVERLAY_FAR_LINE_WIDTH, 0.75));
        assertEquals("8", ConfigValueFormat.format(DECIMAL, 8.0));
    }

    @Test
    void mapsSliderFractionToStepAlignedValues() {
        assertEquals(5.0, ConfigValueFormat.valueAt(STEPPED, 0), 1e-9);
        assertEquals(10.0, ConfigValueFormat.valueAt(STEPPED, 0.15), 1e-9);
        assertEquals(40.0, ConfigValueFormat.valueAt(STEPPED, 1), 1e-9);
        assertEquals(0.25, ConfigValueFormat.valueAt(DECIMAL, 0), 1e-9);
        assertEquals(8.0, ConfigValueFormat.valueAt(DECIMAL, 1), 1e-9);
    }

    @Test
    void computesClampedFractionForThumbPosition() {
        assertEquals(0.0, ConfigValueFormat.fraction(STEPPED, 5), 1e-9);
        assertEquals(0.5, ConfigValueFormat.fraction(STEPPED, 22.5), 1e-9);
        assertEquals(1.0, ConfigValueFormat.fraction(STEPPED, 40), 1e-9);
        assertEquals(0.0, ConfigValueFormat.fraction(STEPPED, -1), 1e-9);
        assertEquals(1.0, ConfigValueFormat.fraction(STEPPED, 99), 1e-9);
    }
}
