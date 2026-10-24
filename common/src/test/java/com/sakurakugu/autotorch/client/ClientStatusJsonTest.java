package com.sakurakugu.autotorch.client;

import java.util.HashMap;
import java.util.Map;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sakurakugu.autotorch.config.ConfigDefinitions;
import com.sakurakugu.autotorch.network.ServerConfigPayload;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientStatusJsonTest {
    @Test
    void emitsSingleLineParsableJsonWithEveryGroup() {
        String json = ClientStatusJson.build(false, BlockPos.ORIGIN);

        assertFalse(json.contains("\n"), () -> "输出必须是单行：" + json);
        JsonObject root = new JsonParser().parse(json).getAsJsonObject();
        for (String group : new String[]{"nearby", "lightOverlay", "selection", "task", "server"}) {
            assertTrue(root.has(group), () -> "缺少分组：" + group);
        }

        JsonObject nearby = root.getAsJsonObject("nearby");
        assertEquals(4, nearby.get("lightThreshold").getAsInt());
        assertFalse(nearby.get("enabled").getAsBoolean());
        assertFalse(nearby.get("includeSkyLight").getAsBoolean());

        JsonObject overlay = root.getAsJsonObject("lightOverlay");
        assertEquals("crosses", overlay.get("displayMode").getAsString());
        assertEquals(16, overlay.get("horizontalRange").getAsInt());
        assertTrue(overlay.has("slime"));

        JsonObject selection = root.getAsJsonObject("selection");
        assertEquals("box", selection.get("shape").getAsString());
        assertEquals("faces", selection.get("displayMode").getAsString());
        assertEquals("blocky", selection.get("sphereDisplayMode").getAsString());
        assertTrue(selection.get("lightingZone").isJsonNull());
        assertEquals(0, selection.getAsJsonArray("exclusions").size());
        assertFalse(selection.has("radius"), "区间选区不应带半径");

        JsonObject task = root.getAsJsonObject("task");
        assertFalse(task.get("consumeTorches").getAsBoolean());
        assertEquals(8, task.get("minSpacing").getAsInt());
        // 区间任务的“包含天空光”使用正向布尔值，默认不包含。
        assertFalse(task.get("includeSkyLight").getAsBoolean());
        assertFalse(task.has("undergroundOnly"));
        assertTrue(task.has("selectionTool"));
        assertFalse(task.has("woodenAxeSelection"));

        assertTrue(root.getAsJsonObject("server").has("maxSphereRadius"));
    }

    @Test
    void clampsClientTaskDefaultsToServerLimits() {
        ServerConfigPayload defaults = ServerConfigPayload.defaults();
        try {
            ClientConfig.setDefaultMaxTorches(0);
            ClientConfig.setDefaultMinSpacing(1);
            // 服务端上限 256、间距范围 5~8，且禁止“不限”。
            ServerConfigState.update(payload(com.google.common.collect.ImmutableMap.of(
                    "limits.maxTorchesPerTask", "256",
                    "limits.allowUnlimitedTorches", "false",
                    "limits.minSpacing", "5",
                    "limits.maxSpacing", "8")));

            assertEquals(256, ClientStatusJson.effectiveDefaultMaxTorches());
            assertEquals(5, ClientStatusJson.effectiveDefaultMinSpacing());

            // 服务端允许“不限”时，客户端设为 0 才表示不限。
            ServerConfigState.update(payload(com.google.common.collect.ImmutableMap.of(
                    "limits.maxTorchesPerTask", "256",
                    "limits.allowUnlimitedTorches", "true",
                    "limits.minSpacing", "5",
                    "limits.maxSpacing", "8")));
            assertEquals(0, ClientStatusJson.effectiveDefaultMaxTorches());

            ClientConfig.setDefaultMaxTorches(1024);
            assertEquals(256, ClientStatusJson.effectiveDefaultMaxTorches());
            ClientConfig.setDefaultMinSpacing(12);
            assertEquals(8, ClientStatusJson.effectiveDefaultMinSpacing());
        } finally {
            ClientConfig.setDefaultMaxTorches(ConfigDefinitions.TASK_DEFAULT_MAX_TORCHES.defaultValue());
            ClientConfig.setDefaultMinSpacing(ConfigDefinitions.TASK_DEFAULT_MIN_SPACING.defaultValue());
            ServerConfigState.update(defaults);
        }
    }

    /** 在默认服务端配置的基础上覆盖指定键，构造模拟的服务端快照。 */
    private static ServerConfigPayload payload(Map<String, String> overrides) {
        Map<String, String> values = new HashMap<>(ServerConfigPayload.defaults().values());
        values.putAll(overrides);
        return new ServerConfigPayload(values);
    }

    @Test
    void omitsPlayerBoundGroupsWithoutPosition() {
        JsonObject root = new JsonParser().parse(ClientStatusJson.build(false, null)).getAsJsonObject();

        assertTrue(root.has("nearby"));
        assertTrue(root.has("lightOverlay"));
        assertTrue(root.has("server"));
        assertFalse(root.has("selection"));
        assertFalse(root.has("task"));
    }

    @Test
    void sphereSelectionCarriesRadius() {
        SelectionState.setShape(com.sakurakugu.autotorch.network.AreaShape.SPHERE);
        try {
            JsonObject selection = new JsonParser().parse(ClientStatusJson.build(false, BlockPos.ORIGIN))
                    .getAsJsonObject().getAsJsonObject("selection");

            assertEquals("sphere", selection.get("shape").getAsString());
            assertTrue(selection.has("radius"));
        } finally {
            SelectionState.setShape(com.sakurakugu.autotorch.network.AreaShape.BOX);
        }
    }
}
