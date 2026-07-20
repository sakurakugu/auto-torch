package dev.sakurakugu.autotorch.server;

import dev.sakurakugu.autotorch.network.AreaZone;
import dev.sakurakugu.autotorch.network.StartLightingPayload;
import net.neoforged.neoforge.common.ModConfigSpec;

/** 由服务端权威控制的区域照明任务限制与性能预算。 */
public final class ServerConfig {
    public static final int HARD_MAX_BOX_AXIS_LENGTH = 256;
    public static final int HARD_MAX_TORCHES = 4096;
    public static final ModConfigSpec SPEC;

    private static final ModConfigSpec.IntValue MAX_BOX_AXIS_LENGTH;
    private static final ModConfigSpec.IntValue MAX_SPHERE_RADIUS;
    private static final ModConfigSpec.IntValue MAX_EXCLUSIONS;
    private static final ModConfigSpec.IntValue MAX_TORCHES_PER_TASK;
    private static final ModConfigSpec.BooleanValue ALLOW_UNLIMITED_TORCHES;
    private static final ModConfigSpec.IntValue MIN_SPACING;
    private static final ModConfigSpec.IntValue MAX_SPACING;
    private static final ModConfigSpec.IntValue MAX_CONCURRENT_TASKS;
    private static final ModConfigSpec.IntValue SCAN_BUDGET_PER_TASK_TICK;
    private static final ModConfigSpec.IntValue PLACE_BUDGET_PER_TASK_TICK;
    private static final ModConfigSpec.IntValue GLOBAL_SCAN_BUDGET_PER_TICK;
    private static final ModConfigSpec.IntValue GLOBAL_PLACE_BUDGET_PER_TICK;
    private static final ModConfigSpec.IntValue RANDOM_PLACEMENT_ATTEMPTS;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.push("limits");
        MAX_BOX_AXIS_LENGTH = builder.defineInRange(
                "maxBoxAxisLength", HARD_MAX_BOX_AXIS_LENGTH, 1, HARD_MAX_BOX_AXIS_LENGTH);
        MAX_SPHERE_RADIUS = builder.defineInRange(
                "maxSphereRadius", AreaZone.MAX_SPHERE_RADIUS, 1, AreaZone.MAX_SPHERE_RADIUS);
        MAX_EXCLUSIONS = builder.defineInRange(
                "maxExclusions", StartLightingPayload.MAX_EXCLUSIONS, 0, StartLightingPayload.MAX_EXCLUSIONS);
        MAX_TORCHES_PER_TASK = builder.defineInRange(
                "maxTorchesPerTask", HARD_MAX_TORCHES, 1, HARD_MAX_TORCHES);
        ALLOW_UNLIMITED_TORCHES = builder.define("allowUnlimitedTorches", true);
        MIN_SPACING = builder.defineInRange("minSpacing", 3, 3, 12);
        MAX_SPACING = builder.defineInRange("maxSpacing", 12, 3, 12);
        MAX_CONCURRENT_TASKS = builder.defineInRange("maxConcurrentTasks", 64, 1, 1024);
        builder.pop();

        builder.push("performance");
        SCAN_BUDGET_PER_TASK_TICK = builder
                .comment("Maximum positions scanned by each active task per server tick.")
                .defineInRange("scanBudgetPerTaskTick", 12_000, 1, 120_000);
        PLACE_BUDGET_PER_TASK_TICK = builder
                .comment("Maximum torches placed by each active task per server tick.")
                .defineInRange("placeBudgetPerTaskTick", 8, 1, 64);
        GLOBAL_SCAN_BUDGET_PER_TICK = builder
                .comment("Maximum positions scanned by all active tasks per server tick.")
                .defineInRange("globalScanBudgetPerTick", 24_000, 1, 240_000);
        GLOBAL_PLACE_BUDGET_PER_TICK = builder
                .comment("Maximum torches placed by all active tasks per server tick.")
                .defineInRange("globalPlaceBudgetPerTick", 16, 1, 256);
        RANDOM_PLACEMENT_ATTEMPTS = builder.defineInRange("randomPlacementAttempts", 32, 1, 128);
        builder.pop();
        SPEC = builder.build();
    }

    private ServerConfig() {
    }

    public static int maxBoxAxisLength() { return MAX_BOX_AXIS_LENGTH.get(); }
    public static int maxSphereRadius() { return MAX_SPHERE_RADIUS.get(); }
    public static int maxExclusions() { return MAX_EXCLUSIONS.get(); }
    public static int maxTorchesPerTask() { return MAX_TORCHES_PER_TASK.get(); }
    public static boolean allowsUnlimitedTorches() { return ALLOW_UNLIMITED_TORCHES.get(); }
    public static int minSpacing() { return Math.min(MIN_SPACING.get(), MAX_SPACING.get()); }
    public static int maxSpacing() { return Math.max(MIN_SPACING.get(), MAX_SPACING.get()); }
    public static int maxConcurrentTasks() { return MAX_CONCURRENT_TASKS.get(); }
    public static int scanBudgetPerTaskTick() { return SCAN_BUDGET_PER_TASK_TICK.get(); }
    public static int placeBudgetPerTaskTick() { return PLACE_BUDGET_PER_TASK_TICK.get(); }
    public static int globalScanBudgetPerTick() { return GLOBAL_SCAN_BUDGET_PER_TICK.get(); }
    public static int globalPlaceBudgetPerTick() { return GLOBAL_PLACE_BUDGET_PER_TICK.get(); }
    public static int randomPlacementAttempts() { return RANDOM_PLACEMENT_ATTEMPTS.get(); }
}
