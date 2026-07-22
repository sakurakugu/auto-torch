package com.sakurakugu.autotorch.server;

import com.sakurakugu.autotorch.config.ConfigBackend;
import com.sakurakugu.autotorch.network.AreaZone;
import com.sakurakugu.autotorch.network.StartLightingPayload;

/** Loader-neutral facade for server-authoritative limits and budgets. */
public final class ServerConfig {
    public static final int HARD_MAX_BOX_AXIS_LENGTH = 256;
    public static final int HARD_MAX_TORCHES = 4096;
    private static ConfigBackend backend = new DefaultsBackend();

    private ServerConfig() {
    }

    public static void install(ConfigBackend value) { backend = value; }
    public static int maxBoxAxisLength() { return clamp(integer("limits.maxBoxAxisLength", HARD_MAX_BOX_AXIS_LENGTH), 1, HARD_MAX_BOX_AXIS_LENGTH); }
    public static int maxSphereRadius() { return clamp(integer("limits.maxSphereRadius", AreaZone.MAX_SPHERE_RADIUS), 1, AreaZone.MAX_SPHERE_RADIUS); }
    public static int maxExclusions() { return clamp(integer("limits.maxExclusions", StartLightingPayload.MAX_EXCLUSIONS), 0, StartLightingPayload.MAX_EXCLUSIONS); }
    public static int maxTorchesPerTask() { return clamp(integer("limits.maxTorchesPerTask", HARD_MAX_TORCHES), 1, HARD_MAX_TORCHES); }
    public static boolean allowsUnlimitedTorches() { return bool("limits.allowUnlimitedTorches", true); }
    public static int minSpacing() { return Math.min(spacing("limits.minSpacing", 3), spacing("limits.maxSpacing", 12)); }
    public static int maxSpacing() { return Math.max(spacing("limits.minSpacing", 3), spacing("limits.maxSpacing", 12)); }
    public static int maxConcurrentTasks() { return clamp(integer("limits.maxConcurrentTasks", 64), 1, 1024); }
    public static boolean survivalConsumesTorches() { return bool("gameplay.survivalConsumesTorches", true); }
    public static int scanBudgetPerTaskTick() { return clamp(integer("performance.scanBudgetPerTaskTick", 12_000), 1, 120_000); }
    public static int placeBudgetPerTaskTick() { return clamp(integer("performance.placeBudgetPerTaskTick", 8), 1, 64); }
    public static int globalScanBudgetPerTick() { return clamp(integer("performance.globalScanBudgetPerTick", 24_000), 1, 240_000); }
    public static int globalPlaceBudgetPerTick() { return clamp(integer("performance.globalPlaceBudgetPerTick", 16), 1, 256); }
    public static int randomPlacementAttempts() { return clamp(integer("performance.randomPlacementAttempts", 32), 1, 128); }

    private static boolean bool(String key, boolean fallback) { return backend.getBoolean(key, fallback); }
    private static int integer(String key, int fallback) { return backend.getInt(key, fallback); }
    private static int spacing(String key, int fallback) { return clamp(integer(key, fallback), 3, 12); }
    private static int clamp(int value, int min, int max) { return Math.max(min, Math.min(max, value)); }

    private static final class DefaultsBackend implements ConfigBackend {
        public boolean getBoolean(String key, boolean fallback) { return fallback; }
        public int getInt(String key, int fallback) { return fallback; }
        public void setBoolean(String key, boolean value) { }
        public void setInt(String key, int value) { }
        public void save() { }
    }
}
