package com.sakurakugu.autotorch.config;

import java.util.List;

import com.sakurakugu.autotorch.network.AreaZone;
import com.sakurakugu.autotorch.network.StartLightingPayload;

/** Loader-neutral catalog of configuration keys, defaults, and numeric bounds. */
public final class ConfigDefinitions {
    public static final int HARD_MAX_BOX_AXIS_LENGTH = 257;
    public static final int HARD_MAX_TORCHES = 4096;

    public static final BooleanValue NEARBY_AUTO_TORCH_ENABLED = bool("nearbyAutoTorch.enabled", false);
    public static final IntValue NEARBY_AUTO_TORCH_LIGHT_THRESHOLD = integer("nearbyAutoTorch.lightThreshold", 4, 1, 16);
    public static final BooleanValue NEARBY_AUTO_TORCH_INCLUDE_SKY_LIGHT = bool("nearbyAutoTorch.includeSkyLight", true);
    public static final BooleanValue LIGHT_OVERLAY_ENABLED = bool("lightOverlay.enabled", false);
    public static final IntValue LIGHT_OVERLAY_HORIZONTAL_RANGE = integer("lightOverlay.horizontalRange", 16, 1, 64);
    public static final BooleanValue LIGHT_OVERLAY_SHOW_NUMBERS = bool("lightOverlay.showNumbers", false);
    public static final BooleanValue LIGHT_OVERLAY_DETECT_SWAMP_SLIMES = bool("lightOverlay.detectSwampSlimes", false);
    public static final BooleanValue LIGHT_OVERLAY_DETECT_DROWNED = bool("lightOverlay.detectDrowned", false);
    public static final BooleanValue SELECTION_OVERLAY_ENABLED = bool("selectionOverlay.enabled", true);
    public static final BooleanValue SELECTION_OVERLAY_LINES_ONLY = bool("selectionOverlay.linesOnly", false);
    public static final BooleanValue SELECTION_OVERLAY_SMOOTH_SPHERES = bool("selectionOverlay.smoothSpheres", false);
    public static final IntValue TASK_DEFAULT_MAX_TORCHES = integer("lightingTaskDefaults.maxTorches", 0, 0, HARD_MAX_TORCHES);
    public static final IntValue TASK_DEFAULT_MIN_SPACING = integer("lightingTaskDefaults.minSpacing", 8, 3, 12);
    public static final BooleanValue TASK_DEFAULT_UNDERGROUND_ONLY = bool("lightingTaskDefaults.undergroundOnly", true);
    public static final BooleanValue TASK_DEFAULT_CREATIVE_CONSUMES_TORCHES = bool("lightingTaskDefaults.creativeConsumeTorches", false);
    public static final BooleanValue TASK_DEFAULT_SURVIVAL_CONSUMES_TORCHES = bool("lightingTaskDefaults.survivalConsumeTorches", true);
    public static final BooleanValue TASK_DEFAULT_WOODEN_AXE_SELECTION_ENABLED = bool("lightingTaskDefaults.woodenAxeSelectionEnabled", true);

    public static final IntValue LIMIT_MAX_BOX_AXIS_LENGTH = integer("limits.maxBoxAxisLength", HARD_MAX_BOX_AXIS_LENGTH, 1, HARD_MAX_BOX_AXIS_LENGTH);
    public static final IntValue LIMIT_MAX_SPHERE_RADIUS = integer("limits.maxSphereRadius", AreaZone.MAX_SPHERE_RADIUS, 1, AreaZone.MAX_SPHERE_RADIUS);
    public static final IntValue LIMIT_MAX_EXCLUSIONS = integer("limits.maxExclusions", StartLightingPayload.MAX_EXCLUSIONS, 0, StartLightingPayload.MAX_EXCLUSIONS);
    public static final IntValue LIMIT_MAX_TORCHES_PER_TASK = integer("limits.maxTorchesPerTask", HARD_MAX_TORCHES, 1, HARD_MAX_TORCHES);
    public static final BooleanValue LIMIT_ALLOW_UNLIMITED_TORCHES = bool("limits.allowUnlimitedTorches", true);
    public static final IntValue LIMIT_MIN_SPACING = integer("limits.minSpacing", 3, 3, 12);
    public static final IntValue LIMIT_MAX_SPACING = integer("limits.maxSpacing", 12, 3, 12);
    public static final IntValue LIMIT_MAX_CONCURRENT_TASKS = integer("limits.maxConcurrentTasks", 64, 1, 1024);
    public static final BooleanValue GAMEPLAY_SURVIVAL_CONSUMES_TORCHES = bool("gameplay.survivalConsumesTorches", true);
    public static final IntValue PERFORMANCE_SCAN_BUDGET_PER_TASK_TICK = integer("performance.scanBudgetPerTaskTick", 12_000, 1, 120_000);
    public static final IntValue PERFORMANCE_PLACE_BUDGET_PER_TASK_TICK = integer("performance.placeBudgetPerTaskTick", 8, 1, 64);
    public static final IntValue PERFORMANCE_GLOBAL_SCAN_BUDGET_PER_TICK = integer("performance.globalScanBudgetPerTick", 24_000, 1, 240_000);
    public static final IntValue PERFORMANCE_GLOBAL_PLACE_BUDGET_PER_TICK = integer("performance.globalPlaceBudgetPerTick", 16, 1, 256);
    public static final IntValue PERFORMANCE_RANDOM_PLACEMENT_ATTEMPTS = integer("performance.randomPlacementAttempts", 32, 1, 128);

    public static final List<Value> CLIENT = List.of(
            NEARBY_AUTO_TORCH_ENABLED, NEARBY_AUTO_TORCH_LIGHT_THRESHOLD,
            NEARBY_AUTO_TORCH_INCLUDE_SKY_LIGHT, LIGHT_OVERLAY_ENABLED,
            LIGHT_OVERLAY_HORIZONTAL_RANGE, LIGHT_OVERLAY_SHOW_NUMBERS,
            LIGHT_OVERLAY_DETECT_SWAMP_SLIMES, LIGHT_OVERLAY_DETECT_DROWNED,
            SELECTION_OVERLAY_ENABLED, SELECTION_OVERLAY_LINES_ONLY,
            SELECTION_OVERLAY_SMOOTH_SPHERES, TASK_DEFAULT_MAX_TORCHES,
            TASK_DEFAULT_MIN_SPACING, TASK_DEFAULT_UNDERGROUND_ONLY,
            TASK_DEFAULT_CREATIVE_CONSUMES_TORCHES, TASK_DEFAULT_SURVIVAL_CONSUMES_TORCHES,
            TASK_DEFAULT_WOODEN_AXE_SELECTION_ENABLED
    );

    public static final List<Value> SERVER = List.of(
            LIMIT_MAX_BOX_AXIS_LENGTH, LIMIT_MAX_SPHERE_RADIUS, LIMIT_MAX_EXCLUSIONS,
            LIMIT_MAX_TORCHES_PER_TASK, LIMIT_ALLOW_UNLIMITED_TORCHES,
            LIMIT_MIN_SPACING, LIMIT_MAX_SPACING, LIMIT_MAX_CONCURRENT_TASKS,
            GAMEPLAY_SURVIVAL_CONSUMES_TORCHES, PERFORMANCE_SCAN_BUDGET_PER_TASK_TICK,
            PERFORMANCE_PLACE_BUDGET_PER_TASK_TICK, PERFORMANCE_GLOBAL_SCAN_BUDGET_PER_TICK,
            PERFORMANCE_GLOBAL_PLACE_BUDGET_PER_TICK, PERFORMANCE_RANDOM_PLACEMENT_ATTEMPTS
    );

    private ConfigDefinitions() {
    }

    private static BooleanValue bool(String key, boolean defaultValue) {
        return new BooleanValue(key, defaultValue);
    }

    private static IntValue integer(String key, int defaultValue, int minValue, int maxValue) {
        return new IntValue(key, defaultValue, minValue, maxValue);
    }

    public sealed interface Value permits BooleanValue, IntValue {
        String key();
    }

    public record BooleanValue(String key, boolean defaultValue) implements Value {
    }

    public record IntValue(String key, int defaultValue, int minValue, int maxValue) implements Value {
        public IntValue {
            if (minValue > defaultValue || defaultValue > maxValue) {
                throw new IllegalArgumentException("Default value must be within the configured range: " + key);
            }
        }

        public int clamp(int value) {
            return Math.max(minValue, Math.min(maxValue, value));
        }
    }
}
