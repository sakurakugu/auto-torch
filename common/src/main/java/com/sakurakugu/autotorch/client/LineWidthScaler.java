package com.sakurakugu.autotorch.client;

/** 按线段中点到相机的距离计算选区线宽。 */
final class LineWidthScaler {
    private static final double REFERENCE_DISTANCE = 8.0D;
    private static final double REFERENCE_DISTANCE_SQUARED = REFERENCE_DISTANCE * REFERENCE_DISTANCE;
    private static final double MINIMUM_WIDTH = 0.75D;

    private LineWidthScaler() {
    }

    static float scale(float baseWidth, double firstSquared, double secondSquared, double dotProduct) {
        double distanceSquared = (firstSquared + secondSquared + 2.0D * dotProduct) * 0.25D;
        if (distanceSquared <= REFERENCE_DISTANCE_SQUARED) {
            return baseWidth;
        }
        return (float) Math.max(MINIMUM_WIDTH,
                baseWidth * REFERENCE_DISTANCE / Math.sqrt(distanceSquared));
    }
}
