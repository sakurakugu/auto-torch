package com.sakurakugu.autotorch.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.World;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.EnumActionResult;
import net.minecraft.init.Blocks;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
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
        if (minecraft.theWorld != previousLevel) {
            previousLevel = minecraft.theWorld;
            ticksUntilScan = 0;
            lastAttemptPosition = null;
            lastAttemptAge = RETRY_DELAY_TICKS;
        }
        if (lastAttemptPosition != null && lastAttemptAge < RETRY_DELAY_TICKS) {
            lastAttemptAge++;
        }
        if (!ClientConfig.isNearbyAutoTorchEnabled()
                || minecraft.theWorld == null
                || minecraft.thePlayer == null
                || minecraft.playerController == null
                || minecraft.currentScreen != null
                || minecraft.thePlayer.isSpectator()
                || !minecraft.thePlayer.isEntityAlive()) {
            return;
        }
        if (ticksUntilScan-- > 0) {
            return;
        }
        ticksUntilScan = SCAN_INTERVAL_TICKS - 1;

        TorchSource torch = findTorch(minecraft.thePlayer);
        if (torch == null) {
            return;
        }
        BlockPos target = findTarget(minecraft.theWorld, minecraft.thePlayer);
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
                    Vec3d center = centerOf(candidate);
                    double distance = player.getDistanceSq(center.xCoord, center.yCoord, center.zCoord);
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
        if (!level.isAirBlock(target) || level.getBlockState(target).getMaterial().isLiquid()) {
            return false;
        }
        BlockPos floorPos = target.down();
        if (!level.getBlockState(floorPos).isSideSolid(level, floorPos, EnumFacing.UP)
                || player.getEntityBoundingBox().intersectsWith(new AxisAlignedBB(target))) {
            return false;
        }
        Vec3d hitLocation = centerOf(target.down()).addVector(0.0, 0.5, 0.0);
        return player.getPositionEyes(1.0F).squareDistanceTo(hitLocation) <= 20.25;
    }

    private static int measuredLight(World level, BlockPos position) {
        int blockLight = level.getLightFor(EnumSkyBlock.BLOCK, position);
        return ClientConfig.includesSkyLight()
                ? Math.max(blockLight, level.getLightFor(EnumSkyBlock.SKY, position))
                : blockLight;
    }

    private static boolean isWaitingToRetry(BlockPos candidate) {
        return lastAttemptPosition != null
                && lastAttemptPosition.equals(candidate)
                && lastAttemptAge < RETRY_DELAY_TICKS;
    }

    private static TorchSource findTorch(EntityPlayerSP player) {
        if (isTorch(player.getHeldItemOffhand())) {
            return new TorchSource(EnumHand.OFF_HAND, -1);
        }
        int selected = player.inventory.currentItem;
        if (isTorch(player.inventory.getStackInSlot(selected))) {
            return new TorchSource(EnumHand.MAIN_HAND, selected);
        }
        for (int slot = 0; slot < 9; slot++) {
            if (isTorch(player.inventory.getStackInSlot(slot))) {
                return new TorchSource(EnumHand.MAIN_HAND, slot);
            }
        }
        return null;
    }

    private static boolean isTorch(ItemStack stack) {
        return stack != null && stack.getItem() == Item.getItemFromBlock(Blocks.TORCH);
    }

    private static void place(Minecraft minecraft, TorchSource torch, BlockPos target) {
        EntityPlayerSP player = minecraft.thePlayer;
        int previousSlot = player.inventory.currentItem;
        if (torch.hotbarSlot() >= 0) {
            player.inventory.currentItem = torch.hotbarSlot();
        }

        BlockPos support = target.down();
        EnumActionResult result = minecraft.playerController.processRightClickBlock(
                player, minecraft.theWorld, player.getHeldItem(torch.hand()), support, EnumFacing.UP,
                centerOf(support).addVector(0.0, 0.5, 0.0), torch.hand());
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
