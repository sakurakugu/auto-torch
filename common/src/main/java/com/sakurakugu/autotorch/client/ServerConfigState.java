package com.sakurakugu.autotorch.client;

import com.sakurakugu.autotorch.config.ConfigDefinitions;
import com.sakurakugu.autotorch.config.ConfigDefinitions.BooleanValue;
import com.sakurakugu.autotorch.config.ConfigDefinitions.DoubleValue;
import com.sakurakugu.autotorch.config.ConfigDefinitions.IntValue;
import com.sakurakugu.autotorch.config.ConfigDefinitions.StringValue;
import com.sakurakugu.autotorch.config.ConfigDefinitions.Value;
import com.sakurakugu.autotorch.network.ServerConfigPayload;

/** 客户端保存的最近一次服务端权威配置，仅用于界面显示。 */
public final class ServerConfigState {
    private static volatile ServerConfigPayload config = ServerConfigPayload.defaults();
    /** 每次服务端同步自增，界面据此刷新滑动条等控件。 */
    private static volatile long revision;

    private ServerConfigState() {
    }

    public static boolean survivalConsumesTorches() {
        return booleanValue(ConfigDefinitions.GAMEPLAY_SURVIVAL_CONSUMES_TORCHES);
    }

    public static boolean lightingTaskEnabled() {
        return booleanValue(ConfigDefinitions.LIGHTING_TASK_ENABLED);
    }

    public static int maxBoxAxisLength() { return intValue(ConfigDefinitions.LIMIT_MAX_BOX_AXIS_LENGTH); }
    public static int maxSphereRadius() { return intValue(ConfigDefinitions.LIMIT_MAX_SPHERE_RADIUS); }
    public static int maxExclusions() { return intValue(ConfigDefinitions.LIMIT_MAX_EXCLUSIONS); }
    public static int maxTorchesPerTask() { return intValue(ConfigDefinitions.LIMIT_MAX_TORCHES_PER_TASK); }
    public static boolean allowsUnlimitedTorches() { return booleanValue(ConfigDefinitions.LIMIT_ALLOW_UNLIMITED_TORCHES); }
    public static int minSpacing() { return intValue(ConfigDefinitions.LIMIT_MIN_SPACING); }
    public static int maxSpacing() { return intValue(ConfigDefinitions.LIMIT_MAX_SPACING); }

    public static boolean booleanValue(BooleanValue definition) {
        return value(definition) instanceof Boolean result ? result : definition.defaultValue();
    }

    public static int intValue(IntValue definition) {
        return value(definition) instanceof Integer result ? result : definition.defaultValue();
    }

    /** 服务端配置的当前生效值；缺失或格式异常时退回默认值，避免旧服务端导致界面崩溃。 */
    public static Object value(Value definition) {
        String raw = config.values().get(definition.key());
        if (raw == null) return defaultValue(definition);
        try {
            if (definition instanceof BooleanValue) return Boolean.parseBoolean(raw);
            if (definition instanceof IntValue) return Integer.parseInt(raw);
            if (definition instanceof DoubleValue) return Double.parseDouble(raw);
            if (definition instanceof StringValue) return raw;
        } catch (NumberFormatException ignored) {
            // 服务端文本异常时按默认值显示。
        }
        return defaultValue(definition);
    }

    public static long revision() {
        return revision;
    }

    public static void update(ServerConfigPayload value) {
        config = value;
        revision++;
    }

    private static Object defaultValue(Value definition) {
        if (definition instanceof BooleanValue value) return value.defaultValue();
        if (definition instanceof IntValue value) return value.defaultValue();
        if (definition instanceof DoubleValue value) return value.defaultValue();
        if (definition instanceof StringValue value) return value.defaultValue();
        throw new IllegalArgumentException("Unsupported server setting: " + definition.key());
    }
}
