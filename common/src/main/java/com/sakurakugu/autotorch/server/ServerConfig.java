package com.sakurakugu.autotorch.server;

import java.util.List;

import com.sakurakugu.autotorch.config.ConfigBackend;
import com.sakurakugu.autotorch.config.ConfigDefinitions;
import com.sakurakugu.autotorch.config.ConfigDefinitions.BooleanValue;
import com.sakurakugu.autotorch.config.ConfigDefinitions.IntValue;
import com.sakurakugu.autotorch.config.ConfigDefinitions.ServerCategory;

import static com.sakurakugu.autotorch.config.ConfigDefinitions.*;

/** Loader-neutral facade for server-authoritative limits and budgets. */
public final class ServerConfig {
    private static ConfigBackend backend = new DefaultsBackend();

    private ServerConfig() {
    }

    /** 设置结果状态：命令层与管理员界面共用同一份校验逻辑。 */
    public enum SetStatus {
        OK,
        UNKNOWN_KEY,
        UNSUPPORTED_TYPE,
        INVALID_VALUE,
        OUT_OF_RANGE,
        SPACING_ORDER
    }

    /** 设置结果：成功时带实际生效值，供命令回显。 */
    public record SetResult(SetStatus status, Object value) {
        public boolean ok() { return status == SetStatus.OK; }
    }

    public static void install(ConfigBackend value) { backend = value; }
    public static List<ConfigDefinitions.Value> definitions() { return SERVER; }
    public static ConfigDefinitions.Value definition(String key) {
        return SERVER.stream().filter(value -> value.key().equals(key)).findFirst().orElse(null);
    }
    public static Object get(String key) {
        // 间距两项在读取时按较小值/较大值归一，get 也返回实际生效的上下限。
        if (LIMIT_MIN_SPACING.key().equals(key)) return minSpacing();
        if (LIMIT_MAX_SPACING.key().equals(key)) return maxSpacing();
        var definition = definition(key);
        if (definition instanceof BooleanValue value) return bool(value);
        if (definition instanceof IntValue value) return integer(value);
        return null;
    }
    public static boolean set(String key, Object value) {
        var definition = definition(key);
        if (definition instanceof BooleanValue booleanValue && value instanceof Boolean booleanResult) {
            backend.setBoolean(booleanValue.key(), booleanResult);
        } else if (definition instanceof IntValue intValue && value instanceof Integer integerResult) {
            int newValue = intValue.clamp(integerResult);
            if (definition == LIMIT_MIN_SPACING || definition == LIMIT_MAX_SPACING) {
                int currentMin = minSpacing();
                int currentMax = maxSpacing();
                // 间距上下限的跨项约束也在门面层兜底，避免以后的界面绕过命令层的校验。
                if (definition == LIMIT_MIN_SPACING && !minSpacingChangeAllowed(newValue, currentMax)) return false;
                if (definition == LIMIT_MAX_SPACING && !maxSpacingChangeAllowed(newValue, currentMin)) return false;
                // 顺手把两项按上下限排序写回，修正手改 TOML 造成的反序。
                backend.setInt(LIMIT_MIN_SPACING.key(), definition == LIMIT_MIN_SPACING ? newValue : currentMin);
                backend.setInt(LIMIT_MAX_SPACING.key(), definition == LIMIT_MAX_SPACING ? newValue : currentMax);
            } else {
                backend.setInt(intValue.key(), newValue);
            }
        } else return false;
        backend.save();
        return true;
    }
    /**
     * 按文本校验并写入服务端配置，供管理员命令与设置界面共用。
     * 校验顺序与命令层一致：未知键、类型、取值，最后才是间距交叉约束。
     */
    public static SetResult setFromText(String key, String rawValue) {
        ConfigDefinitions.Value definition = definition(key);
        if (definition == null) return new SetResult(SetStatus.UNKNOWN_KEY, null);
        Object value;
        if (definition instanceof BooleanValue) {
            if (!rawValue.equalsIgnoreCase("true") && !rawValue.equalsIgnoreCase("false")) {
                return new SetResult(SetStatus.INVALID_VALUE, null);
            }
            value = Boolean.parseBoolean(rawValue);
        } else if (definition instanceof IntValue intValue) {
            try {
                value = Integer.parseInt(rawValue);
            } catch (NumberFormatException exception) {
                return new SetResult(SetStatus.INVALID_VALUE, null);
            }
            if ((Integer) value < intValue.minValue() || (Integer) value > intValue.maxValue()) {
                return new SetResult(SetStatus.OUT_OF_RANGE, null);
            }
        } else {
            return new SetResult(SetStatus.UNSUPPORTED_TYPE, null);
        }
        if (!set(key, value)) return new SetResult(SetStatus.SPACING_ORDER, null);
        return new SetResult(SetStatus.OK, get(key));
    }

    /** 恢复全部服务端配置并立即持久化。 */
    public static void resetDefaults() {
        reset(SERVER);
    }

    /** 恢复一个分组内的服务端配置。 */
    public static void resetDefaults(ServerCategory category) {
        reset(category.definitions());
    }

    /** 恢复单个服务端配置项。 */
    public static void resetDefaults(ConfigDefinitions.Value definition) {
        if (!SERVER.contains(definition)) throw new IllegalArgumentException("Unknown server setting");
        reset(List.of(definition));
    }

    /**
     * 按范围恢复默认值：空字符串表示全部，分组枚举名表示一个分组，
     * 配置键表示单项，其余按 TOML 配置节前缀匹配；未知范围返回 false。
     */
    public static boolean resetScope(String scope) {
        if (scope == null || scope.isEmpty()) {
            resetDefaults();
            return true;
        }
        ServerCategory category = ServerCategory.byName(scope);
        if (category != null) {
            resetDefaults(category);
            return true;
        }
        ConfigDefinitions.Value definition = definition(scope);
        if (definition != null) {
            resetDefaults(definition);
            return true;
        }
        List<ConfigDefinitions.Value> section = SERVER.stream()
                .filter(value -> value.key().startsWith(scope + ".")).toList();
        if (section.isEmpty()) return false;
        reset(section);
        return true;
    }

    private static void reset(List<ConfigDefinitions.Value> definitions) {
        for (var definition : definitions) {
            if (definition instanceof BooleanValue value) backend.setBoolean(value.key(), value.defaultValue());
            else if (definition instanceof IntValue value) backend.setInt(value.key(), value.defaultValue());
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

    /**
     * 修改间距下限后不能超过当前上限。间距是两项独立配置，命令层用它拒绝交叉修改；
     * 读取阶段仍由 {@link #minSpacing()}、{@link #maxSpacing()} 兼容手改 TOML 造成的反序。
     */
    public static boolean minSpacingChangeAllowed(int value, int currentMaxSpacing) {
        return value <= currentMaxSpacing;
    }

    /** 修改间距上限后不能低于当前下限。 */
    public static boolean maxSpacingChangeAllowed(int value, int currentMinSpacing) {
        return value >= currentMinSpacing;
    }

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
        public double getDouble(String key, double fallback) { return fallback; }
        public String getString(String key, String fallback) { return fallback; }
        public void setBoolean(String key, boolean value) { }
        public void setInt(String key, int value) { }
        public void setDouble(String key, double value) { }
        public void setString(String key, String value) { }
        public void save() { }
    }
}

