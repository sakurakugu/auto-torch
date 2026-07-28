package com.sakurakugu.autotorch.server;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.stream.Collectors;

import com.sakurakugu.autotorch.network.AreaZone;
import net.minecraft.util.EnumChatFormatting;
import com.sakurakugu.autotorch.compat.BlockPos;
import com.sakurakugu.autotorch.compat.WorldAccess;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.world.WorldServer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Item;
import net.minecraft.block.Block;
import net.minecraft.init.Blocks;

/** 单个玩家的增量照明任务，通过每刻预算避免大选区阻塞服务端线程。 */
final class LightingTask {
    private static final int PROGRESS_UPDATE_INTERVAL_TICKS = 10;
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
    private final int lightThreshold;
    private final boolean consumeTorches;
    private final boolean undergroundOnly;
    private final AreaZoneIndex exclusions;
    private final Random random;
    private final Map<Long, List<BlockPos>> placedByCell = new HashMap<>();

    // scanIndex 在当前扫描轮次中的位置；第二轮会缩短间距以补齐遗漏暗区。
    private long scanIndex;
    private int pass;
    private int placed;
    private int skippedUnloaded;
    private int ticksUntilProgressUpdate = PROGRESS_UPDATE_INTERVAL_TICKS;

    LightingTask(
            WorldServer level,
            AreaZone selection,
            BlockPos scanMin,
            BlockPos scanMax,
            int maxTorches,
            int configuredSpacing,
            int lightThreshold,
            boolean consumeTorches,
            boolean undergroundOnly,
            List<AreaZone> exclusions,
            UUID playerId
    ) {
        this.level = level;
        this.selection = selection;
        this.min = scanMin.getImmutable();
        this.sizeX = scanMax.getX() - min.getX() + 1;
        this.sizeY = scanMax.getY() - min.getY() + 1;
        this.sizeZ = scanMax.getZ() - min.getZ() + 1;
        this.volume = (long) sizeX * sizeY * sizeZ;
        this.maxTorches = maxTorches;
        this.configuredSpacing = configuredSpacing;
        this.lightThreshold = lightThreshold;
        this.consumeTorches = consumeTorches;
        this.undergroundOnly = undergroundOnly;
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
        if (player.getServerForPlayer() != level) {
            return finish(player, "message.autotorch.wrong_dimension", 0, 0);
        }
        if (maxTorches > 0 && placed >= maxTorches) {
            return finish(player, "message.autotorch.max_reached", 0, 0, placed);
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
                        placed, skippedUnloaded);
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
            if (torchPos == null || !farEnoughFromPlaced(torchPos, currentSpacing())) {
                continue;
            }
            if (consumeTorches && !hasTorch(player)) {
                return finish(player, "message.autotorch.out_of_torches", scannedThisTick, placedThisTick, placed);
            }
            if (!WorldAccess.setTorch(level, torchPos, UPDATE_ALL)) {
                continue;
            }
            if (consumeTorches) {
                consumeTorch(player);
            }

            rememberPlaced(torchPos);
            placed++;
            placedThisTick++;
            if (maxTorches > 0 && placed >= maxTorches) {
                return finish(player, "message.autotorch.max_reached", scannedThisTick, placedThisTick, placed);
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
        ticksUntilProgressUpdate = PROGRESS_UPDATE_INTERVAL_TICKS;
        sendProgress(player);
    }

    private void sendProgress(EntityPlayerMP player) {
        long scanned = (long) pass * volume + scanIndex;
        long total = volume * 2L;
        int percent = (int) Math.min(100L, scanned * 100L / total);
        int passFilled = (int) Math.min(PROGRESS_BAR_LENGTH,
                scanIndex * PROGRESS_BAR_LENGTH / volume);
        String bar = formattedProgressBar(pass, passFilled);
        LightingTaskManager.sendSystemMessage(
                player, new ChatComponentTranslation("message.autotorch.progress", bar, percent, placed), true);
    }

    private static TickResult finish(
            EntityPlayerMP player,
            String messageKey,
            int scannedThisTick,
            int placedThisTick,
            Object... messageArguments
    ) {
        LightingTaskManager.sendSystemMessage(player, new ChatComponentText(""), true);
        LightingTaskManager.sendSystemMessage(player, new ChatComponentTranslation(messageKey, messageArguments));
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
        if (isExcluded(feet) || !WorldAccess.isAir(level, feet) || !WorldAccess.isAir(level, feet.up())) {
            return false;
        }
        if (WorldAccess.block(level, feet).getMaterial().isLiquid()
                || WorldAccess.blockLight(level, feet) > lightThreshold) {
            return false;
        }
        if (undergroundOnly && WorldAccess.skyLight(level, feet) > 0) {
            return false;
        }

        BlockPos floorPos = feet.down();
        return WorldAccess.isTopSolid(level, floorPos);
    }

    private BlockPos findTorchPosition(EntityPlayerMP player, BlockPos darkPosition) {
        // 优先尝试暗点脚下；失败后在附近随机寻找可放置且玩家有权限的位置。
        for (int attempt = 0; attempt < ServerConfig.randomPlacementAttempts(); attempt++) {
            int radius = attempt == 0 ? 0 : 4;
            int dx = radius == 0 ? 0 : random.nextInt(radius * 2 + 1) - radius;
            int dz = radius == 0 ? 0 : random.nextInt(radius * 2 + 1) - radius;
            int dy = radius == 0 ? 0 : random.nextInt(5) - 2;
            BlockPos candidate = darkPosition.add(dx, dy, dz);

            if (!insideSelection(candidate) || isExcluded(candidate) || !isChunkLoaded(candidate)) {
                continue;
            }
            if (!WorldAccess.isAir(level, candidate)
                    || WorldAccess.block(level, candidate).getMaterial().isLiquid()) {
                continue;
            }

            BlockPos floorPos = candidate.down();
            if (WorldAccess.isTopSolid(level, floorPos)
                    && level.canMineBlock(player, candidate.getX(), candidate.getY(), candidate.getZ())) {
                return candidate.getImmutable();
            }
        }
        return null;
    }

    private boolean insideSelection(BlockPos pos) {
        return pos.getY() >= min.getY() && pos.getY() < min.getY() + sizeY && selection.contains(pos);
    }

    private boolean isChunkLoaded(BlockPos pos) {
        return WorldAccess.exists(level, pos);
    }

    private boolean isExcluded(BlockPos pos) {
        return exclusions.contains(pos);
    }

    private int currentSpacing() {
        return pass == 0 ? configuredSpacing : Math.max(ServerConfig.minSpacing(), configuredSpacing / 2);
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
                    List<BlockPos> nearby = placedByCell.get(cellKey(cellX + dx, cellY + dy, cellZ + dz));
                    if (nearby == null) {
                        continue;
                    }
                    for (BlockPos other : nearby) {
                        if (other.distanceSq(pos) < minimumSquared) {
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

    private static boolean hasTorch(EntityPlayerMP player) {
        for (int slot = 0; slot < player.inventory.getSizeInventory(); slot++) {
            ItemStack stack = player.inventory.getStackInSlot(slot);
            if (stack != null && stack.getItem() == Item.getItemFromBlock(Blocks.torch)) {
                return true;
            }
        }
        return false;
    }

    private static void consumeTorch(EntityPlayerMP player) {
        for (int slot = 0; slot < player.inventory.getSizeInventory(); slot++) {
            ItemStack stack = player.inventory.getStackInSlot(slot);
            if (stack != null && stack.getItem() == Item.getItemFromBlock(Blocks.torch)) {
                stack.stackSize--;
                if (stack.stackSize <= 0) {
                    player.inventory.setInventorySlotContents(slot, null);
                }
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

    static String formattedProgressBar(int pass, int filled) {
        EnumChatFormatting filledColor = pass == 0 ? EnumChatFormatting.GRAY : EnumChatFormatting.GREEN;
        EnumChatFormatting remainingColor = pass == 0 ? EnumChatFormatting.DARK_GRAY : EnumChatFormatting.GRAY;
        return filledColor + progressBar(filled)
                + remainingColor + progressBar(PROGRESS_BAR_LENGTH - filled)
                + EnumChatFormatting.RESET;
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
