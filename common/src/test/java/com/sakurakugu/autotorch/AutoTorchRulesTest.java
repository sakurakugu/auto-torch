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
        assertEquals(8, AutoTorchRules.maxSafeSecondPassSpacing(0));
        assertEquals(4, AutoTorchRules.maxSafeSecondPassSpacing(7));
        assertEquals(3, AutoTorchRules.maxSafeSecondPassSpacing(9));
        assertEquals(2, AutoTorchRules.maxSafeSecondPassSpacing(10));
        assertEquals(1, AutoTorchRules.maxSafeSecondPassSpacing(11));
        assertEquals(1, AutoTorchRules.maxSafeSecondPassSpacing(15));

        assertEquals(3, AutoTorchRules.secondPassSpacing(8, 9, 1));
        assertEquals(1, AutoTorchRules.secondPassSpacing(8, 11, 1));
        assertEquals(4, AutoTorchRules.secondPassSpacing(8, 11, 4));
        assertTrue(AutoTorchRules.canTorchMeetLightThreshold(14));
        assertFalse(AutoTorchRules.canTorchMeetLightThreshold(15));
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
