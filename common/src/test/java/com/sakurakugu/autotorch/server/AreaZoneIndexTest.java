package com.sakurakugu.autotorch.server;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import com.sakurakugu.autotorch.network.AreaShape;
import com.sakurakugu.autotorch.network.AreaZone;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AreaZoneIndexTest {
    @Test
    void findsBoxesAcrossNegativeCoordinates() {
        AreaZoneIndex index = new AreaZoneIndex(Collections.singletonList(new AreaZone(
                AreaShape.BOX, new BlockPos(-17, -1, -17), new BlockPos(1, 16, 1))));

        assertTrue(index.contains(new BlockPos(-17, -1, -17)));
        assertTrue(index.contains(new BlockPos(0, 15, 0)));
        assertFalse(index.contains(new BlockPos(-18, -1, -17)));
        assertFalse(index.contains(new BlockPos(0, 17, 0)));
    }

    @Test
    void filtersCandidatesByExactSphereShape() {
        AreaZoneIndex index = new AreaZoneIndex(Collections.singletonList(new AreaZone(
                AreaShape.SPHERE, BlockPos.ZERO, new BlockPos(5, 0, 0))));

        assertTrue(index.contains(new BlockPos(3, 4, 0)));
        assertFalse(index.contains(new BlockPos(5, 5, 0)));
        assertFalse(index.contains(new BlockPos(6, 0, 0)));
    }

    @Test
    void handlesEmptyIndex() {
        assertFalse(new AreaZoneIndex(Collections.emptyList()).contains(BlockPos.ZERO));
    }

    @Test
    void findsZonesAcrossBvhBranches() {
        AreaZoneIndex index = new AreaZoneIndex(Arrays.asList(
                box(-200, -10, -200, -180, 10, -180),
                box(-100, -10, 100, -80, 10, 120),
                box(0, -10, 0, 20, 10, 20),
                box(80, -10, -120, 100, 10, -100),
                box(180, -10, 180, 200, 10, 200)
        ));

        assertTrue(index.contains(new BlockPos(-190, 0, -190)));
        assertTrue(index.contains(new BlockPos(10, 0, 10)));
        assertTrue(index.contains(new BlockPos(190, 0, 190)));
        assertFalse(index.contains(new BlockPos(50, 0, 50)));
    }

    @Test
    void handlesMaximumSizedOverlappingSpheresWithoutExpandingTheirVolume() {
        List<AreaZone> zones = new ArrayList<>();
        for (int index = 0; index < 32; index++) {
            zones.add(new AreaZone(
                    AreaShape.SPHERE,
                    new BlockPos(index * 4, 0, 0),
                    new BlockPos(index * 4 + AreaZone.MAX_SPHERE_RADIUS, 0, 0)));
        }
        AreaZoneIndex index = new AreaZoneIndex(zones);

        assertTrue(index.contains(new BlockPos(0, AreaZone.MAX_SPHERE_RADIUS, 0)));
        assertTrue(index.contains(new BlockPos(31 * 4, 0, AreaZone.MAX_SPHERE_RADIUS)));
        assertFalse(index.contains(new BlockPos(0, AreaZone.MAX_SPHERE_RADIUS, 1)));
        assertFalse(index.contains(new BlockPos(1000, 0, 0)));
    }

    @Test
    void matchesDirectZoneChecksForMixedRandomAreas() {
        Random random = new Random(0xA17EA5L);
        List<AreaZone> zones = new ArrayList<>();
        for (int index = 0; index < 32; index++) {
            zones.add(randomZone(random, index % 2 == 0 ? AreaShape.BOX : AreaShape.SPHERE));
        }
        AreaZoneIndex index = new AreaZoneIndex(zones);

        for (int attempt = 0; attempt < 10_000; attempt++) {
            BlockPos pos = new BlockPos(
                    nextInt(random, -300, 301), nextInt(random, -100, 101), nextInt(random, -300, 301));
            boolean expected = zones.stream().anyMatch(zone -> zone.contains(pos));
            assertEquals(expected, index.contains(pos), "查询结果不一致: " + pos);
        }
    }

    private static AreaZone box(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        return new AreaZone(AreaShape.BOX, new BlockPos(minX, minY, minZ), new BlockPos(maxX, maxY, maxZ));
    }

    private static AreaZone randomZone(Random random, AreaShape shape) {
        BlockPos first = new BlockPos(
                nextInt(random, -200, 201), nextInt(random, -60, 61), nextInt(random, -200, 201));
        BlockPos second;
        if (shape == AreaShape.SPHERE) {
            second = first.offset(nextInt(random, 1, 81), nextInt(random, -20, 21), nextInt(random, -20, 21));
        } else {
            second = first.offset(nextInt(random, -80, 81), nextInt(random, -40, 41), nextInt(random, -80, 81));
        }
        return new AreaZone(shape, first, second);
    }

    private static int nextInt(Random random, int origin, int bound) {
        return origin + random.nextInt(bound - origin);
    }
}
