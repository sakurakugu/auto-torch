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
    void keepsNearbyOverlayFastAndSlowsLargerRanges() {
        assertEquals(4, AutoTorchRules.lightOverlayRefreshIntervalTicks(16));
        assertEquals(8, AutoTorchRules.lightOverlayRefreshIntervalTicks(24));
        assertEquals(20, AutoTorchRules.lightOverlayRefreshIntervalTicks(32));
        assertEquals(40, AutoTorchRules.lightOverlayRefreshIntervalTicks(48));
        assertEquals(80, AutoTorchRules.lightOverlayRefreshIntervalTicks(64));
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
