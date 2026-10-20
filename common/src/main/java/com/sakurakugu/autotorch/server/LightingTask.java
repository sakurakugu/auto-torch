package com.sakurakugu.autotorch.server;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.stream.Collectors;

import com.sakurakugu.autotorch.AutoTorchRules;
import com.sakurakugu.autotorch.network.AreaZone;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.WorldServer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.util.text.Style;
import net.minecraft.block.state.BlockFaceShape;
import net.minecraft.block.state.IBlockState;
// 1.12.2 的 ForgeRegistries 位于 net.minecraftforge.fml.common.registry 包。
import net.minecraftforge.fml.common.registry.ForgeRegistries;

/** 单个玩家的增量照明任务，通过每刻预算避免大选区阻塞服务端线程。 */
final class LightingTask {
    private static final int PROGRESS_BAR_LENGTH = 20;
    private static final int UPDATE_ALL = 3;

    private final WorldServer level;
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
    private final int secondPassMinimumSpacing; // 第二轮补点的间距下限
    private final int lightThreshold;
    private final boolean consumeTorches;
    private final boolean undergroundOnly;
    private final net.minecraft.item.ItemBlock placementItem;
    private final int progressUpdateIntervalTicks;
    private final AreaZoneIndex exclusions;
    private final Random random;
    private final Map<Long, List<BlockPos>> placedByCell = new HashMap<>();

    // scanIndex 在当前扫描轮次中的位置；第二轮会缩短间距以补齐遗漏暗区。
    private long scanIndex;
    private int pass;
    private int placed;
    private int skippedUnloaded;
    private int ticksUntilProgressUpdate;

    LightingTask(
            WorldServer level,
            AreaZone selection,
            BlockPos scanMin,
            BlockPos scanMax,
            int maxTorches,
            int configuredSpacing,
            int secondPassMinimumSpacing,
            int lightThreshold,
            boolean consumeTorches,
            boolean undergroundOnly,
            net.minecraft.item.ItemBlock placementItem,
            int progressUpdateIntervalTicks,
            List<AreaZone> exclusions,
            UUID playerId
    ) {
        this.level = level;
        this.selection = selection;
        this.min = scanMin.toImmutable();
        this.sizeX = scanMax.getX() - min.getX() + 1;
        this.sizeY = scanMax.getY() - min.getY() + 1;
        this.sizeZ = scanMax.getZ() - min.getZ() + 1;
        this.volume = (long) sizeX * sizeY * sizeZ;
        this.maxTorches = maxTorches;
        this.configuredSpacing = configuredSpacing;
        this.secondPassMinimumSpacing = secondPassMinimumSpacing;
        this.lightThreshold = lightThreshold;
        this.consumeTorches = consumeTorches;
        this.undergroundOnly = undergroundOnly;
        this.placementItem = placementItem;
        this.progressUpdateIntervalTicks = progressUpdateIntervalTicks;
        this.ticksUntilProgressUpdate = progressUpdateIntervalTicks;
        this.exclusions = new AreaZoneIndex(exclusions.stream()
                .filter(selection::intersects).collect(Collectors.toList()));

        // 使用稳定种子生成伪随机遍历，使结果可复现，同时避免总从选区同一角开始。
        long seed = level.getSeed() ^ playerId.getMostSignificantBits() ^ playerId.getLeastSignificantBits()
                ^ min.toLong() ^ Long.rotateLeft(scanMax.toLong(), 23);
        this.random = new Random(seed);
        this.permutationStart = Math.floorMod(random.nextLong(), volume);
        this.permutationStep = chooseCoprimeStep(volume, random);
    }

    TickResult tick(EntityPlayerMP player, int scanBudget, int placeBudget) {
        if (player.getServerWorld() != level) {
            return finish(player, "message.autotorch.wrong_dimension", 0, 0);
        }
        if (maxTorches > 0 && placed >= maxTorches) {
            return finish(player, "message.autotorch.max_reached", 0, 0, placed, placementName());
        }

        int scannedThisTick = 0;
        int placedThisTick = 0;
        // 扫描量和放置量分别限流，兼顾无效位置扫描与方块更新的两类开销。
        while (scannedThisTick < scanBudget && placedThisTick < placeBudget) {
            if (scanIndex >= volume) {
                if (pass == 0) {
                    // 第一轮按配置间距铺设，第二轮以更小间距填补仍然无光的位置。
                    pass = 1;
                    scanIndex = 0;
                    continue;
                }
                return finish(player, "message.autotorch.completed", scannedThisTick, placedThisTick,
                        placed, placementName(), skippedUnloaded);
            }

            BlockPos feet = positionAt(scanIndex++);
            scannedThisTick++;
            if (!insideSelection(feet)) {
                continue;
            }
            if (!isChunkLoaded(feet)) {
                skippedUnloaded++;
                continue;
            }
            if (!isPotentialSpawnPosition(feet)) {
                continue;
            }

            BlockPos torchPos = findTorchPosition(player, feet);
            if (torchPos == null || !farEnoughFromPlaced(torchPos)) {
                continue;
            }
            if (consumeTorches && !hasTorch(player)) {
                return finish(player, "message.autotorch.out_of_torches", scannedThisTick, placedThisTick,
                        placementName(), placed);
            }
            if (!level.setBlockState(torchPos, placementItem.getBlock().getDefaultState(), UPDATE_ALL)) {
                continue;
            }
            if (consumeTorches) {
                consumeTorch(player);
            }

            rememberPlaced(torchPos);
            placed++;
            placedThisTick++;
            if (maxTorches > 0 && placed >= maxTorches) {
                return finish(player, "message.autotorch.max_reached", scannedThisTick, placedThisTick,
                        placed, placementName());
            }
        }
        return new TickResult(false, scannedThisTick, placedThisTick);
    }

    void showInitialProgress(EntityPlayerMP player) {
        sendProgress(player);
    }

    void tickProgress(EntityPlayerMP player) {
        if (--ticksUntilProgressUpdate > 0) {
            return;
        }
        ticksUntilProgressUpdate = progressUpdateIntervalTicks;
        sendProgress(player);
    }

    int progressPercent() {
        long scanned = (long) pass * volume + scanIndex;
        return (int) Math.min(100L, scanned * 100L / (volume * 2L));
    }

    int placedCount() {
        return placed;
    }

    /** 任务实际放置物品的注册 ID，供状态查询在客户端按本地语言显示名称。 */
    String placementItemId() {
        return ForgeRegistries.ITEMS.getKey(placementItem).toString();
    }

    private void sendProgress(EntityPlayerMP player) {
        long scanned = (long) pass * volume + scanIndex;
        long total = volume * 2L;
        int percent = (int) Math.min(100L, scanned * 100L / total);
        int passFilled = (int) Math.min(PROGRESS_BAR_LENGTH,
                scanIndex * PROGRESS_BAR_LENGTH / volume);
        ITextComponent bar;
        if (pass == 0) {
            bar = new TextComponentString(progressBar(passFilled)).setStyle(new Style().setColor(TextFormatting.GRAY))
                    .appendSibling(new TextComponentString(progressBar(PROGRESS_BAR_LENGTH - passFilled))
                            .setStyle(new Style().setColor(TextFormatting.DARK_GRAY)));
        } else {
            // 第二轮从左向右用绿色覆盖第一轮已经铺满的灰色进度条。
            bar = new TextComponentString(progressBar(passFilled)).setStyle(new Style().setColor(TextFormatting.GREEN))
                    .appendSibling(new TextComponentString(progressBar(PROGRESS_BAR_LENGTH - passFilled))
                            .setStyle(new Style().setColor(TextFormatting.GRAY)));
        }
        player.sendStatusMessage(new TextComponentTranslation("message.autotorch.progress", bar, percent, placed,
                placementName()), true);
    }

    private String placementName() {
        return new ItemStack(placementItem).getDisplayName();
    }

    private static TickResult finish(
            EntityPlayerMP player,
            String messageKey,
            int scannedThisTick,
            int placedThisTick,
            Object... messageArguments
    ) {
        player.sendStatusMessage(new TextComponentString(""), true);
        player.sendStatusMessage(new TextComponentTranslation(messageKey, messageArguments), false);
        return new TickResult(true, scannedThisTick, placedThisTick);
    }

    private BlockPos positionAt(long index) {
        // 与总体积互质的步长会恰好访问每个位置一次，且不会额外保存打乱后的坐标表。
        long linear = (permutationStart + index * permutationStep) % volume;
        int x = (int) (linear % sizeX);
        linear /= sizeX;
        int z = (int) (linear % sizeZ);
        int y = (int) (linear / sizeZ);
        return min.add(x, y, z);
    }

    private boolean isPotentialSpawnPosition(BlockPos feet) {
        if (isExcluded(feet) || !level.isAirBlock(feet) || !level.isAirBlock(feet.up())) {
            return false;
        }
        if (level.getBlockState(feet).getMaterial().isLiquid()
                || level.getLightFor(EnumSkyBlock.BLOCK, feet) > lightThreshold) {
            return false;
        }
        if (undergroundOnly && level.getLightFor(EnumSkyBlock.SKY, feet) > 0) {
            return false;
        }

        BlockPos floorPos = feet.down();
        IBlockState floor = level.getBlockState(floorPos);
        return floor.getBlockFaceShape(level, floorPos, EnumFacing.UP) == BlockFaceShape.SOLID;
    }

    private BlockPos findTorchPosition(EntityPlayerMP player, BlockPos darkPosition) {
        // 优先尝试暗点脚下；失败后在附近随机寻找可放置且玩家有权限的位置。
        // 尝试次数在本次查找开始时读一次：保留运行中调速，同时避免循环条件里反复读配置。
        int maxAttempts = ServerConfig.randomPlacementAttempts();
        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            int radius = attempt == 0 ? 0 : 4;
            int dx = radius == 0 ? 0 : random.nextInt(radius * 2 + 1) - radius;
            int dz = radius == 0 ? 0 : random.nextInt(radius * 2 + 1) - radius;
            int dy = radius == 0 ? 0 : random.nextInt(5) - 2;
            BlockPos candidate = darkPosition.add(dx, dy, dz);

            if (!insideSelection(candidate) || isExcluded(candidate) || !isChunkLoaded(candidate)) {
                continue;
            }
            if (!level.isAirBlock(candidate) || level.getBlockState(candidate).getMaterial().isLiquid()) {
                continue;
            }

            // 1.12.2 没有 1.13 的 Block.isValidPosition，改用等价的 canPlaceBlockAt。
            if (placementItem.getBlock().canPlaceBlockAt(level, candidate)
                    && level.isBlockModifiable(player, candidate)) {
                return candidate.toImmutable();
            }
        }
        return null;
    }

    private boolean insideSelection(BlockPos pos) {
        return pos.getY() >= min.getY() && pos.getY() < min.getY() + sizeY && selection.contains(pos);
    }

    private boolean isChunkLoaded(BlockPos pos) {
        return level.isBlockLoaded(pos);
    }

    private boolean isExcluded(BlockPos pos) {
        return exclusions.contains(pos);
    }

    private boolean farEnoughFromPlaced(BlockPos pos) {
        // 用配置间距划分空间桶，只需检查相邻 27 个桶即可判断最近距离。
        int cellX = Math.floorDiv(pos.getX(), configuredSpacing);
        int cellY = Math.floorDiv(pos.getY(), configuredSpacing);
        int cellZ = Math.floorDiv(pos.getZ(), configuredSpacing);
        long configuredMinimumSquared = (long) configuredSpacing * configuredSpacing;

        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    List<BlockPos> nearby = placedByCell.get(cellKey(cellX + dx, cellY + dy, cellZ + dz));
                    if (nearby == null) {
                        continue;
                    }
                    for (BlockPos other : nearby) {
                        double distanceSquared = other.distanceSq(pos);
                        if (distanceSquared >= configuredMinimumSquared) {
                            continue;
                        }
                        // 补点按已有光源的当前发光等级计算，兼容较暗光源和方块状态变化。
                        // 只查询已加载区块；发光等级取自方块状态，与实际放置时使用的默认状态一致。
                        int spacing = pass == 0 ? configuredSpacing : AutoTorchRules.secondPassSpacing(
                                configuredSpacing, lightThreshold, secondPassMinimumSpacing,
                                isChunkLoaded(other) ? level.getBlockState(other).getLightValue() : 0);
                        long minimumSquared = (long) spacing * spacing;
                        if (distanceSquared < minimumSquared) {
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
        placedByCell.computeIfAbsent(cellKey(cellX, cellY, cellZ), ignored -> new ArrayList<>()).add(pos);
    }

    private boolean hasTorch(EntityPlayerMP player) {
        for (int slot = 0; slot < player.inventory.getSizeInventory(); slot++) {
            // 放置物品现在可配置，消耗与检查都以任务开始时解析出的物品为准。
            if (player.inventory.getStackInSlot(slot).getItem() == placementItem) {
                return true;
            }
        }
        return false;
    }

    private void consumeTorch(EntityPlayerMP player) {
        for (int slot = 0; slot < player.inventory.getSizeInventory(); slot++) {
            ItemStack stack = player.inventory.getStackInSlot(slot);
            if (stack.getItem() == placementItem) {
                stack.shrink(1);
                player.inventory.markDirty();
                return;
            }
        }
    }

    private static long cellKey(int x, int y, int z) {
        return new BlockPos(x, y, z).toLong();
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

    private static String progressBar(int length) {
        StringBuilder result = new StringBuilder(length);
        for (int i = 0; i < length; i++) result.append('|');
        return result.toString();
    }

    static final class TickResult {
        private final boolean done;
        private final int scanned;
        private final int placed;

        TickResult(boolean done, int scanned, int placed) {
            this.done = done;
            this.scanned = scanned;
            this.placed = placed;
        }

        boolean done() { return done; }
        int scanned() { return scanned; }
        int placed() { return placed; }
    }
}
