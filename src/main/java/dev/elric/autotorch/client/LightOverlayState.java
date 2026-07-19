package dev.sakurakugu.autotorch.client;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/** Maintains the client-only light-risk scan and an immutable snapshot for rendering. */
public final class LightOverlayState {
    private static final int HORIZONTAL_RANGE = 16;
    private static final int DOWN_RANGE = 16;
    private static final int UP_RANGE = 4;
    private static final int SCAN_BUDGET_PER_TICK = 4_096;
    private static final int REFRESH_INTERVAL_TICKS = 20;
    private static final int DIAMETER = HORIZONTAL_RANGE * 2 + 1;
    private static final int HEIGHT = DOWN_RANGE + UP_RANGE + 1;
    private static final int SCAN_VOLUME = DIAMETER * DIAMETER * HEIGHT;

    private static boolean enabled;
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
        if (!enabled) {
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
        } else if (scanIndex >= SCAN_VOLUME) {
            ticksSinceCompleted++;
            if (ticksSinceCompleted >= REFRESH_INTERVAL_TICKS || movedOutsideRefreshArea(playerPos, scanCenter)) {
                beginScan(playerPos);
            } else {
                return;
            }
        }

        int end = Math.min(SCAN_VOLUME, scanIndex + SCAN_BUDGET_PER_TICK);
        BlockPos.MutableBlockPos feet = new BlockPos.MutableBlockPos();
        while (scanIndex < end) {
            setPositionForIndex(feet, scanCenter, scanIndex++);
            Marker marker = markerAt(currentLevel, feet);
            if (marker != null) {
                workingMarkers.add(marker);
            }
        }
        if (scanIndex >= SCAN_VOLUME) {
            markers = List.copyOf(workingMarkers);
            ticksSinceCompleted = 0;
        }
    }

    private static void beginScan(BlockPos center) {
        scanCenter = center.immutable();
        scanIndex = 0;
        ticksSinceCompleted = 0;
        workingMarkers = new ArrayList<>();
    }

    private static void clearScan() {
        scanCenter = null;
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
        int xOffset = index % DIAMETER - HORIZONTAL_RANGE;
        index /= DIAMETER;
        int zOffset = index % DIAMETER - HORIZONTAL_RANGE;
        int yOffset = index / DIAMETER - DOWN_RANGE;
        pos.set(center.getX() + xOffset, center.getY() + yOffset, center.getZ() + zOffset);
    }

    private static @Nullable Marker markerAt(ClientLevel level, BlockPos feet) {
        if (!level.hasChunk(SectionPos.blockToSectionCoord(feet.getX()),
                SectionPos.blockToSectionCoord(feet.getZ()))) {
            return null;
        }
        if (level.getBrightness(LightLayer.BLOCK, feet) > 0
                || !level.getFluidState(feet).isEmpty()
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
        return new Marker(feet.immutable(), level.getBrightness(LightLayer.SKY, feet) > 0);
    }

    public record Marker(BlockPos pos, boolean nightOnly) {
    }
}
