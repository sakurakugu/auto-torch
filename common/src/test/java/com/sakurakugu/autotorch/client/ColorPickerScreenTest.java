package com.sakurakugu.autotorch.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ColorPickerScreenTest {
    @Test
    void rgbBarTopGivesMaximumAndBottomGivesMinimum() {
        // 通道条顶部应取 255、底部应取 0；此前顶部会得到 1，底部会得到负数。
        assertEquals(255, ColorPickerScreen.channelFromTopDownRatio(0.0));
        assertEquals(0, ColorPickerScreen.channelFromTopDownRatio(1.0));
        assertEquals(128, ColorPickerScreen.channelFromTopDownRatio(0.5));
    }

    @Test
    void rgbBarRatioIsClampedWhenDraggedOutside() {
        assertEquals(255, ColorPickerScreen.channelFromTopDownRatio(-0.5));
        assertEquals(0, ColorPickerScreen.channelFromTopDownRatio(1.5));
    }

    @Test
    void hsvRoundTripPreservesColors() {
        int[] colors = { 0x000000, 0xFFFFFF, 0xFF0000, 0x00FF00, 0x0000FF, 0x123456 };
        for (int color : colors) {
            float[] hsv = ColorPickerScreen.rgbToHsv(color);
            assertEquals(color, ColorPickerScreen.hsvToRgb(hsv[0], hsv[1], hsv[2]));
        }
    }
}
