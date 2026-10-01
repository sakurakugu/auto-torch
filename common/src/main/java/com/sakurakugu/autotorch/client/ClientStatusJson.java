package com.sakurakugu.autotorch.client;

import java.util.List;
import java.util.Locale;

import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.sakurakugu.autotorch.network.AreaShape;
import com.sakurakugu.autotorch.network.AreaZone;
import net.minecraft.util.math.BlockPos;

/** 把当前客户端状态汇总成单行 JSON 文本，供 `/autotorch status json` 输出。 */
public final class ClientStatusJson {
    private ClientStatusJson() {
    }

    /**
     * 汇总当前状态。
     *
     * @param consumesTorches 本次任务是否会消耗背包火把，由调用方结合游戏模式判定
     * @param playerPosition  玩家所在方块；为 null 时省略与玩家绑定的选区和任务分组
     */
    public static String build(boolean consumesTorches, BlockPos playerPosition) {
        JsonObject root = new JsonObject();
        root.add("nearby", nearby());
        root.add("lightOverlay", lightOverlay());
        if (playerPosition != null) {
            root.add("selection", selection(playerPosition));
            root.add("task", task(consumesTorches));
        }
        root.add("server", server());
        return root.toString();
    }

    /** 任务实际使用的火把上限；0 表示不限。 */
    public static int effectiveDefaultMaxTorches() {
        int configured = ClientConfig.defaultMaxTorches();
        if (configured == 0) {
            return ServerConfigState.allowsUnlimitedTorches() ? 0 : ServerConfigState.maxTorchesPerTask();
        }
        return Math.min(configured, ServerConfigState.maxTorchesPerTask());
    }

    /** 任务实际使用的间距，被服务端上下限裁剪后的值。 */
    public static int effectiveDefaultMinSpacing() {
        return Math.max(ServerConfigState.minSpacing(),
                Math.min(ServerConfigState.maxSpacing(), ClientConfig.defaultMinSpacing()));
    }

    private static JsonObject nearby() {
        JsonObject nearby = new JsonObject();
        nearby.addProperty("enabled", ClientConfig.isNearbyAutoTorchEnabled());
        nearby.addProperty("lightThreshold", ClientConfig.nearbyAutoTorchThreshold());
        nearby.addProperty("includeSkyLight", ClientConfig.includesSkyLight());
        return nearby;
    }

    private static JsonObject lightOverlay() {
        JsonObject overlay = new JsonObject();
        overlay.addProperty("enabled", LightOverlayState.isEnabled());
        overlay.addProperty("renderThrough", ClientConfig.isLightOverlayRenderThrough());
        overlay.addProperty("rotateNumbers", ClientConfig.rotatesLightOverlayNumbers());
        // 范围取运行时值：设置时会被重新裁剪，可能不等于配置中的原始值。
        overlay.addProperty("horizontalRange", LightOverlayState.horizontalRange());
        overlay.addProperty("downRange", LightOverlayState.downRange());
        overlay.addProperty("upRange", LightOverlayState.upRange());
        overlay.addProperty("displayMode", enumName(LightOverlayState.displayMode()));
        overlay.addProperty("swampSlime", LightOverlayState.isSwampSlimeDetectionEnabled());
        overlay.addProperty("drowned", LightOverlayState.isDrownedDetectionEnabled());
        return overlay;
    }

    private static JsonObject selection(BlockPos playerPosition) {
        AreaZone draft = SelectionState.draft(playerPosition);
        JsonObject selection = new JsonObject();
        selection.addProperty("shape", enumName(draft.shape()));
        selection.add("first", position(draft.first()));
        selection.add("second", position(draft.second()));
        if (draft.shape() == AreaShape.SPHERE) {
            selection.addProperty("radius", draft.radius());
        }
        selection.addProperty("displayMode", enumName(SelectionState.displayMode()));
        selection.addProperty("sphereDisplayMode", enumName(SelectionState.sphereDisplayMode()));
        selection.addProperty("overlayEnabled", SelectionState.isOverlayEnabled());
        selection.addProperty("drafting", SelectionState.drafting());
        selection.addProperty("editingExclusion", SelectionState.isEditingExclusion());
        AreaZone lightingZone = SelectionState.lightingZone();
        selection.add("lightingZone", lightingZone == null ? JsonNull.INSTANCE : zone(lightingZone));
        JsonArray exclusions = new JsonArray();
        List<AreaZone> zones = SelectionState.exclusions();
        for (AreaZone exclusion : zones) {
            exclusions.add(zone(exclusion));
        }
        selection.add("exclusions", exclusions);
        return selection;
    }

    private static JsonObject task(boolean consumesTorches) {
        int maxTorches = effectiveDefaultMaxTorches();
        JsonObject task = new JsonObject();
        task.addProperty("woodenAxeSelection", ClientConfig.isWoodenAxeSelectionEnabled());
        task.addProperty("consumeTorches", consumesTorches);
        // 0 表示不限，用 null 表达比数字 0 更准确。
        if (maxTorches == 0) {
            task.add("maxTorches", JsonNull.INSTANCE);
        } else {
            task.addProperty("maxTorches", maxTorches);
        }
        task.addProperty("minSpacing", effectiveDefaultMinSpacing());
        task.addProperty("lightThreshold", ClientConfig.defaultTaskLightThreshold());
        task.addProperty("undergroundOnly", ClientConfig.isDefaultUndergroundOnly());
        return task;
    }

    private static JsonObject server() {
        JsonObject server = new JsonObject();
        server.addProperty("lightingTaskEnabled", ServerConfigState.lightingTaskEnabled());
        server.addProperty("survivalConsumesTorches", ServerConfigState.survivalConsumesTorches());
        server.addProperty("maxBoxAxisLength", ServerConfigState.maxBoxAxisLength());
        server.addProperty("maxSphereRadius", ServerConfigState.maxSphereRadius());
        server.addProperty("maxExclusions", ServerConfigState.maxExclusions());
        server.addProperty("maxTorchesPerTask", ServerConfigState.maxTorchesPerTask());
        server.addProperty("allowsUnlimitedTorches", ServerConfigState.allowsUnlimitedTorches());
        server.addProperty("minSpacing", ServerConfigState.minSpacing());
        server.addProperty("maxSpacing", ServerConfigState.maxSpacing());
        return server;
    }

    private static JsonObject zone(AreaZone zone) {
        JsonObject value = new JsonObject();
        value.addProperty("shape", enumName(zone.shape()));
        value.add("first", position(zone.first()));
        value.add("second", position(zone.second()));
        if (zone.shape() == AreaShape.SPHERE) {
            value.addProperty("radius", zone.radius());
        }
        return value;
    }

    private static JsonArray position(BlockPos pos) {
        JsonArray array = new JsonArray();
        // gson 2.2.4 只提供 add(JsonElement)，需要显式包一层 JsonPrimitive。
        array.add(new JsonPrimitive(Integer.valueOf(pos.getX())));
        array.add(new JsonPrimitive(Integer.valueOf(pos.getY())));
        array.add(new JsonPrimitive(Integer.valueOf(pos.getZ())));
        return array;
    }

    /** 枚举统一转成小写下划线形式，与配置键风格一致。 */
    private static String enumName(Enum<?> value) {
        return value.name().toLowerCase(Locale.ROOT);
    }
}
