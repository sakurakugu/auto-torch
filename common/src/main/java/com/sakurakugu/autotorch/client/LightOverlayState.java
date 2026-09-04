package com.sakurakugu.autotorch.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.sakurakugu.autotorch.config.ConfigDefinitions;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.SectionPos;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.chunk.LevelChunk;

/** 维护仅在客户端执行的光照风险扫描，以及供渲染使用的不可变快照。 */
public final class LightOverlayState {
    private static final int MAX_VERTICAL_RANGE = 64;
    private static final int SCAN_BUDGET_PER_TICK = 12_000;
    private static final int LIGHT_CHANGE_RADIUS = 15;
    private static final int CACHE_MARGIN = 16;
    private static final int VERIFICATION_INTERVAL_TICKS = 100;

    private static boolean enabled = ClientConfig.isLightOverlayEnabled();
    private static boolean swampSlimeDetectionEnabled = ClientConfig.detectsSwampSlimes();
    private static boolean drownedDetectionEnabled = ClientConfig.detectsDrowned();
    private static DisplayMode displayMode = modeFromConfig();
    private static int horizontalRange = ClientConfig.lightOverlayRange();
    private static int downRange = ClientConfig.lightOverlayDownRange();
    private static int upRange = ClientConfig.lightOverlayUpRange();
    private static ClientLevel level;
    private static BlockPos scanCenter;
    private static int minY;
    private static int maxY;
    private static int ticksUntilVerification = VERIFICATION_INTERVAL_TICKS;
    private static final Map<Long, MarkerColumn> columnCache = new HashMap<>();
    private static final Set<Long> urgentColumns = new LinkedHashSet<>();
    private static final Set<Long> lightUpdateColumns = new LinkedHashSet<>();
    private static final Set<Long> backgroundColumns = new LinkedHashSet<>();
    private static final Set<Long> verificationColumns = new LinkedHashSet<>();
    private static List<MarkerColumn> markerColumns = List.of();

    private LightOverlayState() {
    }

    /** 配置整体替换后同步运行时缓存。 */
    public static void reloadConfig() {
        enabled = ClientConfig.isLightOverlayEnabled();
        swampSlimeDetectionEnabled = ClientConfig.detectsSwampSlimes();
        drownedDetectionEnabled = ClientConfig.detectsDrowned();
        displayMode = modeFromConfig();
        horizontalRange = ClientConfig.lightOverlayRange();
        downRange = ClientConfig.lightOverlayDownRange();
        upRange = ClientConfig.lightOverlayUpRange();
        normalizeVerticalRange();
        clearScan();
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static boolean toggle() {
        setEnabled(!enabled);
        return enabled;
    }

    public static void setEnabled(boolean value) {
        if (enabled == value) {
            return;
        }
        enabled = value;
        ClientConfig.setLightOverlayEnabled(value);
        if (!enabled) {
            clearScan();
        }
    }

    public static DisplayMode displayMode() {
        return displayMode;
    }

    public static DisplayMode cycleDisplayMode() {
        setDisplayMode(DisplayMode.values()[(displayMode.ordinal() + 1) % DisplayMode.values().length]);
        return displayMode;
    }

    public static void setDisplayMode(DisplayMode value) {
        if (displayMode == value) {
            return;
        }
        displayMode = value;
        ClientConfig.setLightOverlayMode(value.ordinal());
        ClientConfig.setShowsLightOverlayNumbers(value != DisplayMode.CROSSES);
    }

    public static boolean isSwampSlimeDetectionEnabled() {
        return swampSlimeDetectionEnabled;
    }

    public static boolean toggleSwampSlimeDetection() {
        setSwampSlimeDetectionEnabled(!swampSlimeDetectionEnabled);
        return swampSlimeDetectionEnabled;
    }

    public static void setSwampSlimeDetectionEnabled(boolean value) {
        if (swampSlimeDetectionEnabled == value) {
            return;
        }
        swampSlimeDetectionEnabled = value;
        ClientConfig.setDetectsSwampSlimes(value);
        clearScan();
    }

    public static boolean isDrownedDetectionEnabled() {
        return drownedDetectionEnabled;
    }

    public static boolean toggleDrownedDetection() {
        setDrownedDetectionEnabled(!drownedDetectionEnabled);
        return drownedDetectionEnabled;
    }

    public static void setDrownedDetectionEnabled(boolean value) {
        if (drownedDetectionEnabled == value) {
            return;
        }
        drownedDetectionEnabled = value;
        ClientConfig.setDetectsDrowned(value);
        clearScan();
    }

    public static int horizontalRange() {
        return horizontalRange;
    }

    public static void setHorizontalRange(int value) {
        if (value < ConfigDefinitions.LIGHT_OVERLAY_HORIZONTAL_RANGE.minValue()
                || value > ConfigDefinitions.LIGHT_OVERLAY_HORIZONTAL_RANGE.maxValue()) {
            throw new IllegalArgumentException("Light overlay range must be between "
                    + ConfigDefinitions.LIGHT_OVERLAY_HORIZONTAL_RANGE.minValue() + " and "
                    + ConfigDefinitions.LIGHT_OVERLAY_HORIZONTAL_RANGE.maxValue());
        }
        if (horizontalRange != value) {
            horizontalRange = value;
            ClientConfig.setLightOverlayRange(value);
            clearScan();
        }
    }

    public static int downRange() { return downRange; }
    public static int upRange() { return upRange; }

    public static void setDownRange(int value) {
        int clamped = Math.max(0, Math.min(MAX_VERTICAL_RANGE - upRange, value));
        if (downRange != clamped) {
            downRange = clamped;
            ClientConfig.setLightOverlayDownRange(clamped);
            clearScan();
        }
    }

    public static void setUpRange(int value) {
        int clamped = Math.max(0, Math.min(MAX_VERTICAL_RANGE - downRange, value));
        if (upRange != clamped) {
            upRange = clamped;
            ClientConfig.setLightOverlayUpRange(clamped);
            clearScan();
        }
    }

    private static void normalizeVerticalRange() {
        if (downRange + upRange > MAX_VERTICAL_RANGE) {
            upRange = Math.max(0, MAX_VERTICAL_RANGE - downRange);
            ClientConfig.setLightOverlayUpRange(upRange);
        }
    }

    static List<MarkerColumn> markerColumns() {
        return markerColumns;
    }

    public static void tick(Minecraft minecraft) {
        ClientLevel currentLevel = minecraft.level;
        if (currentLevel != level) {
            level = currentLevel;
            clearScan();
        }
        if (!enabled || currentLevel == null || minecraft.player == null) {
            return;
        }

        BlockPos playerPos = minecraft.player.blockPosition();
        boolean visibleAreaChanged = updateVisibleArea(playerPos);
        if (--ticksUntilVerification <= 0) {
            enqueueVisibleColumns(verificationColumns);
            ticksUntilVerification = VERIFICATION_INTERVAL_TICKS;
        }

        Set<Long> activeQueue = !urgentColumns.isEmpty() ? urgentColumns
                : !lightUpdateColumns.isEmpty() ? lightUpdateColumns
                : !backgroundColumns.isEmpty() ? backgroundColumns : verificationColumns;
        boolean cacheChanged = scanQueuedColumns(currentLevel, activeQueue, SCAN_BUDGET_PER_TICK);
        if (visibleAreaChanged || cacheChanged) {
            publishVisibleMarkers();
        }
    }

    private static void clearScan() {
        scanCenter = null;
        minY = 0;
        maxY = 0;
        ticksUntilVerification = VERIFICATION_INTERVAL_TICKS;
        columnCache.clear();
        urgentColumns.clear();
        lightUpdateColumns.clear();
        backgroundColumns.clear();
        verificationColumns.clear();
        markerColumns = List.of();
    }

    private static boolean updateVisibleArea(BlockPos playerPos) {
        boolean verticalChanged = scanCenter == null
                || Math.abs(playerPos.getY() - scanCenter.getY()) >= 4;
        boolean centerChanged = scanCenter == null
                || scanCenter.getX() != playerPos.getX()
                || scanCenter.getZ() != playerPos.getZ()
                || verticalChanged;
        if (!verticalChanged && !centerChanged) {
            return false;
        }

        int centerY = verticalChanged ? playerPos.getY() : scanCenter.getY();
        scanCenter = new BlockPos(playerPos.getX(), centerY, playerPos.getZ());
        minY = centerY - downRange;
        maxY = centerY + upRange;
        pruneDistantColumns();
        enqueueVisibleColumns(backgroundColumns);
        return true;
    }

    private static void enqueueVisibleColumns(Set<Long> destination) {
        if (scanCenter == null) {
            return;
        }
        // 从玩家附近向外加入队列，使首次开启和移动后的近处标记最先出现。
        int centerX = scanCenter.getX();
        int centerZ = scanCenter.getZ();
        for (int radius = 0; radius <= horizontalRange; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                enqueueColumn(destination, centerX + dx, centerZ - radius);
                if (radius != 0) {
                    enqueueColumn(destination, centerX + dx, centerZ + radius);
                }
            }
            for (int dz = -radius + 1; dz < radius; dz++) {
                enqueueColumn(destination, centerX - radius, centerZ + dz);
                enqueueColumn(destination, centerX + radius, centerZ + dz);
            }
        }
    }

    private static void enqueueColumn(Set<Long> destination, int x, int z) {
        long key = columnKey(x, z);
        if (destination == urgentColumns) {
            backgroundColumns.remove(key);
            verificationColumns.remove(key);
        }
        MarkerColumn cached = columnCache.get(key);
        if (cached == null || cached.minY() != minY
                || destination == verificationColumns || destination == urgentColumns) {
            destination.add(key);
        }
    }

    private static boolean scanQueuedColumns(ClientLevel currentLevel, Set<Long> queue, int budget) {
        int columnsRemaining = Math.max(1, budget / (downRange + upRange + 1));
        boolean changed = false;
        Iterator<Long> iterator = queue.iterator();
        while (iterator.hasNext() && columnsRemaining-- > 0) {
            long key = iterator.next();
            if (!isVisibleColumn(key)) {
                iterator.remove();
                continue;
            }
            iterator.remove();
            urgentColumns.remove(key);
            lightUpdateColumns.remove(key);
            backgroundColumns.remove(key);
            verificationColumns.remove(key);
            List<Marker> updatedMarkers = scanColumn(currentLevel, columnX(key), columnZ(key));
            MarkerColumn previous = columnCache.get(key);
            if (previous == null || previous.minY() != minY || !previous.markers().equals(updatedMarkers)) {
                columnCache.put(key, new MarkerColumn(key, minY, updatedMarkers));
                changed = true;
            }
            // 方块变更通知可能早于原版光照引擎完成传播；下一 tick 再复核一次，避免缓存瞬时的 0 光照。
            if (queue == urgentColumns) {
                lightUpdateColumns.add(key);
            }
        }
        return changed;
    }

    private static List<Marker> scanColumn(ClientLevel currentLevel, int x, int z) {
        int chunkX = SectionPos.blockToSectionCoord(x);
        int chunkZ = SectionPos.blockToSectionCoord(z);
        if (!currentLevel.hasChunk(chunkX, chunkZ)) {
            return List.of();
        }
        LevelChunk chunk = currentLevel.getChunk(chunkX, chunkZ);
        List<Marker> columnMarkers = new ArrayList<>();
        BlockPos.MutableBlockPos floor = new BlockPos.MutableBlockPos(x, minY - 1, z);
        BlockPos.MutableBlockPos feet = new BlockPos.MutableBlockPos(x, minY, z);
        BlockPos.MutableBlockPos head = new BlockPos.MutableBlockPos(x, minY + 1, z);
        BlockState floorState = chunk.getBlockState(floor);
        BlockState feetState = chunk.getBlockState(feet);
        BlockState headState = chunk.getBlockState(head);
        for (int y = minY; y <= maxY; y++) {
            floor.setY(y - 1);
            feet.setY(y);
            head.setY(y + 1);
            Marker marker = markerAt(currentLevel, floor, feet, head, floorState, feetState, headState);
            if (marker != null) {
                columnMarkers.add(marker);
            }
            floorState = feetState;
            feetState = headState;
            head.setY(y + 2);
            headState = chunk.getBlockState(head);
        }
        return List.copyOf(columnMarkers);
    }

    private static void publishVisibleMarkers() {
        List<MarkerColumn> visibleColumns = new ArrayList<>();
        for (Map.Entry<Long, MarkerColumn> entry : columnCache.entrySet()) {
            if (isVisibleColumn(entry.getKey())) {
                visibleColumns.add(entry.getValue());
            }
        }
        markerColumns = List.copyOf(visibleColumns);
    }

    private static boolean isVisibleColumn(long key) {
        return scanCenter != null
                && Math.abs(columnX(key) - scanCenter.getX()) <= horizontalRange
                && Math.abs(columnZ(key) - scanCenter.getZ()) <= horizontalRange;
    }

    private static void pruneDistantColumns() {
        int retainedRange = horizontalRange + CACHE_MARGIN;
        columnCache.keySet().removeIf(key -> Math.abs(columnX(key) - scanCenter.getX()) > retainedRange
                || Math.abs(columnZ(key) - scanCenter.getZ()) > retainedRange);
        urgentColumns.removeIf(key -> !isVisibleColumn(key));
        lightUpdateColumns.removeIf(key -> !isVisibleColumn(key));
        backgroundColumns.removeIf(key -> !isVisibleColumn(key));
        verificationColumns.removeIf(key -> !isVisibleColumn(key));
    }

    /** 方块形状变化后刷新其碰撞影响位置以及方块光最多可传播到的列。 */
    public static void markBlockDirty(ClientLevel sourceLevel, BlockPos pos) {
        if (!enabled || sourceLevel != level || !intersectsVerticalRange(pos.getY() - LIGHT_CHANGE_RADIUS,
                pos.getY() + LIGHT_CHANGE_RADIUS)) {
            return;
        }
        enqueueUrgentRange(pos.getX() - LIGHT_CHANGE_RADIUS, pos.getZ() - LIGHT_CHANGE_RADIUS,
                pos.getX() + LIGHT_CHANGE_RADIUS, pos.getZ() + LIGHT_CHANGE_RADIUS);
    }

    /** 客户端收到一节光照数据后，只重扫该节覆盖的可见列。 */
    public static void markSectionDirty(ClientLevel sourceLevel, int sectionX, int sectionY, int sectionZ) {
        int sectionMinY = sectionY << 4;
        if (!enabled || sourceLevel != level || !intersectsVerticalRange(sectionMinY - 2, sectionMinY + 17)) {
            return;
        }
        enqueueUrgentRange(sectionX << 4, sectionZ << 4,
                (sectionX << 4) + 15, (sectionZ << 4) + 15);
    }

    /** 区块载入或批量更新时刷新与当前显示区域相交的节范围。 */
    public static void markSectionRangeDirty(
            ClientLevel sourceLevel, int minSectionX, int minSectionY, int minSectionZ,
            int maxSectionX, int maxSectionY, int maxSectionZ
    ) {
        if (!enabled || sourceLevel != level
                || !intersectsVerticalRange((minSectionY << 4) - 2, (maxSectionY << 4) + 17)) {
            return;
        }
        enqueueUrgentRange(minSectionX << 4, minSectionZ << 4,
                (maxSectionX << 4) + 15, (maxSectionZ << 4) + 15);
    }

    private static void enqueueUrgentRange(int minX, int minZ, int maxX, int maxZ) {
        if (scanCenter == null) {
            return;
        }
        int visibleMinX = scanCenter.getX() - horizontalRange;
        int visibleMaxX = scanCenter.getX() + horizontalRange;
        int visibleMinZ = scanCenter.getZ() - horizontalRange;
        int visibleMaxZ = scanCenter.getZ() + horizontalRange;
        minX = Math.max(minX, visibleMinX);
        maxX = Math.min(maxX, visibleMaxX);
        minZ = Math.max(minZ, visibleMinZ);
        maxZ = Math.min(maxZ, visibleMaxZ);
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                long key = columnKey(x, z);
                backgroundColumns.remove(key);
                lightUpdateColumns.remove(key);
                verificationColumns.remove(key);
                urgentColumns.add(key);
            }
        }
    }

    private static boolean intersectsVerticalRange(int changedMinY, int changedMaxY) {
        return scanCenter != null && changedMinY <= maxY && changedMaxY >= minY;
    }

    private static long columnKey(int x, int z) {
        return (long) x << 32 | z & 0xFFFFFFFFL;
    }

    private static int columnX(long key) {
        return (int) (key >> 32);
    }

    private static int columnZ(long key) {
        return (int) key;
    }

    private static Marker markerAt(
            ClientLevel level, BlockPos floorPos, BlockPos feet, BlockPos head,
            BlockState floor, BlockState feetState, BlockState headState
    ) {
        if (drownedDetectionEnabled
                && isDrownedRisk(level, feet, head, floor, feetState, headState)) {
            return marker(level, feet, RiskType.DROWNED);
        }
        if (!feetState.getFluidState().isEmpty() || !headState.getFluidState().isEmpty()) {
            return null;
        }

        if (!feetState.getCollisionShape(level, feet).isEmpty()
                || !headState.getCollisionShape(level, head).isEmpty()) {
            return null;
        }

        if (!Block.isFaceFull(floor.getCollisionShape(level, floorPos), Direction.UP)) {
            return null;
        }
        if (!SpawnPlacementTypes.ON_GROUND.isSpawnPositionOk(level, feet, EntityType.ZOMBIE)) {
            return null;
        }
        int blockLight = level.getBrightness(LightLayer.BLOCK, feet);
        RiskType riskType = blockLight > 0 && isSwampSlimeRisk(level, feet, blockLight)
                ? RiskType.SWAMP_SLIME : RiskType.NORMAL;
        return new Marker(
                feet.immutable(),
                blockLight,
                level.getBrightness(LightLayer.SKY, feet),
                riskType
        );
    }

    private static boolean isSwampSlimeRisk(ClientLevel level, BlockPos feet, int blockLight) {
        return swampSlimeDetectionEnabled
                && blockLight <= 7
                && feet.getY() > 50
                && feet.getY() < 70
                && level.getBiome(feet).is(BiomeTags.ALLOWS_SURFACE_SLIME_SPAWNS)
                && SpawnPlacementTypes.ON_GROUND.isSpawnPositionOk(level, feet, EntityType.SLIME);
    }

    private static boolean isDrownedRisk(
            ClientLevel level, BlockPos feet, BlockPos head,
            BlockState floorState, BlockState feetState, BlockState headState
    ) {
        // 每个连续且可生成怪物的水柱中，仅保留最高的完全有效位置。
        return isDrownedSpawnPosition(level, feet, floorState, feetState)
                && !isDrownedSpawnPosition(level, head, feetState, headState);
    }

    private static boolean isDrownedSpawnPosition(
            ClientLevel level, BlockPos pos, BlockState belowState, BlockState state
    ) {
        if (level.getBrightness(LightLayer.BLOCK, pos) != 0
                || !state.getFluidState().is(FluidTags.WATER)
                || !belowState.getFluidState().is(FluidTags.WATER)) {
            return false;
        }

        // 1.20.6 客户端扫描时水中放置规则可能误判，水体条件已在上方完整检查。
        Holder<Biome> biome = level.getBiome(pos);
        return biomeAllowsDrowned(biome)
                && (biome.is(BiomeTags.MORE_FREQUENT_DROWNED_SPAWNS)
                || pos.getY() < level.getSeaLevel() - 5);
    }

    private static boolean biomeAllowsDrowned(Holder<Biome> biome) {
        boolean drownedInSpawnList = biome.value().getMobSettings()
                .getMobs(MobCategory.MONSTER).unwrap().stream()
                .anyMatch(entry -> entry.type == EntityType.DROWNED);
        // 客户端可能未同步生物群系怪物生成表，使用原版生物群系标签补足判定。
        return drownedInSpawnList
                || biome.is(BiomeTags.IS_OCEAN)
                || biome.is(BiomeTags.MORE_FREQUENT_DROWNED_SPAWNS)
                || biome.is(Biomes.DRIPSTONE_CAVES);
    }

    private static Marker marker(ClientLevel level, BlockPos pos, RiskType riskType) {
        return new Marker(
                pos.immutable(),
                level.getBrightness(LightLayer.BLOCK, pos),
                level.getBrightness(LightLayer.SKY, pos),
                riskType
        );
    }

    public enum DisplayMode {
        CROSSES,
        NUMBERS,
        BOXED_NUMBERS
    }

    private static DisplayMode modeFromConfig() {
        int mode = ClientConfig.lightOverlayMode();
        if (mode == 0 && ClientConfig.showsLightOverlayNumbers()) {
            mode = 1;
        }
        return DisplayMode.values()[Math.max(0, Math.min(mode, DisplayMode.values().length - 1))];
    }

    public enum RiskType {
        NORMAL,
        SWAMP_SLIME,
        DROWNED
    }

    public record Marker(BlockPos pos, int blockLight, int skyLight, RiskType riskType) {
        public boolean nightOnly() {
            return blockLight == 0 && skyLight > 0;
        }

        public boolean isRisk() {
            return blockLight == 0 || riskType != RiskType.NORMAL;
        }
    }

    record MarkerColumn(long key, int minY, List<Marker> markers) {
    }
}
