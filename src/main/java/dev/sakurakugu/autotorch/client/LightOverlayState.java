package dev.sakurakugu.autotorch.client;

import java.util.ArrayList;
import java.util.List;

import dev.sakurakugu.autotorch.AutoTorchRules;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/** 维护仅在客户端执行的光照风险扫描，以及供渲染使用的不可变快照。 */
public final class LightOverlayState {
    public static final int MIN_HORIZONTAL_RANGE = 1;
    public static final int MAX_HORIZONTAL_RANGE = 64;
    private static final int DEFAULT_HORIZONTAL_RANGE = 16;
    private static final int DOWN_RANGE = 16;
    private static final int UP_RANGE = 4;
    private static final int SCAN_BUDGET_PER_TICK = 12_000;
    private static final int HEIGHT = DOWN_RANGE + UP_RANGE + 1;

    private static boolean enabled = ClientConfig.isLightOverlayEnabled();
    private static boolean swampSlimeDetectionEnabled = ClientConfig.detectsSwampSlimes();
    private static boolean drownedDetectionEnabled = ClientConfig.detectsDrowned();
    private static DisplayMode displayMode = ClientConfig.showsLightOverlayNumbers()
            ? DisplayMode.NUMBERS : DisplayMode.CROSSES;
    private static int horizontalRange = ClientConfig.lightOverlayRange();
    private static int scanRange = horizontalRange;
    private static @Nullable ClientLevel level;
    private static @Nullable BlockPos scanCenter;
    private static int scanIndex;
    private static int ticksSinceCompleted;
    private static List<Marker> workingMarkers = new ArrayList<>();
    private static List<Marker> markers = List.of();

    private LightOverlayState() {
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
        displayMode = displayMode == DisplayMode.CROSSES ? DisplayMode.NUMBERS : DisplayMode.CROSSES;
        ClientConfig.setShowsLightOverlayNumbers(displayMode == DisplayMode.NUMBERS);
        return displayMode;
    }

    public static boolean isSwampSlimeDetectionEnabled() {
        return swampSlimeDetectionEnabled;
    }

    public static boolean toggleSwampSlimeDetection() {
        swampSlimeDetectionEnabled = !swampSlimeDetectionEnabled;
        ClientConfig.setDetectsSwampSlimes(swampSlimeDetectionEnabled);
        clearScan();
        return swampSlimeDetectionEnabled;
    }

    public static boolean isDrownedDetectionEnabled() {
        return drownedDetectionEnabled;
    }

    public static boolean toggleDrownedDetection() {
        drownedDetectionEnabled = !drownedDetectionEnabled;
        ClientConfig.setDetectsDrowned(drownedDetectionEnabled);
        clearScan();
        return drownedDetectionEnabled;
    }

    public static int horizontalRange() {
        return horizontalRange;
    }

    public static void setHorizontalRange(int value) {
        if (value < MIN_HORIZONTAL_RANGE || value > MAX_HORIZONTAL_RANGE) {
            throw new IllegalArgumentException("Light overlay range must be between "
                    + MIN_HORIZONTAL_RANGE + " and " + MAX_HORIZONTAL_RANGE);
        }
        if (horizontalRange != value) {
            horizontalRange = value;
            ClientConfig.setLightOverlayRange(value);
            clearScan();
        }
    }

    public static List<Marker> markers() {
        return markers;
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
        if (scanCenter == null) {
            beginScan(playerPos);
        }
        int scanVolume = scanVolume();
        if (scanIndex >= scanVolume) {
            ticksSinceCompleted++;
            if (ticksSinceCompleted >= AutoTorchRules.lightOverlayRefreshIntervalTicks(scanRange)
                    || movedOutsideRefreshArea(playerPos, scanCenter)) {
                beginScan(playerPos);
                scanVolume = scanVolume();
            } else {
                return;
            }
        }

        int end = Math.min(scanVolume, scanIndex + SCAN_BUDGET_PER_TICK);
        BlockPos.MutableBlockPos feet = new BlockPos.MutableBlockPos();
        while (scanIndex < end) {
            setPositionForIndex(feet, scanCenter, scanIndex++);
            Marker marker = markerAt(currentLevel, feet);
            if (marker != null) {
                workingMarkers.add(marker);
            }
        }
        if (scanIndex >= scanVolume) {
            markers = List.copyOf(workingMarkers);
            ticksSinceCompleted = 0;
        }
    }

    private static void beginScan(BlockPos center) {
        scanCenter = center.immutable();
        scanRange = horizontalRange;
        scanIndex = 0;
        ticksSinceCompleted = 0;
        workingMarkers = new ArrayList<>();
    }

    private static void clearScan() {
        scanCenter = null;
        scanRange = horizontalRange;
        scanIndex = 0;
        ticksSinceCompleted = 0;
        workingMarkers = new ArrayList<>();
        markers = List.of();
    }

    private static boolean movedOutsideRefreshArea(BlockPos playerPos, BlockPos center) {
        return Math.abs(playerPos.getX() - center.getX()) >= 4
                || Math.abs(playerPos.getY() - center.getY()) >= 4
                || Math.abs(playerPos.getZ() - center.getZ()) >= 4;
    }

    private static void setPositionForIndex(BlockPos.MutableBlockPos pos, BlockPos center, int index) {
        int diameter = scanRange * 2 + 1;
        int xOffset = index % diameter - scanRange;
        index /= diameter;
        int zOffset = index % diameter - scanRange;
        int yOffset = index / diameter - DOWN_RANGE;
        pos.set(center.getX() + xOffset, center.getY() + yOffset, center.getZ() + zOffset);
    }

    private static int scanVolume() {
        int diameter = scanRange * 2 + 1;
        return diameter * diameter * HEIGHT;
    }

    private static @Nullable Marker markerAt(ClientLevel level, BlockPos feet) {
        if (!level.hasChunk(SectionPos.blockToSectionCoord(feet.getX()),
                SectionPos.blockToSectionCoord(feet.getZ()))) {
            return null;
        }
        if (drownedDetectionEnabled && isDrownedRisk(level, feet)) {
            return marker(level, feet, RiskType.DROWNED);
        }
        if (!level.getFluidState(feet).isEmpty()
                || !level.getFluidState(feet.above()).isEmpty()) {
            return null;
        }

        BlockState feetState = level.getBlockState(feet);
        BlockState headState = level.getBlockState(feet.above());
        if (!feetState.getCollisionShape(level, feet).isEmpty()
                || !headState.getCollisionShape(level, feet.above()).isEmpty()) {
            return null;
        }

        BlockPos floorPos = feet.below();
        BlockState floor = level.getBlockState(floorPos);
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

    private static boolean isDrownedRisk(ClientLevel level, BlockPos pos) {
        // 每个连续且可生成怪物的水柱中，仅保留最高的完全有效位置。
        return isDrownedSpawnPosition(level, pos) && !isDrownedSpawnPosition(level, pos.above());
    }

    private static boolean isDrownedSpawnPosition(ClientLevel level, BlockPos pos) {
        if (level.getBrightness(LightLayer.BLOCK, pos) != 0
                || !level.getFluidState(pos).is(FluidTags.WATER)
                || !level.getFluidState(pos.below()).is(FluidTags.WATER)
                || !SpawnPlacementTypes.IN_WATER.isSpawnPositionOk(level, pos, EntityType.DROWNED)) {
            return false;
        }

        boolean drownedInSpawnList = level.getBiome(pos).value().getMobSettings()
                .getMobs(MobCategory.MONSTER).unwrap().stream()
                .anyMatch(entry -> entry.value().type() == EntityType.DROWNED);
        return drownedInSpawnList
                && (level.getBiome(pos).is(BiomeTags.MORE_FREQUENT_DROWNED_SPAWNS)
                || pos.getY() < level.getSeaLevel() - 5);
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
        NUMBERS
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
}
