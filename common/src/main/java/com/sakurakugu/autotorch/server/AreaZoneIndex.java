package com.sakurakugu.autotorch.server;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.sakurakugu.autotorch.network.AreaZone;
import net.minecraft.core.BlockPos;

/** 按固定大小的空间桶索引区域，避免每次查询都遍历全部区域。 */
final class AreaZoneIndex {
    private static final int CELL_SIZE = 16;

    private final Map<Long, List<AreaZone>> zonesByCell = new HashMap<>();

    AreaZoneIndex(List<AreaZone> zones) {
        for (AreaZone zone : zones) {
            BlockPos min = zone.min();
            BlockPos max = zone.max();
            int minCellX = cellCoordinate(min.getX());
            int minCellY = cellCoordinate(min.getY());
            int minCellZ = cellCoordinate(min.getZ());
            int maxCellX = cellCoordinate(max.getX());
            int maxCellY = cellCoordinate(max.getY());
            int maxCellZ = cellCoordinate(max.getZ());

            for (int cellX = minCellX; cellX <= maxCellX; cellX++) {
                for (int cellY = minCellY; cellY <= maxCellY; cellY++) {
                    for (int cellZ = minCellZ; cellZ <= maxCellZ; cellZ++) {
                        zonesByCell.computeIfAbsent(cellKey(cellX, cellY, cellZ), ignored -> new ArrayList<>())
                                .add(zone);
                    }
                }
            }
        }
    }

    boolean contains(BlockPos pos) {
        List<AreaZone> candidates = zonesByCell.get(cellKey(
                cellCoordinate(pos.getX()), cellCoordinate(pos.getY()), cellCoordinate(pos.getZ())));
        if (candidates == null) {
            return false;
        }
        for (AreaZone zone : candidates) {
            if (zone.contains(pos)) {
                return true;
            }
        }
        return false;
    }

    private static int cellCoordinate(int blockCoordinate) {
        return Math.floorDiv(blockCoordinate, CELL_SIZE);
    }

    private static long cellKey(int x, int y, int z) {
        return BlockPos.asLong(x, y, z);
    }
}
