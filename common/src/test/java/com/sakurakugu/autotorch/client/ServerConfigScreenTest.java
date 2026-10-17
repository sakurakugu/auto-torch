package com.sakurakugu.autotorch.client;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.sakurakugu.autotorch.config.ConfigBackend;
import com.sakurakugu.autotorch.config.ConfigDefinitions;
import com.sakurakugu.autotorch.network.ServerConfigSetPayload;
import com.sakurakugu.autotorch.server.ServerConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 服务端设置界面把火把间距上下限合并为一个双端点滑动条。 */
class ServerConfigScreenTest {
    @Test
    void mergesSpacingLimitsIntoOneDualSliderRow() {
        List<ConfigDefinitions.Value> definitions = ConfigDefinitions.ServerCategory.LIMITS.definitions();
        List<ServerConfigScreen.RowSpec> specs = ServerConfigScreen.buildRowSpecs(definitions);

        // 区域限制原本 8 项配置，间距上下限合并后只剩 7 行。
        assertEquals(definitions.size() - 1, specs.size());
        List<ServerConfigScreen.RowKind> kinds = specs.stream().map(ServerConfigScreen.RowSpec::kind).toList();
        assertEquals(1, kinds.stream()
                .filter(kind -> kind == ServerConfigScreen.RowKind.SPACING_RANGE).count());
        int spacingIndex = kinds.indexOf(ServerConfigScreen.RowKind.SPACING_RANGE);
        assertEquals(ConfigDefinitions.LIMIT_MIN_SPACING, specs.get(spacingIndex).definition());
        // 合并后的行代表上下限两项，上限配置不再单独占行。
        assertTrue(specs.stream().noneMatch(spec -> spec.definition() == ConfigDefinitions.LIMIT_MAX_SPACING));
    }

    @Test
    void otherCategoriesKeepOneRowPerSetting() {
        for (ConfigDefinitions.ServerCategory category : List.of(
                ConfigDefinitions.ServerCategory.RULES, ConfigDefinitions.ServerCategory.PERFORMANCE)) {
            List<ConfigDefinitions.Value> definitions = category.definitions();
            List<ServerConfigScreen.RowSpec> specs = ServerConfigScreen.buildRowSpecs(definitions);
            assertEquals(definitions.size(), specs.size(), category.name());
            assertTrue(specs.stream().allMatch(spec -> spec.kind() == ServerConfigScreen.RowKind.NORMAL),
                    category.name());
        }
    }

    @Test
    void submitsSpacingRequestsInAnOrderTheServerAccepts() {
        // 新下限 5 不超过当前上限 8，先写下限即可。
        assertEquals(List.of(
                        new ServerConfigSetPayload("limits.minSpacing", "5"),
                        new ServerConfigSetPayload("limits.maxSpacing", "10")),
                ServerConfigScreen.spacingSetRequests(5, 10, 8));

        // 新下限 9 超过当前上限 8，整体上调时必须先写上限，否则下限会被交叉校验拒绝。
        assertEquals(List.of(
                        new ServerConfigSetPayload("limits.maxSpacing", "11"),
                        new ServerConfigSetPayload("limits.minSpacing", "9")),
                ServerConfigScreen.spacingSetRequests(9, 11, 8));

        // 两项同时下调时先写下限同样满足交叉校验。
        assertEquals(List.of(
                        new ServerConfigSetPayload("limits.minSpacing", "1"),
                        new ServerConfigSetPayload("limits.maxSpacing", "6")),
                ServerConfigScreen.spacingSetRequests(1, 6, 8));
    }

    @Test
    void spacingRequestsApplyWithoutSpacingOrderRejection() {
        ServerConfig.install(new MemoryBackend());
        ServerConfig.setFromText("limits.minSpacing", "3");
        ServerConfig.setFromText("limits.maxSpacing", "8");

        // 整体上调：按界面生成的顺序逐项提交，每一步都能通过服务端交叉校验。
        assertTrue(apply(ServerConfigScreen.spacingSetRequests(9, 11, ServerConfig.maxSpacing())));
        assertEquals(9, ServerConfig.minSpacing());
        assertEquals(11, ServerConfig.maxSpacing());

        // 整体下调同样通过。
        assertTrue(apply(ServerConfigScreen.spacingSetRequests(2, 5, ServerConfig.maxSpacing())));
        assertEquals(2, ServerConfig.minSpacing());
        assertEquals(5, ServerConfig.maxSpacing());
    }

    private static boolean apply(List<ServerConfigSetPayload> requests) {
        for (ServerConfigSetPayload request : requests) {
            if (ServerConfig.setFromText(request.key(), request.value()).status() != ServerConfig.SetStatus.OK) {
                return false;
            }
        }
        return true;
    }

    /** 测试用内存后端：未写入的键读取配置默认值，模拟服务端 TOML。 */
    private static final class MemoryBackend implements ConfigBackend {
        private final Map<String, Object> values = new HashMap<>();

        @Override
        public boolean getBoolean(String key, boolean defaultValue) {
            return values.get(key) instanceof Boolean value ? value : defaultValue;
        }

        @Override
        public int getInt(String key, int defaultValue) {
            return values.get(key) instanceof Integer value ? value : defaultValue;
        }

        @Override
        public double getDouble(String key, double defaultValue) {
            return values.get(key) instanceof Double value ? value : defaultValue;
        }

        @Override
        public String getString(String key, String defaultValue) {
            return values.get(key) instanceof String value ? value : defaultValue;
        }

        @Override
        public void setBoolean(String key, boolean value) { values.put(key, value); }

        @Override
        public void setInt(String key, int value) { values.put(key, value); }

        @Override
        public void setDouble(String key, double value) { values.put(key, value); }

        @Override
        public void setString(String key, String value) { values.put(key, value); }

        @Override
        public void save() { }
    }
}
