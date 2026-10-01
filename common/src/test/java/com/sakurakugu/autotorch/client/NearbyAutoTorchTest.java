package com.sakurakugu.autotorch.client;

import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class NearbyAutoTorchTest {
    private static final int WINDOW_TICKS = NearbyAutoTorch.DARK_CONFIRM_TICKS;
    private static final int THRESHOLD = 4;

    @Test
    void requiresTheConfiguredDarkWindow() {
        NearbyAutoTorch.LightStability stability = new NearbyAutoTorch.LightStability();
        BlockPos position = new BlockPos(1, 2, 3);

        assertFalse(stability.confirm(position, 0, THRESHOLD, 100L, WINDOW_TICKS));
        // 窗口未满，即使已经黑了一个扫描间隔的整数倍也不放过。
        assertFalse(stability.confirm(position, 0, THRESHOLD, 100L + WINDOW_TICKS - 1L, WINDOW_TICKS));
        assertTrue(stability.confirm(position, 0, THRESHOLD, 100L + WINDOW_TICKS, WINDOW_TICKS));
    }

    @Test
    void brightScanBreaksDarkConfirmation() {
        NearbyAutoTorch.LightStability stability = new NearbyAutoTorch.LightStability();
        BlockPos position = new BlockPos(1, 2, 3);

        assertFalse(stability.confirm(position, 0, THRESHOLD, 100L, WINDOW_TICKS));
        assertFalse(stability.confirm(position, THRESHOLD, THRESHOLD, 110L, WINDOW_TICKS));
        // 中间亮过一次，计时从头开始。
        assertFalse(stability.confirm(position, 0, THRESHOLD, 120L, WINDOW_TICKS));
        assertTrue(stability.confirm(position, 0, THRESHOLD, 130L, WINDOW_TICKS));
    }

    @Test
    void darkWindowLastsOneScanInterval() {
        assertEquals(NearbyAutoTorch.SCAN_INTERVAL_TICKS, NearbyAutoTorch.DARK_CONFIRM_TICKS);
    }

    @Test
    void forgottenPositionsRestartTheWindow() {
        NearbyAutoTorch.LightStability stability = new NearbyAutoTorch.LightStability();
        BlockPos position = new BlockPos(1, 2, 3);

        assertFalse(stability.confirm(position, 0, THRESHOLD, 100L, WINDOW_TICKS));
        // 中间几轮因为不合法或冷却被跳过，不能靠单次观测就通过。
        stability.forget(position);
        assertFalse(stability.confirm(position, 0, THRESHOLD, 150L, WINDOW_TICKS));
        assertTrue(stability.confirm(position, 0, THRESHOLD, 160L, WINDOW_TICKS));
    }

    @Test
    void pruneDropsStalePositions() {
        NearbyAutoTorch.LightStability stability = new NearbyAutoTorch.LightStability();
        BlockPos stale = new BlockPos(1, 2, 3);
        BlockPos fresh = new BlockPos(4, 5, 6);

        assertFalse(stability.confirm(stale, 0, THRESHOLD, 100L, WINDOW_TICKS));
        assertFalse(stability.confirm(fresh, 0, THRESHOLD, 130L, WINDOW_TICKS));
        // 只清掉远早于窗口的残留记录，扫描框内的坐标不受影响。
        stability.prune(130L, WINDOW_TICKS);
        // stale 的记录被清掉，需要重新计时。
        assertFalse(stability.confirm(stale, 0, THRESHOLD, 131L, WINDOW_TICKS));
        // fresh 的记录还在，够窗口就通过。
        assertTrue(stability.confirm(fresh, 0, THRESHOLD, 140L, WINDOW_TICKS));
    }
}
