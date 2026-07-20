package dev.sakurakugu.autotorch.server;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import dev.sakurakugu.autotorch.network.AreaZone;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** 单个玩家的增量照明任务，通过每刻预算避免大选区阻塞服务端线程。 */
final class LightingTask {

    private final ServerLevel level;
    private final AreaZone selection;
    private final BlockPos min;
    private final int sizeX;
    private final int sizeY;
    private final int sizeZ;
    private final long volume;
    private final long permutationStart;
    private final long permutationStep;
    private final int maxTorches;
    private final int configuredSpacing;
    private final boolean consumeTorches;
    private final boolean undergroundOnly;
    private final List<AreaZone> exclusions;
    private final Random random;
    private final Map<Long, List<BlockPos>> placedByCell = new HashMap<>();

    // scanIndex 在当前扫描轮次中的位置；第二轮会缩短间距以补齐遗漏暗区。
    private long scanIndex;
    private int pass;
    private int placed;
    private int skippedUnloaded;

    LightingTask(
            ServerLevel level,
            AreaZone selection,
            int maxTorches,
            int configuredSpacing,
            boolean consumeTorches,
            boolean undergroundOnly,
            List<AreaZone> exclusions,
            UUID playerId
    ) {
        this.level = level;
        this.selection = selection;
        this.min = selection.min();
        BlockPos max = selection.max();
        this.sizeX = max.getX() - min.getX() + 1;
        this.sizeY = max.getY() - min.getY() + 1;
        this.sizeZ = max.getZ() - min.getZ() + 1;
        this.volume = (long) sizeX * sizeY * sizeZ;
        this.maxTorches = maxTorches;
        this.configuredSpacing = configuredSpacing;
        this.consumeTorches = consumeTorches;
        this.undergroundOnly = undergroundOnly;
        this.exclusions = List.copyOf(exclusions);

        // 使用稳定种子生成伪随机遍历，使结果可复现，同时避免总从选区同一角开始。
        long seed = level.getSeed() ^ playerId.getMostSignificantBits() ^ playerId.getLeastSignificantBits()
                ^ min.asLong() ^ Long.rotateLeft(max.asLong(), 23);
        this.random = new Random(seed);
        this.permutationStart = Math.floorMod(random.nextLong(), volume);
        this.permutationStep = chooseCoprimeStep(volume, random);
    }

    boolean tick(ServerPlayer player) {
        if (player.level() != level) {
            player.sendSystemMessage(Component.translatable("message.autotorch.wrong_dimension"));
            return true;
        }
        if (maxTorches > 0 && placed >= maxTorches) {
            player.sendSystemMessage(Component.translatable("message.autotorch.max_reached", placed));
            return true;
        }

        int scannedThisTick = 0;
        int placedThisTick = 0;
        // 扫描量和放置量分别限流，兼顾无效位置扫描与方块更新的两类开销。
        while (scannedThisTick < ServerConfig.scanBudgetPerTaskTick()
                && placedThisTick < ServerConfig.placeBudgetPerTaskTick()) {
            if (scanIndex >= volume) {
                if (pass == 0) {
                    // 第一轮按配置间距铺设，第二轮以更小间距填补仍然无光的位置。
                    pass = 1;
                    scanIndex = 0;
                    continue;
                }
                player.sendSystemMessage(Component.translatable("message.autotorch.completed", placed, skippedUnloaded));
                return true;
            }

            BlockPos feet = positionAt(scanIndex++);
            scannedThisTick++;
            if (!isChunkLoaded(feet)) {
                skippedUnloaded++;
                continue;
            }
            if (!isPotentialSpawnPosition(feet)) {
                continue;
            }

            BlockPos torchPos = findTorchPosition(player, feet);
            if (torchPos == null || !farEnoughFromPlaced(torchPos, currentSpacing())) {
                continue;
            }
            if (consumeTorches && !player.isCreative() && !hasTorch(player)) {
                player.sendSystemMessage(Component.translatable("message.autotorch.out_of_torches", placed));
                return true;
            }
            if (!level.setBlock(torchPos, Blocks.TORCH.defaultBlockState(), Block.UPDATE_ALL)) {
                continue;
            }
            if (consumeTorches && !player.isCreative()) {
                consumeTorch(player);
            }

            rememberPlaced(torchPos);
            placed++;
            placedThisTick++;
            if (maxTorches > 0 && placed >= maxTorches) {
                player.sendSystemMessage(Component.translatable("message.autotorch.max_reached", placed));
                return true;
            }
        }
        return false;
    }

    private BlockPos positionAt(long index) {
        // 与总体积互质的步长会恰好访问每个位置一次，且不会额外保存打乱后的坐标表。
        long linear = (permutationStart + index * permutationStep) % volume;
        int x = (int) (linear % sizeX);
        linear /= sizeX;
        int z = (int) (linear % sizeZ);
        int y = (int) (linear / sizeZ);
        return min.offset(x, y, z);
    }

    private boolean isPotentialSpawnPosition(BlockPos feet) {
        if (isExcluded(feet) || !level.getBlockState(feet).isAir() || !level.getBlockState(feet.above()).isAir()) {
            return false;
        }
        if (!level.getFluidState(feet).isEmpty() || level.getBrightness(LightLayer.BLOCK, feet) > 0) {
            return false;
        }
        if (undergroundOnly && level.getBrightness(LightLayer.SKY, feet) > 0) {
            return false;
        }

        BlockPos floorPos = feet.below();
        BlockState floor = level.getBlockState(floorPos);
        return Block.isFaceFull(floor.getCollisionShape(level, floorPos), Direction.UP);
    }

    private BlockPos findTorchPosition(ServerPlayer player, BlockPos darkPosition) {
        // 优先尝试暗点脚下；失败后在附近随机寻找可放置且玩家有权限的位置。
        for (int attempt = 0; attempt < ServerConfig.randomPlacementAttempts(); attempt++) {
            int radius = attempt == 0 ? 0 : 4;
            int dx = radius == 0 ? 0 : random.nextInt(radius * 2 + 1) - radius;
            int dz = radius == 0 ? 0 : random.nextInt(radius * 2 + 1) - radius;
            int dy = radius == 0 ? 0 : random.nextInt(5) - 2;
            BlockPos candidate = darkPosition.offset(dx, dy, dz);

            if (!insideSelection(candidate) || isExcluded(candidate) || !isChunkLoaded(candidate)) {
                continue;
            }
            if (!level.getBlockState(candidate).isAir() || !level.getFluidState(candidate).isEmpty()) {
                continue;
            }

            BlockState torch = Blocks.TORCH.defaultBlockState();
            if (torch.canSurvive(level, candidate) && player.mayInteract(level, candidate)) {
                return candidate.immutable();
            }
        }
        return null;
    }

    private boolean insideSelection(BlockPos pos) {
        return selection.contains(pos);
    }

    private boolean isChunkLoaded(BlockPos pos) {
        return level.hasChunk(SectionPos.blockToSectionCoord(pos.getX()), SectionPos.blockToSectionCoord(pos.getZ()));
    }

    private boolean isExcluded(BlockPos pos) {
        return exclusions.stream().anyMatch(exclusion -> exclusion.contains(pos));
    }

    private int currentSpacing() {
        return pass == 0 ? configuredSpacing : Math.max(3, configuredSpacing / 2);
    }

    private boolean farEnoughFromPlaced(BlockPos pos, int spacing) {
        // 用配置间距划分空间桶，只需检查相邻 27 个桶即可判断最近距离。
        int cellX = Math.floorDiv(pos.getX(), configuredSpacing);
        int cellY = Math.floorDiv(pos.getY(), configuredSpacing);
        int cellZ = Math.floorDiv(pos.getZ(), configuredSpacing);
        long minimumSquared = (long) spacing * spacing;

        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    List<BlockPos> nearby = placedByCell.get(BlockPos.asLong(cellX + dx, cellY + dy, cellZ + dz));
                    if (nearby == null) {
                        continue;
                    }
                    for (BlockPos other : nearby) {
                        if (other.distSqr(pos) < minimumSquared) {
                            return false;
                        }
                    }
                }
            }
        }
        return true;
    }

    private void rememberPlaced(BlockPos pos) {
        int cellX = Math.floorDiv(pos.getX(), configuredSpacing);
        int cellY = Math.floorDiv(pos.getY(), configuredSpacing);
        int cellZ = Math.floorDiv(pos.getZ(), configuredSpacing);
        placedByCell.computeIfAbsent(BlockPos.asLong(cellX, cellY, cellZ), ignored -> new ArrayList<>()).add(pos);
    }

    private static boolean hasTorch(ServerPlayer player) {
        return player.getInventory().contains(stack -> stack.is(Items.TORCH));
    }

    private static void consumeTorch(ServerPlayer player) {
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.is(Items.TORCH)) {
                stack.shrink(1);
                player.getInventory().setChanged();
                return;
            }
        }
    }

    private static long chooseCoprimeStep(long modulus, Random random) {
        if (modulus == 1) {
            return 1;
        }
        long step = 1 + Math.floorMod(random.nextLong(), modulus - 1);
        while (greatestCommonDivisor(step, modulus) != 1) {
            step++;
            if (step >= modulus) {
                step = 1;
            }
        }
        return step;
    }

    private static long greatestCommonDivisor(long a, long b) {
        while (b != 0) {
            long remainder = a % b;
            a = b;
            b = remainder;
        }
        return a;
    }
}
