package com.sakurakugu.autotorch;

/** 可独立测试的任务与刷新策略。 */
public final class AutoTorchRules {
    private AutoTorchRules() {
    }

    public static boolean consumesInventoryTorches(boolean creativePlayer, boolean requested) {
        return creativePlayer && requested;
    }

    public static int divideRoundUp(int value, int divisor) {
        if (value < 0 || divisor <= 0) {
            throw new IllegalArgumentException("Value must be non-negative and divisor must be positive");
        }
        return value / divisor + (value % divisor == 0 ? 0 : 1);
    }

    public static int lightOverlayRefreshIntervalTicks(int range) {
        if (range <= 16) {
            return 4;
        }
        if (range <= 24) {
            return 8;
        }
        if (range <= 32) {
            return 20;
        }
        if (range <= 48) {
            return 40;
        }
        return 80;
    }

    public static boolean boxesIntersect(
            int firstMinX, int firstMinY, int firstMinZ, int firstMaxX, int firstMaxY, int firstMaxZ,
            int secondMinX, int secondMinY, int secondMinZ, int secondMaxX, int secondMaxY, int secondMaxZ
    ) {
        return firstMinX <= secondMaxX && firstMaxX >= secondMinX
                && firstMinY <= secondMaxY && firstMaxY >= secondMinY
                && firstMinZ <= secondMaxZ && firstMaxZ >= secondMinZ;
    }

    public static boolean spheresIntersect(
            int firstX, int firstY, int firstZ, long firstRadiusSquared,
            int secondX, int secondY, int secondZ, long secondRadiusSquared
    ) {
        long dx = (long) firstX - secondX;
        long dy = (long) firstY - secondY;
        long dz = (long) firstZ - secondZ;
        double combinedRadius = Math.sqrt(firstRadiusSquared) + Math.sqrt(secondRadiusSquared);
        return dx * dx + dy * dy + dz * dz <= combinedRadius * combinedRadius;
    }

    public static boolean sphereIntersectsBox(
            int centerX, int centerY, int centerZ, long radiusSquared,
            int minX, int minY, int minZ, int maxX, int maxY, int maxZ
    ) {
        long dx = distanceOutside(centerX, minX, maxX);
        long dy = distanceOutside(centerY, minY, maxY);
        long dz = distanceOutside(centerZ, minZ, maxZ);
        return dx * dx + dy * dy + dz * dz <= radiusSquared;
    }

    private static long distanceOutside(int value, int min, int max) {
        if (value < min) {
            return (long) min - value;
        }
        if (value > max) {
            return (long) value - max;
        }
        return 0L;
    }
}
