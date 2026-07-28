package com.sakurakugu.autotorch.compat;

import net.minecraft.entity.Entity;
import net.minecraft.util.Vec3;

/** 兼容 Minecraft 1.7.10 的不可变方块坐标。 */
public class BlockPos {
    public static final BlockPos ORIGIN = new BlockPos(0, 0, 0);

    protected int x;
    protected int y;
    protected int z;

    public BlockPos(int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public BlockPos(Entity entity) {
        this(floor(entity.posX), floor(entity.posY), floor(entity.posZ));
    }

    public BlockPos(Vec3 vector) {
        this(floor(vector.xCoord), floor(vector.yCoord), floor(vector.zCoord));
    }

    public int getX() { return x; }
    public int getY() { return y; }
    public int getZ() { return z; }

    public BlockPos add(int dx, int dy, int dz) {
        return dx == 0 && dy == 0 && dz == 0 ? this : new BlockPos(x + dx, y + dy, z + dz);
    }

    public BlockPos up() { return add(0, 1, 0); }
    public BlockPos down() { return add(0, -1, 0); }
    public BlockPos getImmutable() { return new BlockPos(x, y, z); }

    public double distanceSq(BlockPos other) {
        long dx = (long) x - other.x;
        long dy = (long) y - other.y;
        long dz = (long) z - other.z;
        return dx * dx + dy * dy + dz * dz;
    }

    public long toLong() {
        return ((long) x & 0x3FFFFFFL) << 38
                | ((long) z & 0x3FFFFFFL) << 12
                | (long) y & 0xFFFL;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof BlockPos)) return false;
        BlockPos pos = (BlockPos) other;
        return x == pos.x && y == pos.y && z == pos.z;
    }

    @Override
    public int hashCode() {
        return (y + z * 31) * 31 + x;
    }

    @Override
    public String toString() {
        return "BlockPos{" + x + "," + y + "," + z + "}";
    }

    private static int floor(double value) {
        int integer = (int) value;
        return value < integer ? integer - 1 : integer;
    }

    /** 扫描热路径复用的可变坐标，保存到集合前必须调用 getImmutable。 */
    public static final class MutableBlockPos extends BlockPos {
        public MutableBlockPos() {
            super(0, 0, 0);
        }

        public MutableBlockPos set(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
            return this;
        }
    }
}
