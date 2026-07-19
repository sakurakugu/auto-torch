package dev.elric.autotorch.network;

import net.minecraft.core.BlockPos;

/** 以中心和半径描述的轴对齐立方体排除区。 */
public record ExclusionZone(BlockPos center, int radius) {
    public ExclusionZone {
        // BlockPos.MutableBlockPos 也可作为参数传入，必须复制后再长期保存。
        center = center.immutable();
    }

    public BlockPos min() {
        return center.offset(-radius, -radius, -radius);
    }

    public BlockPos max() {
        return center.offset(radius, radius, radius);
    }

    public boolean contains(BlockPos pos) {
        return Math.abs(pos.getX() - center.getX()) <= radius
                && Math.abs(pos.getY() - center.getY()) <= radius
                && Math.abs(pos.getZ() - center.getZ()) <= radius;
    }
}
