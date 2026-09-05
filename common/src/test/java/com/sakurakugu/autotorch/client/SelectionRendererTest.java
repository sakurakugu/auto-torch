package com.sakurakugu.autotorch.client;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class SelectionRendererTest {
    @Test
    void keepsBaseWidthWithinEightBlocks() {
        assertEquals(3.0F, LineWidthScaler.scale(3.0F, 64.0D, 64.0D, 64.0D));
    }

    @Test
    void scalesWidthOutsideEightBlocks() {
        assertEquals(1.5F, LineWidthScaler.scale(3.0F, 256.0D, 256.0D, 256.0D));
    }

    @Test
    void neverScalesBelowMinimumWidth() {
        assertEquals(0.75F, LineWidthScaler.scale(3.0F, 4096.0D, 4096.0D, 4096.0D));
    }
}
