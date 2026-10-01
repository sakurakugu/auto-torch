package com.sakurakugu.autotorch.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.World;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.init.Blocks;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.Vec3;

import java.util.HashMap;
import java.util.Map;

/** 通过普通客户端交互，在玩家附近的黑暗区域放置快捷栏中的火把。 */
public final class NearbyAutoTorch {
    /** 扫描间隔。 */
    static final int SCAN_INTERVAL_TICKS = 10;
    /** 黑暗确认窗口：候选位置需连续保持黑暗这么多 tick 才允许放置火把。 */
    static final int DARK_CONFIRM_TICKS = SCAN_INTERVAL_TICKS;
    private static final int RETRY_DELAY_TICKS = 40;
    private static final int HORIZONTAL_RADIUS = 2;
    private static final int MIN_Y_OFFSET = -2;
    private static final int MAX_Y_OFFSET = 1;

    private static World previousLevel;
    private static int ticksUntilScan;
    private static BlockPos lastAttemptPosition;
    private static int lastAttemptAge;
    private static long tickCount;
    private static final LightStability lightStability = new LightStability();

    private NearbyAutoTorch() {
    }

    public static void tick(Minecraft minecraft) {
        tickCount++;
        if (minecraft.theWorld != previousLevel) {
            previousLevel = minecraft.theWorld;
            ticksUntilScan = 0;
            lastAttemptPosition = null;
            lastAttemptAge = RETRY_DELAY_TICKS;
            lightStability.clear();
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
            lightStability.clear();
            return;
        }
        if (ticksUntilScan-- > 0) {
            return;
        }
        ticksUntilScan = SCAN_INTERVAL_TICKS - 1;

        TorchSource torch = findTorch(minecraft.thePlayer);
        if (torch == null) {
            lightStability.clear();
            return;
        }
        int threshold = ClientConfig.nearbyAutoTorchThreshold();
        lightStability.prune(tickCount, DARK_CONFIRM_TICKS);
        BlockPos target = findTarget(minecraft.theWorld, minecraft.thePlayer, threshold, DARK_CONFIRM_TICKS);
        if (target != null) {
            place(minecraft, torch, target);
        }
    }

    private static BlockPos findTarget(World level, EntityPlayerSP player, int threshold,
            int confirmTicks) {
        BlockPos origin = player.getPosition();
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;

        for (int dy = MIN_Y_OFFSET; dy <= MAX_Y_OFFSET; dy++) {
            for (int dx = -HORIZONTAL_RADIUS; dx <= HORIZONTAL_RADIUS; dx++) {
                for (int dz = -HORIZONTAL_RADIUS; dz <= HORIZONTAL_RADIUS; dz++) {
                    BlockPos candidate = origin.add(dx, dy, dz);
                    if (!isValidTarget(level, player, candidate)
                            || isWaitingToRetry(candidate)) {
                        // 这一轮没观测到该坐标，不能把这段时间也算成黑暗。
                        lightStability.forget(candidate);
                        continue;
                    }
                    int light = measuredLight(level, candidate);
                    if (!lightStability.confirm(candidate, light, threshold, tickCount, confirmTicks)) {
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
        if (!level.isAirBlock(target)
                || level.getBlockState(target).getBlock().getMaterial().isLiquid()) {
            return false;
        }
        BlockPos floorPos = target.down();
        if (!level.getBlockState(floorPos).getBlock().isSideSolid(level, floorPos, EnumFacing.UP)
                || player.getEntityBoundingBox().intersectsWith(
                        new AxisAlignedBB(target, target.add(1, 1, 1)))) {
            return false;
        }
        Vec3 hitLocation = centerOf(target.down()).addVector(0.0, 0.5, 0.0);
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
                player, minecraft.theWorld, player.getHeldItem(), support, EnumFacing.UP,
                centerOf(support).addVector(0.0, 0.5, 0.0));
        if (placed) {
            player.swingItem();
        }

        if (torch.hotbarSlot() >= 0) {
            player.inventory.currentItem = previousSlot;
        }
        lastAttemptPosition = target.getImmutable();
        lastAttemptAge = 0;
        lightStability.clear();
    }

    /**
     * 记录候选点开始变暗的 tick，要求黑暗持续达到确认窗口后才允许放置，用于过滤刚挖掉方块时
     * 客户端亮度更新延迟造成的“瞬间全黑”。窗口只在扫描时采样，所以实际精度是一个扫描间隔。
     */
    static final class LightStability {
        private final Map<BlockPos, Long> darkSince = new HashMap<>();

        /** 清理很久没有被重新观测的残留记录，避免扫描框移走后记录无限增长。 */
        void prune(long now, int confirmTicks) {
            darkSince.entrySet().removeIf(entry -> now - entry.getValue() > confirmTicks + SCAN_INTERVAL_TICKS);
        }

        /** 未被观测到的坐标必须显式遗忘，否则没看它的那几轮也会被算成黑暗。 */
        void forget(BlockPos position) {
            darkSince.remove(position);
        }

        /** 返回 true 表示该坐标已经连续黑够了配置的 tick 数，可以放置。 */
        boolean confirm(BlockPos position, int light, int threshold, long now, int confirmTicks) {
            BlockPos immutablePosition = position.getImmutable();
            if (light >= threshold) {
                darkSince.remove(immutablePosition);
                return false;
            }
            Long since = darkSince.putIfAbsent(immutablePosition, now);
            return since != null && now - since >= confirmTicks;
        }

        void clear() {
            darkSince.clear();
        }
    }

    private static Vec3 centerOf(BlockPos pos) {
        return new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
    }

    private static final class TorchSource {
        private final int hotbarSlot;

        private TorchSource(int hotbarSlot) {
            this.hotbarSlot = hotbarSlot;
        }

        private int hotbarSlot() { return hotbarSlot; }
    }
}
