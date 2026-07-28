package com.sakurakugu.autotorch.compat;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

/** 将 1.7.10 的整数坐标世界 API 收敛到统一入口。 */
public final class WorldAccess {
    private WorldAccess() {
    }

    public static Block block(World world, BlockPos pos) {
        return world.getBlock(pos.getX(), pos.getY(), pos.getZ());
    }

    public static boolean isAir(World world, BlockPos pos) {
        return world.isAirBlock(pos.getX(), pos.getY(), pos.getZ());
    }

    public static boolean exists(World world, BlockPos pos) {
        return world.blockExists(pos.getX(), pos.getY(), pos.getZ());
    }

    public static boolean isTopSolid(World world, BlockPos pos) {
        return world.isSideSolid(pos.getX(), pos.getY(), pos.getZ(), ForgeDirection.UP);
    }

    public static int blockLight(World world, BlockPos pos) {
        return world.getSavedLightValue(EnumSkyBlock.Block, pos.getX(), pos.getY(), pos.getZ());
    }

    public static int skyLight(World world, BlockPos pos) {
        return world.getSavedLightValue(EnumSkyBlock.Sky, pos.getX(), pos.getY(), pos.getZ());
    }

    public static boolean setTorch(World world, BlockPos pos, int flags) {
        return world.setBlock(pos.getX(), pos.getY(), pos.getZ(), Blocks.torch, 0, flags);
    }
}
