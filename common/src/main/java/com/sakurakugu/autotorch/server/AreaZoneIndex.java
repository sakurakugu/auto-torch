package com.sakurakugu.autotorch.server;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import com.sakurakugu.autotorch.network.AreaShape;
import com.sakurakugu.autotorch.network.AreaZone;
import net.minecraft.util.math.BlockPos;

/** 使用静态包围盒层次结构索引区域，使内存占用只与区域数量相关。 */
final class AreaZoneIndex {
    private static final int MAX_LEAF_SIZE = 4;

    private final Node root;

    AreaZoneIndex(List<AreaZone> zones) {
        if (zones.isEmpty()) {
            root = null;
            return;
        }

        List<Entry> entries = new ArrayList<>(zones.size());
        for (AreaZone zone : zones) {
            entries.add(new Entry(zone));
        }
        root = build(entries, 0, entries.size());
    }

    boolean contains(BlockPos pos) {
        return root != null && root.contains(pos.getX(), pos.getY(), pos.getZ());
    }

    private static Node build(List<Entry> entries, int fromIndex, int toIndex) {
        Bounds bounds = boundsOf(entries, fromIndex, toIndex);
        int size = toIndex - fromIndex;
        if (size <= MAX_LEAF_SIZE) {
            return new Node(bounds, entries.subList(fromIndex, toIndex).toArray(new Entry[0]));
        }

        final Axis splitAxis = bounds.longestAxis();
        Collections.sort(entries.subList(fromIndex, toIndex), new Comparator<Entry>() {
            @Override
            public int compare(Entry left, Entry right) {
                return Long.compare(left.center(splitAxis), right.center(splitAxis));
            }
        });
        int middle = fromIndex + size / 2;
        return new Node(bounds, build(entries, fromIndex, middle), build(entries, middle, toIndex));
    }

    private static Bounds boundsOf(List<Entry> entries, int fromIndex, int toIndex) {
        Entry first = entries.get(fromIndex);
        int minX = first.minX;
        int minY = first.minY;
        int minZ = first.minZ;
        int maxX = first.maxX;
        int maxY = first.maxY;
        int maxZ = first.maxZ;

        for (int index = fromIndex + 1; index < toIndex; index++) {
            Entry entry = entries.get(index);
            minX = Math.min(minX, entry.minX);
            minY = Math.min(minY, entry.minY);
            minZ = Math.min(minZ, entry.minZ);
            maxX = Math.max(maxX, entry.maxX);
            maxY = Math.max(maxY, entry.maxY);
            maxZ = Math.max(maxZ, entry.maxZ);
        }
        return new Bounds(minX, minY, minZ, maxX, maxY, maxZ);
    }

    private enum Axis {
        X, Y, Z
    }

    private static final class Bounds {
        private final int minX;
        private final int minY;
        private final int minZ;
        private final int maxX;
        private final int maxY;
        private final int maxZ;

        private Bounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
            this.minX = minX;
            this.minY = minY;
            this.minZ = minZ;
            this.maxX = maxX;
            this.maxY = maxY;
            this.maxZ = maxZ;
        }

        private boolean contains(int x, int y, int z) {
            return x >= minX && x <= maxX
                    && y >= minY && y <= maxY
                    && z >= minZ && z <= maxZ;
        }

        private Axis longestAxis() {
            long sizeX = (long) maxX - minX;
            long sizeY = (long) maxY - minY;
            long sizeZ = (long) maxZ - minZ;
            if (sizeX >= sizeY && sizeX >= sizeZ) {
                return Axis.X;
            }
            return sizeY >= sizeZ ? Axis.Y : Axis.Z;
        }
    }

    private static final class Entry {
        private final boolean sphere;
        private final int centerX;
        private final int centerY;
        private final int centerZ;
        private final long radiusSquared;
        private final int minX;
        private final int minY;
        private final int minZ;
        private final int maxX;
        private final int maxY;
        private final int maxZ;

        private Entry(AreaZone zone) {
            sphere = zone.shape() == AreaShape.SPHERE;
            centerX = zone.first().getX();
            centerY = zone.first().getY();
            centerZ = zone.first().getZ();
            radiusSquared = sphere ? zone.radiusSquared() : 0;

            BlockPos min = zone.min();
            BlockPos max = zone.max();
            minX = min.getX();
            minY = min.getY();
            minZ = min.getZ();
            maxX = max.getX();
            maxY = max.getY();
            maxZ = max.getZ();
        }

        private long center(Axis axis) {
            switch (axis) {
                case X:
                    return (long) minX + maxX;
                case Y:
                    return (long) minY + maxY;
                case Z:
                    return (long) minZ + maxZ;
                default:
                    throw new IllegalArgumentException("未知坐标轴: " + axis);
            }
        }

        private boolean contains(int x, int y, int z) {
            if (x < minX || x > maxX || y < minY || y > maxY || z < minZ || z > maxZ) {
                return false;
            }
            if (!sphere) {
                return true;
            }

            long dx = (long) x - centerX;
            long dy = (long) y - centerY;
            long dz = (long) z - centerZ;
            return dx * dx + dy * dy + dz * dz <= radiusSquared;
        }
    }

    private static final class Node {
        private final Bounds bounds;
        private final Node left;
        private final Node right;
        private final Entry[] entries;

        private Node(Bounds bounds, Entry[] entries) {
            this.bounds = bounds;
            this.left = null;
            this.right = null;
            this.entries = entries;
        }

        private Node(Bounds bounds, Node left, Node right) {
            this.bounds = bounds;
            this.left = left;
            this.right = right;
            this.entries = null;
        }

        private boolean contains(int x, int y, int z) {
            if (!bounds.contains(x, y, z)) {
                return false;
            }
            if (entries != null) {
                for (Entry entry : entries) {
                    if (entry.contains(x, y, z)) {
                        return true;
                    }
                }
                return false;
            }
            return left.contains(x, y, z) || right.contains(x, y, z);
        }
    }
}
