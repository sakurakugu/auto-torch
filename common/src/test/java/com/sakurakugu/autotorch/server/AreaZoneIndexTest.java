package com.sakurakugu.autotorch.server;

import java.util.List;

import com.sakurakugu.autotorch.network.AreaShape;
import com.sakurakugu.autotorch.network.AreaZone;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AreaZoneIndexTest {
    @Test
    void findsBoxesAcrossNegativeCellBoundaries() {
        AreaZoneIndex index = new AreaZoneIndex(List.of(new AreaZone(
                AreaShape.BOX, new BlockPos(-17, -1, -17), new BlockPos(1, 16, 1))));

        assertTrue(index.contains(new BlockPos(-17, -1, -17)));
        assertTrue(index.contains(new BlockPos(0, 15, 0)));
        assertFalse(index.contains(new BlockPos(-18, -1, -17)));
        assertFalse(index.contains(new BlockPos(0, 17, 0)));
    }

    @Test
    void filtersCandidatesByExactSphereShape() {
        AreaZoneIndex index = new AreaZoneIndex(List.of(new AreaZone(
                AreaShape.SPHERE, BlockPos.ZERO, new BlockPos(5, 0, 0))));

        assertTrue(index.contains(new BlockPos(3, 4, 0)));
        assertFalse(index.contains(new BlockPos(5, 5, 0)));
        assertFalse(index.contains(new BlockPos(6, 0, 0)));
    }

    @Test
    void handlesEmptyIndex() {
        assertFalse(new AreaZoneIndex(List.of()).contains(BlockPos.ZERO));
    }
}
