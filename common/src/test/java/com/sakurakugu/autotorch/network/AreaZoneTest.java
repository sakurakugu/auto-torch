package com.sakurakugu.autotorch.network;

import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AreaZoneTest {
    @Test
    void zeroRadiusSphereContainsOnlyItsCenter() {
        BlockPos center = new BlockPos(3, 5, 7);
        AreaZone sphere = new AreaZone(AreaShape.SPHERE, center, center);

        assertEquals(0, sphere.radius());
        assertEquals(center, sphere.min());
        assertEquals(center, sphere.max());
        assertTrue(sphere.contains(center));
        assertFalse(sphere.contains(center.add(1, 0, 0)));
    }
}
