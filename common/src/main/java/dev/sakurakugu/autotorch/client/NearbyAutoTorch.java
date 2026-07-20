package dev.sakurakugu.autotorch.client;

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

/** 通过普通客户端交互，在玩家附近的黑暗区域放置快捷栏中的火把。 */
public final class NearbyAutoTorch {
    private static final int SCAN_INTERVAL_TICKS = 10;
    private static final int RETRY_DELAY_TICKS = 40;
    private static final int HORIZONTAL_RADIUS = 2;
    private static final int MIN_Y_OFFSET = -2;
    private static final int MAX_Y_OFFSET = 1;

    private static ClientLevel previousLevel;
    private static int ticksUntilScan;
    private static BlockPos lastAttemptPosition;
    private static int lastAttemptAge;

    private NearbyAutoTorch() {
    }

    public static void tick(Minecraft minecraft) {
        if (minecraft.level != previousLevel) {
            previousLevel = minecraft.level;
            ticksUntilScan = 0;
            lastAttemptPosition = null;
            lastAttemptAge = RETRY_DELAY_TICKS;
        }
        if (lastAttemptPosition != null && lastAttemptAge < RETRY_DELAY_TICKS) {
            lastAttemptAge++;
        }
        if (!ClientConfig.isNearbyAutoTorchEnabled()
                || minecraft.level == null
                || minecraft.player == null
                || minecraft.gameMode == null
                || minecraft.screen != null
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
        BlockPos target = findTarget(minecraft.level, minecraft.player);
        if (target != null) {
            place(minecraft, torch, target);
        }
    }

    private static BlockPos findTarget(ClientLevel level, LocalPlayer player) {
        BlockPos origin = player.blockPosition();
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        int threshold = ClientConfig.nearbyAutoTorchThreshold();

        for (int dy = MIN_Y_OFFSET; dy <= MAX_Y_OFFSET; dy++) {
            for (int dx = -HORIZONTAL_RADIUS; dx <= HORIZONTAL_RADIUS; dx++) {
                for (int dz = -HORIZONTAL_RADIUS; dz <= HORIZONTAL_RADIUS; dz++) {
                    BlockPos candidate = origin.offset(dx, dy, dz);
                    if (!isValidTarget(level, player, candidate)
                            || measuredLight(level, candidate) >= threshold
                            || isWaitingToRetry(candidate)) {
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
    }

    private record TorchSource(InteractionHand hand, int hotbarSlot) {
    }
}
