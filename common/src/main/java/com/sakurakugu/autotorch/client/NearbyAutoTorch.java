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
        if (minecraft.world != previousLevel) {
            previousLevel = minecraft.world;
            ticksUntilScan = 0;
            lastAttemptPosition = null;
            lastAttemptAge = RETRY_DELAY_TICKS;
            lightStability.clear();
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
            lightStability.clear();
            return;
        }
        if (ticksUntilScan-- > 0) {
            return;
        }
        ticksUntilScan = SCAN_INTERVAL_TICKS - 1;

        TorchSource torch = findTorch(minecraft.player);
        if (torch == null) {
            lightStability.clear();
            return;
        }
        int threshold = ClientConfig.nearbyAutoTorchThreshold();
        lightStability.prune(tickCount, DARK_CONFIRM_TICKS);
        BlockPos target = findTarget(minecraft.world, minecraft.player, threshold, DARK_CONFIRM_TICKS);
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
            BlockPos immutablePosition = position.toImmutable();
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
