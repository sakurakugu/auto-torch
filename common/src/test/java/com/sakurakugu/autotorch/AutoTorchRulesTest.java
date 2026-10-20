package com.sakurakugu.autotorch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class AutoTorchRulesTest {
    @Test
    void usesClientChoiceForCreativeAndSingleplayerOwner() {
        assertTrue(AutoTorchRules.consumesInventoryTorches(true, true, false, false));
        assertFalse(AutoTorchRules.consumesInventoryTorches(true, false, true, false));
        assertTrue(AutoTorchRules.consumesInventoryTorches(false, true, false, true));
        assertFalse(AutoTorchRules.consumesInventoryTorches(false, false, true, true));
    }

    @Test
    void usesServerRuleForMultiplayerSurvival() {
        assertTrue(AutoTorchRules.consumesInventoryTorches(false, false, true, false));
        assertFalse(AutoTorchRules.consumesInventoryTorches(false, true, false, false));
    }

    @Test
    void dividesSharedBudgetWithoutLosingRemainder() {
        assertEquals(8_000, AutoTorchRules.divideRoundUp(24_000, 3));
        assertEquals(8_001, AutoTorchRules.divideRoundUp(24_001, 3));
        assertEquals(0, AutoTorchRules.divideRoundUp(0, 3));
        assertThrows(IllegalArgumentException.class, () -> AutoTorchRules.divideRoundUp(1, 0));
    }

    @Test
    void limitsSecondPassSpacingForTheRequestedLightLevel() {
        assertEquals(8, AutoTorchRules.maxSafeSecondPassSpacing(0, 14));
        assertEquals(4, AutoTorchRules.maxSafeSecondPassSpacing(7, 14));
        assertEquals(3, AutoTorchRules.maxSafeSecondPassSpacing(9, 14));
        assertEquals(2, AutoTorchRules.maxSafeSecondPassSpacing(10, 14));
        assertEquals(1, AutoTorchRules.maxSafeSecondPassSpacing(11, 14));
        assertEquals(1, AutoTorchRules.maxSafeSecondPassSpacing(15, 14));

        assertEquals(3, AutoTorchRules.secondPassSpacing(8, 9, 1, 14));
        assertEquals(1, AutoTorchRules.secondPassSpacing(8, 11, 1, 14));
        assertEquals(4, AutoTorchRules.secondPassSpacing(8, 11, 4, 14));
        assertTrue(AutoTorchRules.canLightSourceMeetThreshold(14, 14));
        assertFalse(AutoTorchRules.canLightSourceMeetThreshold(15, 14));
    }

    @Test
    void adjustsSpacingAndWarningsToTheActualLightSource() {
        // 同为处理亮度 ≤7，灵魂火把需要比普通火把更密的补点。
        assertEquals(4, AutoTorchRules.secondPassSpacing(16, 7, 1, 14));
        assertEquals(1, AutoTorchRules.secondPassSpacing(16, 7, 1, 10));
        assertEquals(5, AutoTorchRules.maxSafeSecondPassSpacing(0, 10));
        assertEquals(4, AutoTorchRules.maxSafeSecondPassSpacing(0, 7));
        assertEquals(3, AutoTorchRules.maxSafeSecondPassSpacing(10, 15));
        assertEquals(3, AutoTorchRules.secondPassSpacing(16, 7, 3, 10));

        assertTrue(AutoTorchRules.canLightSourceMeetThreshold(10, 10));
        assertFalse(AutoTorchRules.canLightSourceMeetThreshold(11, 10));
        assertTrue(AutoTorchRules.canLightSourceMeetThreshold(15, 15));
        assertFalse(AutoTorchRules.canLightSourceMeetThreshold(0, 0));
        assertEquals(1, AutoTorchRules.maxSafeSecondPassSpacing(0, 0));
        assertEquals(1, AutoTorchRules.maxSafeSecondPassSpacing(15, 7));
        assertThrows(IllegalArgumentException.class, () -> AutoTorchRules.maxSafeSecondPassSpacing(0, -1));
        assertThrows(IllegalArgumentException.class, () -> AutoTorchRules.maxSafeSecondPassSpacing(0, 16));
    }

    @Test
    void safeSpacingNeverExcludesADarkPointInUnobstructedLight() {
        // 遍历全部有效亮度组合，验证间距内每个位置的曼哈顿衰减结果均高于阈值。
        for (int emission = 1; emission <= 15; emission++) {
            for (int threshold = 0; threshold < emission; threshold++) {
                int spacing = AutoTorchRules.maxSafeSecondPassSpacing(threshold, emission);
                for (int x = 0; x < spacing; x++) {
                    for (int y = 0; y < spacing; y++) {
                        for (int z = 0; z < spacing; z++) {
                            if (x * x + y * y + z * z < spacing * spacing) {
                                assertTrue(emission - x - y - z > threshold);
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    void identifiesDisjointExclusionBounds() {
        assertTrue(AutoTorchRules.boxesIntersect(
                0, 0, 0, 10, 10, 10,
                10, 5, 5, 20, 6, 6));
        assertFalse(AutoTorchRules.boxesIntersect(
                0, 0, 0, 10, 10, 10,
                11, 0, 0, 20, 10, 10));

        assertTrue(AutoTorchRules.sphereIntersectsBox(
                0, 0, 0, 25,
                3, -1, -1, 8, 1, 1));
        assertFalse(AutoTorchRules.sphereIntersectsBox(
                0, 0, 0, 25,
                4, 4, 0, 8, 8, 1));

        assertTrue(AutoTorchRules.spheresIntersect(0, 0, 0, 25, 10, 0, 0, 25));
        assertFalse(AutoTorchRules.spheresIntersect(0, 0, 0, 25, 11, 0, 0, 25));
    }
}
