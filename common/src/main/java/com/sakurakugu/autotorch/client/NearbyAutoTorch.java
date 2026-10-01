package com.sakurakugu.autotorch.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

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

    private static ClientLevel previousLevel;
    private static int ticksUntilScan;
    private static BlockPos lastAttemptPosition;
    private static int lastAttemptAge;
    private static long tickCount;
    private static final LightStability lightStability = new LightStability();

    private NearbyAutoTorch() {
    }

    public static void tick(Minecraft minecraft) {
        tickCount++;
        if (minecraft.level != previousLevel) {
            previousLevel = minecraft.level;
            ticksUntilScan = 0;
            lastAttemptPosition = null;
            lastAttemptAge = RETRY_DELAY_TICKS;
            lightStability.clear();
        }
        if (lastAttemptPosition != null && lastAttemptAge < RETRY_DELAY_TICKS) {
            lastAttemptAge++;
        }
        if (!ClientConfig.isNearbyAutoTorchEnabled()
                || minecraft.level == null
                || minecraft.player == null
                || minecraft.gameMode == null
                || minecraft.gui.screen() != null
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
        BlockPos target = findTarget(minecraft.level, minecraft.player, threshold, DARK_CONFIRM_TICKS);
        if (target != null) {
            place(minecraft, torch, target);
        }
    }

    private static BlockPos findTarget(ClientLevel level, LocalPlayer player, int threshold,
            int confirmTicks) {
        BlockPos origin = player.blockPosition();
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;

        for (int dy = MIN_Y_OFFSET; dy <= MAX_Y_OFFSET; dy++) {
            for (int dx = -HORIZONTAL_RADIUS; dx <= HORIZONTAL_RADIUS; dx++) {
                for (int dz = -HORIZONTAL_RADIUS; dz <= HORIZONTAL_RADIUS; dz++) {
                    BlockPos candidate = origin.offset(dx, dy, dz);
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
                    double distance = player.distanceToSqr(Vec3.atCenterOf(candidate));
                    if (distance < bestDistance) {
                        bestDistance = distance;
                        best = candidate.immutable();
                    }
                }
            }
        }
        return best;
    }

    private static boolean isValidTarget(ClientLevel level, LocalPlayer player, BlockPos target) {
        if (!level.getBlockState(target).isAir() || !level.getFluidState(target).isEmpty()) {
            return false;
        }
        if (!Blocks.TORCH.defaultBlockState().canSurvive(level, target)
                || player.getBoundingBox().intersects(new AABB(target))) {
            return false;
        }
        Vec3 hitLocation = Vec3.atCenterOf(target.below()).add(0.0, 0.5, 0.0);
        return player.getEyePosition().distanceToSqr(hitLocation) <= 20.25;
    }

    private static int measuredLight(ClientLevel level, BlockPos position) {
        int blockLight = level.getBrightness(LightLayer.BLOCK, position);
        return ClientConfig.includesSkyLight()
                ? Math.max(blockLight, level.getBrightness(LightLayer.SKY, position))
                : blockLight;
    }

    private static boolean isWaitingToRetry(BlockPos candidate) {
        return lastAttemptPosition != null
                && lastAttemptPosition.equals(candidate)
                && lastAttemptAge < RETRY_DELAY_TICKS;
    }

    private static TorchSource findTorch(LocalPlayer player) {
        if (player.getOffhandItem().is(Items.TORCH)) {
            return new TorchSource(InteractionHand.OFF_HAND, -1);
        }
        int selected = player.getInventory().getSelectedSlot();
        if (player.getInventory().getItem(selected).is(Items.TORCH)) {
            return new TorchSource(InteractionHand.MAIN_HAND, selected);
        }
        for (int slot = 0; slot < 9; slot++) {
            if (player.getInventory().getItem(slot).is(Items.TORCH)) {
                return new TorchSource(InteractionHand.MAIN_HAND, slot);
            }
        }
        return null;
    }

    private static void place(Minecraft minecraft, TorchSource torch, BlockPos target) {
        LocalPlayer player = minecraft.player;
        int previousSlot = player.getInventory().getSelectedSlot();
        if (torch.hotbarSlot() >= 0) {
            player.getInventory().setSelectedSlot(torch.hotbarSlot());
        }

        BlockPos support = target.below();
        BlockHitResult hit = new BlockHitResult(
                Vec3.atCenterOf(support).add(0.0, 0.5, 0.0), Direction.UP, support, false);
        InteractionResult result = minecraft.gameMode.useItemOn(player, torch.hand(), hit);
        if (result instanceof InteractionResult.Success success
                && success.swingSource() == InteractionResult.SwingSource.CLIENT) {
            player.swing(torch.hand());
        }

        if (torch.hotbarSlot() >= 0) {
            player.getInventory().setSelectedSlot(previousSlot);
        }
        lastAttemptPosition = target.immutable();
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
            BlockPos immutablePosition = position.immutable();
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

    private record TorchSource(InteractionHand hand, int hotbarSlot) {
    }
}
