package com.sakurakugu.autotorch.network;

import com.sakurakugu.autotorch.AutoTorchRules;
import java.util.Objects;
import com.sakurakugu.autotorch.compat.BlockPos;

/** 由 A/B 两点定义的球形或轴对齐长方体区域。 */
public final class AreaZone {
    public static final int MAX_SPHERE_RADIUS = 160;
    private final AreaShape shape;
    private final BlockPos first;
    private final BlockPos second;

    public AreaZone(AreaShape shape, BlockPos first, BlockPos second) {
        this.shape = shape;
        this.first = first.getImmutable();
        this.second = second.getImmutable();
    }

    public AreaShape shape() {
        return shape;
    }

    public BlockPos first() {
        return first;
    }

    public BlockPos second() {
        return second;
    }

    public BlockPos min() {
        if (shape == AreaShape.SPHERE) {
            int radius = radius();
            return first.add(-radius, -radius, -radius);
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
            return first.add(radius, radius, radius);
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

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof AreaZone)) return false;
        AreaZone zone = (AreaZone) other;
        return shape == zone.shape && first.equals(zone.first) && second.equals(zone.second);
    }

    @Override
    public int hashCode() {
        return Objects.hash(shape, first, second);
    }
}
