package com.sakurakugu.autotorch.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
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
        ClientConfigSnapshot config = ClientConfig.snapshot();
        tickCount++;
        if (minecraft.level != previousLevel) {
            previousLevel = minecraft.level;
            ticksUntilScan = 0;
            lastAttemptPosition = null;
            lastAttemptAge = config.nearbyAutoTorchRetryDelay;
            lightStability.clear();
        }
        if (lastAttemptPosition != null && lastAttemptAge < config.nearbyAutoTorchRetryDelay) {
            lastAttemptAge++;
        }
        if (!config.nearbyAutoTorchEnabled
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
        int scanInterval = config.nearbyAutoTorchScanInterval;
        ticksUntilScan = scanInterval - 1;

        net.minecraft.world.item.Item placementItem = ClientConfig.nearbyPlacementItem(config);
        TorchSource torch = findTorch(placementItem, minecraft.player);
        if (torch == null) {
            lightStability.clear();
            return;
        }
        int threshold = config.nearbyAutoTorchLightThreshold;
        int confirmTicks = config.nearbyAutoTorchDarkConfirm;
        lightStability.prune(tickCount, confirmTicks, scanInterval);
        BlockPos target = findTarget(config, placementItem, minecraft.level, minecraft.player, threshold, confirmTicks);
        if (target != null) {
            place(minecraft, torch, target);
        }
    }

    private static BlockPos findTarget(ClientConfigSnapshot config, net.minecraft.world.item.Item placementItem, ClientLevel level, LocalPlayer player, int threshold,
            int confirmTicks) {
        BlockPos origin = player.blockPosition();
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;

        int horizontalRadius = config.nearbyAutoTorchHorizontalRadius;
        for (int dy = MIN_Y_OFFSET; dy <= MAX_Y_OFFSET; dy++) {
            for (int dx = -horizontalRadius; dx <= horizontalRadius; dx++) {
                for (int dz = -horizontalRadius; dz <= horizontalRadius; dz++) {
                    BlockPos candidate = origin.offset(dx, dy, dz);
                    if (!isValidTarget(placementItem, level, player, candidate)
                            || isWaitingToRetry(config, candidate)) {
                        // 这一轮没观测到该坐标，不能把这段时间也算成黑暗。
                        lightStability.forget(candidate);
                        continue;
                    }
                    int light = measuredLight(config, level, candidate);
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

    private static boolean isValidTarget(net.minecraft.world.item.Item placementItem, ClientLevel level, LocalPlayer player, BlockPos target) {
        if (!level.getBlockState(target).isAir() || !level.getFluidState(target).isEmpty()) {
            return false;
        }
        net.minecraft.world.item.Item item = placementItem;
        if (!(item instanceof net.minecraft.world.item.BlockItem blockItem)
                || !blockItem.getBlock().defaultBlockState().canSurvive(level, target)
                || player.getBoundingBox().intersects(new AABB(target))) {
            return false;
        }
        Vec3 hitLocation = Vec3.atCenterOf(target.below()).add(0.0, 0.5, 0.0);
        return player.getEyePosition().distanceToSqr(hitLocation) <= 20.25;
    }

    private static int measuredLight(ClientConfigSnapshot config, ClientLevel level, BlockPos position) {
        int blockLight = level.getBrightness(LightLayer.BLOCK, position);
        return config.nearbyAutoTorchIncludeSkyLight
                ? Math.max(blockLight, level.getBrightness(LightLayer.SKY, position))
                : blockLight;
    }

    private static boolean isWaitingToRetry(ClientConfigSnapshot config, BlockPos candidate) {
        return lastAttemptPosition != null
                && lastAttemptPosition.equals(candidate)
                && lastAttemptAge < config.nearbyAutoTorchRetryDelay;
    }

    private static TorchSource findTorch(net.minecraft.world.item.Item placementItem, LocalPlayer player) {
        net.minecraft.world.item.Item item = placementItem;
        if (player.getOffhandItem().is(item)) {
            return new TorchSource(InteractionHand.OFF_HAND, -1);
        }
        int selected = player.getInventory().getSelectedSlot();
        if (player.getInventory().getItem(selected).is(item)) {
            return new TorchSource(InteractionHand.MAIN_HAND, selected);
        }
        for (int slot = 0; slot < 9; slot++) {
            if (player.getInventory().getItem(slot).is(item)) {
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
        void prune(long now, int confirmTicks, int scanInterval) {
            darkSince.entrySet().removeIf(entry -> now - entry.getValue() > confirmTicks + scanInterval);
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
