package com.sakurakugu.autotorch.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.World;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.EnumActionResult;
import net.minecraft.init.Blocks;
import net.minecraft.world.EnumLightType;
import net.minecraft.block.state.BlockFaceShape;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.Vec3d;

/** 通过普通客户端交互，在玩家附近的黑暗区域放置快捷栏中的火把。 */
public final class NearbyAutoTorch {
    private static final int SCAN_INTERVAL_TICKS = 10;
    private static final int RETRY_DELAY_TICKS = 40;
    private static final int HORIZONTAL_RADIUS = 2;
    private static final int MIN_Y_OFFSET = -2;
    private static final int MAX_Y_OFFSET = 1;

    private static World previousLevel;
    private static int ticksUntilScan;
    private static BlockPos lastAttemptPosition;
    private static int lastAttemptAge;

    private NearbyAutoTorch() {
    }

    public static void tick(Minecraft minecraft) {
        if (minecraft.world != previousLevel) {
            previousLevel = minecraft.world;
            ticksUntilScan = 0;
            lastAttemptPosition = null;
            lastAttemptAge = RETRY_DELAY_TICKS;
        }
        if (lastAttemptPosition != null && lastAttemptAge < RETRY_DELAY_TICKS) {
            lastAttemptAge++;
        }
        if (!ClientConfig.isNearbyAutoTorchEnabled()
                || minecraft.world == null
                || minecraft.player == null
                || minecraft.playerController == null
                || minecraft.currentScreen != null
                || minecraft.player.isSpectator()
                || !minecraft.player.isAlive()) {
            return;
        }
        if (ticksUntilScan-- > 0) {
            return;
        }
        ticksUntilScan = SCAN_INTERVAL_TICKS - 1;

        TorchSource torch = findTorch(minecraft.player);
        if (torch == null) {
            return;
        }
        BlockPos target = findTarget(minecraft.world, minecraft.player);
        if (target != null) {
            place(minecraft, torch, target);
        }
    }

    private static BlockPos findTarget(World level, EntityPlayerSP player) {
        BlockPos origin = player.getPosition();
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        int threshold = ClientConfig.nearbyAutoTorchThreshold();

        for (int dy = MIN_Y_OFFSET; dy <= MAX_Y_OFFSET; dy++) {
            for (int dx = -HORIZONTAL_RADIUS; dx <= HORIZONTAL_RADIUS; dx++) {
                for (int dz = -HORIZONTAL_RADIUS; dz <= HORIZONTAL_RADIUS; dz++) {
                    BlockPos candidate = origin.add(dx, dy, dz);
                    if (!isValidTarget(level, player, candidate)
                            || measuredLight(level, candidate) >= threshold
                            || isWaitingToRetry(candidate)) {
                        continue;
                    }
                    double distance = player.getDistanceSq(centerOf(candidate));
                    if (distance < bestDistance) {
                        bestDistance = distance;
                        best = candidate.toImmutable();
                    }
                }
            }
        }
        return best;
    }

    private static boolean isValidTarget(World level, EntityPlayerSP player, BlockPos target) {
        if (!level.getBlockState(target).isAir(level, target) || !level.getFluidState(target).isEmpty()) {
            return false;
        }
        BlockPos floorPos = target.down();
        if (level.getBlockState(floorPos).getBlockFaceShape(level, floorPos, EnumFacing.UP) != BlockFaceShape.SOLID
                || player.getBoundingBox().intersects(new AxisAlignedBB(target))) {
            return false;
        }
        Vec3d hitLocation = centerOf(target.down()).add(0.0, 0.5, 0.0);
        return player.getEyePosition(1.0F).squareDistanceTo(hitLocation) <= 20.25;
    }

    private static int measuredLight(World level, BlockPos position) {
        int blockLight = level.getLightFor(EnumLightType.BLOCK, position);
        return ClientConfig.includesSkyLight()
                ? Math.max(blockLight, level.getLightFor(EnumLightType.SKY, position))
                : blockLight;
    }

    private static boolean isWaitingToRetry(BlockPos candidate) {
        return lastAttemptPosition != null
                && lastAttemptPosition.equals(candidate)
                && lastAttemptAge < RETRY_DELAY_TICKS;
    }

    private static TorchSource findTorch(EntityPlayerSP player) {
        if (player.getHeldItemOffhand().getItem() == Blocks.TORCH.asItem()) {
            return new TorchSource(EnumHand.OFF_HAND, -1);
        }
        int selected = player.inventory.currentItem;
        if (player.inventory.getStackInSlot(selected).getItem() == Blocks.TORCH.asItem()) {
            return new TorchSource(EnumHand.MAIN_HAND, selected);
        }
        for (int slot = 0; slot < 9; slot++) {
            if (player.inventory.getStackInSlot(slot).getItem() == Blocks.TORCH.asItem()) {
                return new TorchSource(EnumHand.MAIN_HAND, slot);
            }
        }
        return null;
    }

    private static void place(Minecraft minecraft, TorchSource torch, BlockPos target) {
        EntityPlayerSP player = minecraft.player;
        int previousSlot = player.inventory.currentItem;
        if (torch.hotbarSlot() >= 0) {
            player.inventory.currentItem = torch.hotbarSlot();
        }

        BlockPos support = target.down();
        EnumActionResult result = minecraft.playerController.processRightClickBlock(
                player, minecraft.world, support, EnumFacing.UP,
                centerOf(support).add(0.0, 0.5, 0.0), torch.hand());
        if (result == EnumActionResult.SUCCESS) {
            player.swingArm(torch.hand());
        }

        if (torch.hotbarSlot() >= 0) {
            player.inventory.currentItem = previousSlot;
        }
        lastAttemptPosition = target.toImmutable();
        lastAttemptAge = 0;
    }

    private static Vec3d centerOf(BlockPos pos) {
        return new Vec3d(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
    }

    private static final class TorchSource {
        private final EnumHand hand;
        private final int hotbarSlot;

        private TorchSource(EnumHand hand, int hotbarSlot) {
            this.hand = hand;
            this.hotbarSlot = hotbarSlot;
        }

        private EnumHand hand() { return hand; }
        private int hotbarSlot() { return hotbarSlot; }
    }
}
