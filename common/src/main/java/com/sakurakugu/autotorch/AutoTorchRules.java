package com.sakurakugu.autotorch;

/** 可独立测试的任务与刷新策略。 */
public final class AutoTorchRules {
    private static final int TORCH_LIGHT_LEVEL = 14;

    private AutoTorchRules() {
    }

    public static boolean consumesInventoryTorches(
            boolean creativePlayer, boolean requested, boolean survivalConsumesTorches,
            boolean singleplayerOwner
    ) {
        return creativePlayer || singleplayerOwner ? requested : survivalConsumesTorches;
    }

    public static int divideRoundUp(int value, int divisor) {
        if (value < 0 || divisor <= 0) {
            throw new IllegalArgumentException("Value must be non-negative and divisor must be positive");
        }
        return value / divisor + (value % divisor == 0 ? 0 : 1);
    }

    /**
     * 返回第二轮补点间距的安全上限，避免尚未达到目标亮度的位置被间距规则跳过。
     *
     * <p>方块光每沿一个坐标轴传播一格就减一。将距离尽可能平均分配到三轴时，
     * 可得到仍会低于或等于阈值的位置所具有的最小三维直线距离。</p>
     */
    public static int maxSafeSecondPassSpacing(int lightThreshold) {
        if (lightThreshold < 0) {
            throw new IllegalArgumentException("Light threshold must be non-negative");
        }
        if (lightThreshold >= TORCH_LIGHT_LEVEL) {
            return 1;
        }

        int distance = TORCH_LIGHT_LEVEL - lightThreshold;
        int base = distance / 3;
        int remainder = distance % 3;
        int minimumSquaredDistance = base * base * 3
                + remainder * (base * 2 + 1);
        return (int) Math.sqrt(minimumSquaredDistance);
    }

    /** 返回在服务端间距下限约束后的第二轮实际间距。 */
    public static int secondPassSpacing(int configuredSpacing, int lightThreshold, int minimumSpacing) {
        if (configuredSpacing < 1 || minimumSpacing < 1) {
            throw new IllegalArgumentException("Spacing must be positive");
        }
        int requestedSpacing = Math.max(1, configuredSpacing / 2);
        return Math.max(minimumSpacing, Math.min(requestedSpacing, maxSafeSecondPassSpacing(lightThreshold)));
    }

    /** 火把最高能提供 14 级方块光，因此只有更高的目标无法满足。 */
    public static boolean canTorchMeetLightThreshold(int lightThreshold) {
        return lightThreshold <= TORCH_LIGHT_LEVEL;
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
