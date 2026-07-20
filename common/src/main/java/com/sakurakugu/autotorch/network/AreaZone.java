package com.sakurakugu.autotorch.network;

import com.sakurakugu.autotorch.AutoTorchRules;
import net.minecraft.core.BlockPos;

/** 由 A/B 两点定义的球形或轴对齐长方体区域。 */
public record AreaZone(AreaShape shape, BlockPos first, BlockPos second) {
    public static final int MAX_SPHERE_RADIUS = 160;

    public AreaZone {
        first = first.immutable();
        second = second.immutable();
    }

    public BlockPos min() {
        if (shape == AreaShape.SPHERE) {
            int radius = radius();
            return first.offset(-radius, -radius, -radius);
        }
        return new BlockPos(
                Math.min(first.getX(), second.getX()),
                Math.min(first.getY(), second.getY()),
                Math.min(first.getZ(), second.getZ())
        );
    }

    public BlockPos max() {
        if (shape == AreaShape.SPHERE) {
            int radius = radius();
            return first.offset(radius, radius, radius);
        }
        return new BlockPos(
                Math.max(first.getX(), second.getX()),
                Math.max(first.getY(), second.getY()),
                Math.max(first.getZ(), second.getZ())
        );
    }

    public long radiusSquared() {
        long dx = (long) second.getX() - first.getX();
        long dy = (long) second.getY() - first.getY();
        long dz = (long) second.getZ() - first.getZ();
        return dx * dx + dy * dy + dz * dz;
    }

    public int radius() {
        // 方块坐标是整数，任一轴的有效偏移不会超过欧氏半径的向下取整值。
        return (int) Math.sqrt(radiusSquared());
    }

    public boolean contains(BlockPos pos) {
        if (shape == AreaShape.SPHERE) {
            long dx = (long) pos.getX() - first.getX();
            long dy = (long) pos.getY() - first.getY();
            long dz = (long) pos.getZ() - first.getZ();
            return dx * dx + dy * dy + dz * dz <= radiusSquared();
        }
        BlockPos min = min();
        BlockPos max = max();
        return pos.getX() >= min.getX() && pos.getX() <= max.getX()
                && pos.getY() >= min.getY() && pos.getY() <= max.getY()
                && pos.getZ() >= min.getZ() && pos.getZ() <= max.getZ();
    }

    /** 判断两个离散区域是否可能包含同一个方块位置。 */
    public boolean intersects(AreaZone other) {
        if (shape == AreaShape.SPHERE && other.shape == AreaShape.SPHERE) {
            return AutoTorchRules.spheresIntersect(
                    first.getX(), first.getY(), first.getZ(), radiusSquared(),
                    other.first.getX(), other.first.getY(), other.first.getZ(), other.radiusSquared());
        }
        if (shape == AreaShape.SPHERE) {
            return sphereIntersectsBox(this, other);
        }
        if (other.shape == AreaShape.SPHERE) {
            return sphereIntersectsBox(other, this);
        }

        BlockPos thisMin = min();
        BlockPos thisMax = max();
        BlockPos otherMin = other.min();
        BlockPos otherMax = other.max();
        return AutoTorchRules.boxesIntersect(
                thisMin.getX(), thisMin.getY(), thisMin.getZ(),
                thisMax.getX(), thisMax.getY(), thisMax.getZ(),
                otherMin.getX(), otherMin.getY(), otherMin.getZ(),
                otherMax.getX(), otherMax.getY(), otherMax.getZ());
    }

    private static boolean sphereIntersectsBox(AreaZone sphere, AreaZone box) {
        BlockPos boxMin = box.min();
        BlockPos boxMax = box.max();
        return AutoTorchRules.sphereIntersectsBox(
                sphere.first.getX(), sphere.first.getY(), sphere.first.getZ(), sphere.radiusSquared(),
                boxMin.getX(), boxMin.getY(), boxMin.getZ(), boxMax.getX(), boxMax.getY(), boxMax.getZ());
    }
}
