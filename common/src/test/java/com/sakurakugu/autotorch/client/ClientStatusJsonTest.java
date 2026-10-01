package com.sakurakugu.autotorch.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
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

        assertTrue(root.getAsJsonObject("server").has("maxSphereRadius"));
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
