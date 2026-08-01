package com.sakurakugu.autotorch.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.sakurakugu.autotorch.AutoTorchRules;
import com.sakurakugu.autotorch.config.ConfigDefinitions;
import net.minecraft.client.Minecraft;
import net.minecraft.world.World;
import com.sakurakugu.autotorch.compat.BlockPos;
import com.sakurakugu.autotorch.compat.WorldAccess;
import net.minecraft.block.BlockLeaves;

/** 维护仅在客户端执行的光照风险扫描，以及供渲染使用的不可变快照。 */
public final class LightOverlayState {
    private static final int DOWN_RANGE = 16;
    private static final int UP_RANGE = 4;
    private static final int SCAN_BUDGET_PER_TICK = 12_000;
    private static final int HEIGHT = DOWN_RANGE + UP_RANGE + 1;

    private static boolean enabled = ClientConfig.isLightOverlayEnabled();
    private static boolean drownedDetectionEnabled = ClientConfig.detectsDrowned();
    private static DisplayMode displayMode = ClientConfig.showsLightOverlayNumbers()
            ? DisplayMode.NUMBERS : DisplayMode.CROSSES;
    private static int horizontalRange = ClientConfig.lightOverlayRange();
    private static int scanRange = horizontalRange;
    private static World level;
    private static BlockPos scanCenter;
    private static int scanIndex;
    private static int ticksSinceCompleted;
    private static List<Marker> workingMarkers = new ArrayList<>();
    private static List<Marker> markers = Collections.emptyList();

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
        setDisplayMode(displayMode == DisplayMode.CROSSES ? DisplayMode.NUMBERS : DisplayMode.CROSSES);
        return displayMode;
    }

    public static void setDisplayMode(DisplayMode value) {
        if (displayMode == value) {
            return;
        }
        displayMode = value;
        ClientConfig.setShowsLightOverlayNumbers(value == DisplayMode.NUMBERS);
    }

    public static boolean isSwampSlimeDetectionEnabled() {
        return false;
    }

    public static boolean toggleSwampSlimeDetection() {
        return false;
    }

    public static void setSwampSlimeDetectionEnabled(boolean value) {
        // 1.17.1 的客户端生物群系 API 无法可靠判断沼泽史莱姆生成条件。
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

    public static List<Marker> markers() {
        return markers;
    }

    public static void tick(Minecraft minecraft) {
        World currentLevel = minecraft.theWorld;
        if (currentLevel != level) {
            level = currentLevel;
            clearScan();
        }
        if (!enabled || currentLevel == null || minecraft.thePlayer == null) {
            return;
        }

        BlockPos playerPos = new BlockPos(minecraft.thePlayer);
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
            markers = Collections.unmodifiableList(new ArrayList<>(workingMarkers));
            ticksSinceCompleted = 0;
        }
    }

    private static void beginScan(BlockPos center) {
        scanCenter = center.getImmutable();
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
        markers = Collections.emptyList();
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

    private static Marker markerAt(World level, BlockPos feet) {
        if (!WorldAccess.exists(level, feet)) {
            return null;
        }
        // 1.7.10 没有溺尸；保留配置字段以兼容已有客户端配置，但不生成溺尸标记。
        if (WorldAccess.block(level, feet).getMaterial().isLiquid()
                || WorldAccess.block(level, feet.up()).getMaterial().isLiquid()) {
            return null;
        }

        if (WorldAccess.block(level, feet).getCollisionBoundingBoxFromPool(
                    level, feet.getX(), feet.getY(), feet.getZ()) != null
                || WorldAccess.block(level, feet.up()).getCollisionBoundingBoxFromPool(
                    level, feet.getX(), feet.getY() + 1, feet.getZ()) != null) {
            return null;
        }

        BlockPos floorPos = feet.down();
        if (WorldAccess.block(level, floorPos) instanceof BlockLeaves
                || !WorldAccess.isTopSolid(level, floorPos)) {
            return null;
        }
        int blockLight = WorldAccess.blockLight(level, feet);
        return new Marker(
                feet.getImmutable(),
                blockLight,
                WorldAccess.skyLight(level, feet),
                RiskType.NORMAL
        );
    }

    private static Marker marker(World level, BlockPos pos, RiskType riskType) {
        return new Marker(
                pos.getImmutable(),
                WorldAccess.blockLight(level, pos),
                WorldAccess.skyLight(level, pos),
                riskType
        );
    }

    public enum DisplayMode {
        CROSSES,
        NUMBERS
    }

    public enum RiskType {
        NORMAL,
        DROWNED
    }

    public static final class Marker {
        private final BlockPos pos;
        private final int blockLight;
        private final int skyLight;
        private final RiskType riskType;

        public Marker(BlockPos pos, int blockLight, int skyLight, RiskType riskType) {
            this.pos = pos;
            this.blockLight = blockLight;
            this.skyLight = skyLight;
            this.riskType = riskType;
        }

        public BlockPos pos() { return pos; }
        public int blockLight() { return blockLight; }
        public int skyLight() { return skyLight; }
        public RiskType riskType() { return riskType; }

        public boolean nightOnly() {
            return blockLight == 0 && skyLight > 0;
        }

        public boolean isRisk() {
            return blockLight == 0 || riskType != RiskType.NORMAL;
        }
    }
}
