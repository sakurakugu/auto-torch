package com.sakurakugu.autotorch.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.World;
import net.minecraft.client.entity.EntityPlayerSP;
import com.sakurakugu.autotorch.compat.BlockPos;
import com.sakurakugu.autotorch.compat.WorldAccess;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.Vec3;

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
        BlockPos origin = new BlockPos(player);
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
                    Vec3 center = centerOf(candidate);
                    double distance = player.getDistanceSq(center.xCoord, center.yCoord, center.zCoord);
                    if (distance < bestDistance) {
                        bestDistance = distance;
                        best = candidate.getImmutable();
                    }
                }
            }
        }
        return best;
    }

    private static boolean isValidTarget(World level, EntityPlayerSP player, BlockPos target) {
        if (!WorldAccess.isAir(level, target)
                || WorldAccess.block(level, target).getMaterial().isLiquid()) {
            return false;
        }
        BlockPos floorPos = target.down();
        if (!WorldAccess.isTopSolid(level, floorPos)
                || player.boundingBox.intersectsWith(AxisAlignedBB.getBoundingBox(
                        target.getX(), target.getY(), target.getZ(),
                        target.getX() + 1, target.getY() + 1, target.getZ() + 1))) {
            return false;
        }
        Vec3 hitLocation = centerOf(target.down()).addVector(0.0, 0.5, 0.0);
        Vec3 eyes = Vec3.createVectorHelper(player.posX, player.posY + player.getEyeHeight(), player.posZ);
        return eyes.squareDistanceTo(hitLocation) <= 20.25;
    }

    private static int measuredLight(World level, BlockPos position) {
        int blockLight = WorldAccess.blockLight(level, position);
        return ClientConfig.includesSkyLight()
                ? Math.max(blockLight, WorldAccess.skyLight(level, position))
                : blockLight;
    }

    private static boolean isWaitingToRetry(BlockPos candidate) {
        return lastAttemptPosition != null
                && lastAttemptPosition.equals(candidate)
                && lastAttemptAge < RETRY_DELAY_TICKS;
    }

    private static TorchSource findTorch(EntityPlayerSP player) {
        int selected = player.inventory.currentItem;
        if (isTorch(player.inventory.getStackInSlot(selected))) {
            return new TorchSource(selected);
        }
        for (int slot = 0; slot < 9; slot++) {
            if (isTorch(player.inventory.getStackInSlot(slot))) {
                return new TorchSource(slot);
            }
        }
        return null;
    }

    private static boolean isTorch(ItemStack stack) {
        return stack != null && stack.getItem() == Item.getItemFromBlock(Blocks.torch);
    }

    private static void place(Minecraft minecraft, TorchSource torch, BlockPos target) {
        EntityPlayerSP player = minecraft.thePlayer;
        int previousSlot = player.inventory.currentItem;
        if (torch.hotbarSlot() >= 0) {
            player.inventory.currentItem = torch.hotbarSlot();
        }

        BlockPos support = target.down();
        boolean placed = minecraft.playerController.onPlayerRightClick(
                player, minecraft.theWorld, player.getHeldItem(),
                support.getX(), support.getY(), support.getZ(), 1,
                centerOf(support).addVector(0.0, 0.5, 0.0));
        if (placed) {
            player.swingItem();
        }

        if (torch.hotbarSlot() >= 0) {
            player.inventory.currentItem = previousSlot;
        }
        lastAttemptPosition = target.getImmutable();
        lastAttemptAge = 0;
    }

    private static Vec3 centerOf(BlockPos pos) {
        return Vec3.createVectorHelper(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
    }

    private static final class TorchSource {
        private final int hotbarSlot;

        private TorchSource(int hotbarSlot) {
            this.hotbarSlot = hotbarSlot;
        }

        private int hotbarSlot() { return hotbarSlot; }
    }
}
