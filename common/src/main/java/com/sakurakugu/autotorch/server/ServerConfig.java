package com.sakurakugu.autotorch.server;

import java.util.List;

import com.sakurakugu.autotorch.config.ConfigBackend;
import com.sakurakugu.autotorch.config.ConfigDefinitions;
import com.sakurakugu.autotorch.config.ConfigDefinitions.BooleanValue;
import com.sakurakugu.autotorch.config.ConfigDefinitions.IntValue;
import com.sakurakugu.autotorch.config.ConfigDefinitions.Value;

import static com.sakurakugu.autotorch.config.ConfigDefinitions.*;

/** Loader-neutral facade for server-authoritative limits and budgets. */
public final class ServerConfig {
    private static ConfigBackend backend = new DefaultsBackend();

    private ServerConfig() {
    }

    public static void install(ConfigBackend value) { backend = value; }
    public static List<ConfigDefinitions.Value> definitions() { return SERVER; }
    public static ConfigDefinitions.Value definition(String key) {
        return SERVER.stream().filter(value -> value.key().equals(key)).findFirst().orElse(null);
    }
    public static Object get(String key) {
        Value definition = definition(key);
        if (definition instanceof BooleanValue) return bool((BooleanValue) definition);
        if (definition instanceof IntValue) return integer((IntValue) definition);
        return null;
    }
    public static boolean set(String key, Object value) {
        Value definition = definition(key);
        if (definition instanceof BooleanValue && value instanceof Boolean) {
            backend.setBoolean(definition.key(), (Boolean) value);
        } else if (definition instanceof IntValue && value instanceof Integer) {
            IntValue intValue = (IntValue) definition;
            backend.setInt(intValue.key(), intValue.clamp((Integer) value));
        } else return false;
        backend.save();
        return true;
    }
    /** 恢复全部服务端配置并立即持久化。 */
    public static void resetDefaults() {
        for (Value definition : SERVER) {
            if (definition instanceof BooleanValue) {
                BooleanValue value = (BooleanValue) definition;
                backend.setBoolean(value.key(), value.defaultValue());
            } else if (definition instanceof IntValue) {
                IntValue value = (IntValue) definition;
                backend.setInt(value.key(), value.defaultValue());
            }
        }
        backend.save();
    }
    public static int maxBoxAxisLength() { return integer(LIMIT_MAX_BOX_AXIS_LENGTH); }
    public static int maxSphereRadius() { return integer(LIMIT_MAX_SPHERE_RADIUS); }
    public static int maxExclusions() { return integer(LIMIT_MAX_EXCLUSIONS); }
    public static int maxTorchesPerTask() { return integer(LIMIT_MAX_TORCHES_PER_TASK); }
    public static boolean allowsUnlimitedTorches() { return bool(LIMIT_ALLOW_UNLIMITED_TORCHES); }
    public static boolean lightingTaskEnabled() { return bool(LIGHTING_TASK_ENABLED); }
    public static int minSpacing() { return Math.min(integer(LIMIT_MIN_SPACING), integer(LIMIT_MAX_SPACING)); }
    public static int maxSpacing() { return Math.max(integer(LIMIT_MIN_SPACING), integer(LIMIT_MAX_SPACING)); }
    public static int maxConcurrentTasks() { return integer(LIMIT_MAX_CONCURRENT_TASKS); }
    public static boolean survivalConsumesTorches() { return bool(GAMEPLAY_SURVIVAL_CONSUMES_TORCHES); }
    public static int scanBudgetPerTaskTick() { return integer(PERFORMANCE_SCAN_BUDGET_PER_TASK_TICK); }
    public static int placeBudgetPerTaskTick() { return integer(PERFORMANCE_PLACE_BUDGET_PER_TASK_TICK); }
    public static int globalScanBudgetPerTick() { return integer(PERFORMANCE_GLOBAL_SCAN_BUDGET_PER_TICK); }
    public static int globalPlaceBudgetPerTick() { return integer(PERFORMANCE_GLOBAL_PLACE_BUDGET_PER_TICK); }
    public static int randomPlacementAttempts() { return integer(PERFORMANCE_RANDOM_PLACEMENT_ATTEMPTS); }

    private static boolean bool(BooleanValue definition) {
        return backend.getBoolean(definition.key(), definition.defaultValue());
    }

    private static int integer(IntValue definition) {
        return definition.clamp(backend.getInt(definition.key(), definition.defaultValue()));
    }

    private static final class DefaultsBackend implements ConfigBackend {
        public boolean getBoolean(String key, boolean fallback) { return fallback; }
        public int getInt(String key, int fallback) { return fallback; }
        public void setBoolean(String key, boolean value) { }
        public void setInt(String key, int value) { }
        public void save() { }
    }
}
