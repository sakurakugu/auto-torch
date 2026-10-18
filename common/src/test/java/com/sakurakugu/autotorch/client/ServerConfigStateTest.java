package com.sakurakugu.autotorch.client;

import java.util.HashMap;
import java.util.Map;

import com.sakurakugu.autotorch.config.ConfigDefinitions;
import com.sakurakugu.autotorch.network.ServerConfigPayload;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/** 客户端从服务端快照读取配置时，需要兼顾类型解析与异常值回退。 */
class ServerConfigStateTest {
    @Test
    void readsTypedValuesFromServerSnapshot() {
        ServerConfigPayload defaults = ServerConfigPayload.defaults();
        try {
            Map<String, String> values = new HashMap<>(defaults.values());
            values.put("limits.maxSphereRadius", "80");
            values.put("lightingTask.enabled", "false");
            ServerConfigState.update(new ServerConfigPayload(values));

            assertEquals(80, ServerConfigState.maxSphereRadius());
            assertFalse(ServerConfigState.lightingTaskEnabled());
        } finally {
            ServerConfigState.update(defaults);
        }
    }

    @Test
    void fallsBackToDefaultsForMissingOrInvalidValues() {
        ServerConfigPayload defaults = ServerConfigPayload.defaults();
        try {
            ServerConfigState.update(new ServerConfigPayload(Map.of(
                    "limits.maxSphereRadius", "not-a-number",
                    "lightingTask.enabled", "false")));

            // 缺失的键与无法解析的数值都退回默认值，避免旧服务端或损坏数据让界面崩溃。
            assertEquals(ConfigDefinitions.LIMIT_MAX_SPHERE_RADIUS.defaultValue(),
                    ServerConfigState.maxSphereRadius());
            assertEquals(ConfigDefinitions.LIMIT_MAX_TORCHES_PER_TASK.defaultValue(),
                    ServerConfigState.maxTorchesPerTask());
            assertFalse(ServerConfigState.lightingTaskEnabled());
        } finally {
            ServerConfigState.update(defaults);
        }
    }
}
