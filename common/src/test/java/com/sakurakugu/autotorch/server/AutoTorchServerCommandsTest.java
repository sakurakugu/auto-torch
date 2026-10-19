package com.sakurakugu.autotorch.server;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.tree.CommandNode;
import com.sakurakugu.autotorch.client.AutoTorchClientCommands;
import com.sakurakugu.autotorch.config.ConfigBackend;
import com.sakurakugu.autotorch.config.ConfigDefinitions;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AutoTorchServerCommandsTest {
    @Test
    void registersServerCommandTree() {
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        AutoTorchServerCommands.register(dispatcher);

        CommandNode<CommandSourceStack> root = child(dispatcher.getRoot(), "autotorch");
        child(root, "help");
        CommandNode<CommandSourceStack> serverConfig = child(root, "serverconfig");
        assertNotNull(child(serverConfig, "gui").getCommand());
        child(child(serverConfig, "get"), "key");
        child(child(child(serverConfig, "set"), "key"), "value");
        child(serverConfig, "defaults");
    }

    @Test
    void suggestsServerConfigKeys() {
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        AutoTorchServerCommands.register(dispatcher);

        Suggestions suggestions = dispatcher.getCompletionSuggestions(
                dispatcher.parse("autotorch serverconfig get ", source())).join();

        for (ConfigDefinitions.Value definition : ServerConfig.definitions()) {
            assertTrue(suggestions.getList().stream()
                    .anyMatch(suggestion -> suggestion.getText().equals(definition.key())), definition.key());
        }
    }

    @Test
    void keepsServerSuggestionsAfterClientTreeMerges() {
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        AutoTorchServerCommands.register(dispatcher);
        AutoTorchClientCommands.register(dispatcher);

        Suggestions suggestions = dispatcher.getCompletionSuggestions(
                dispatcher.parse("autotorch serverconfig get ", source())).join();

        assertTrue(suggestions.getList().stream()
                .anyMatch(suggestion -> suggestion.getText().equals("lightingTask.enabled")));
    }

    @Test
    void clientTreePassesServerConfigCommandsThrough() {
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        AutoTorchClientCommands.register(dispatcher);

        for (String command : new String[]{
                "autotorch serverconfig gui",
                "autotorch serverconfig get limits.maxSphereRadius",
                "autotorch serverconfig set limits.maxSphereRadius 80",
                "autotorch serverconfig defaults"}) {
            CommandSyntaxException exception = org.junit.jupiter.api.Assertions.assertThrows(
                    CommandSyntaxException.class, () -> dispatcher.execute(command, source()));
            // 无客户端执行器的完整叶节点必须表现为 Unknown command，Fabric 才会转发给服务端。
            assertEquals(CommandSyntaxException.BUILT_IN_EXCEPTIONS.dispatcherUnknownCommand(),
                    exception.getType(), command);
        }
    }

    @Test
    void rejectsCrossedSpacingBounds() {
        // 修改一项时必须保持“下限 ≤ 上限”，否则客户端的间距滑块会出现无效区间。
        assertTrue(ServerConfig.minSpacingChangeAllowed(1, 12));
        assertTrue(ServerConfig.minSpacingChangeAllowed(12, 12));
        assertFalse(ServerConfig.minSpacingChangeAllowed(13, 12));

        assertTrue(ServerConfig.maxSpacingChangeAllowed(12, 1));
        assertTrue(ServerConfig.maxSpacingChangeAllowed(5, 5));
        assertFalse(ServerConfig.maxSpacingChangeAllowed(4, 5));
    }

    @Test
    void commandSetSharesValidationWithAdminScreen() {
        ServerConfig.install(new MemoryBackend());

        assertEquals(ServerConfig.SetStatus.OK,
                ServerConfig.setFromText("limits.maxSphereRadius", "80").status());
        assertEquals(80, ServerConfig.maxSphereRadius());

        // 界面与命令共用同一套错误状态，避免两处校验规则漂移。
        assertEquals(ServerConfig.SetStatus.OUT_OF_RANGE,
                ServerConfig.setFromText("limits.maxSphereRadius", "999").status());
        assertEquals(ServerConfig.SetStatus.INVALID_VALUE,
                ServerConfig.setFromText("lightingTask.enabled", "yes").status());
        assertEquals(ServerConfig.SetStatus.UNKNOWN_KEY,
                ServerConfig.setFromText("missing.key", "1").status());
    }

    @Test
    void setRejectsSpacingThatCrossesTheCounterpart() {
        ServerConfig.install(new MemoryBackend());

        assertEquals(ServerConfig.SetStatus.OK,
                ServerConfig.setFromText("limits.maxSpacing", "5").status());
        assertEquals(ServerConfig.SetStatus.SPACING_ORDER,
                ServerConfig.setFromText("limits.minSpacing", "6").status());
        assertEquals(1, ServerConfig.minSpacing());
        assertEquals(5, ServerConfig.maxSpacing());
    }

    @Test
    void resetsServerConfigByKeyCategoryAndSection() {
        ServerConfig.install(new MemoryBackend());
        ServerConfig.setFromText("limits.maxSphereRadius", "80");
        ServerConfig.setFromText("performance.placeBudgetPerTaskTick", "4");

        assertTrue(ServerConfig.resetScope("limits.maxSphereRadius"));
        assertEquals(ConfigDefinitions.LIMIT_MAX_SPHERE_RADIUS.defaultValue(), ServerConfig.maxSphereRadius());

        assertTrue(ServerConfig.resetScope(ConfigDefinitions.ServerCategory.PERFORMANCE.name()));
        assertEquals(ConfigDefinitions.PERFORMANCE_PLACE_BUDGET_PER_TASK_TICK.defaultValue(),
                ServerConfig.placeBudgetPerTaskTick());

        ServerConfig.setFromText("limits.maxSphereRadius", "80");
        assertTrue(ServerConfig.resetScope("limits"));
        assertEquals(ConfigDefinitions.LIMIT_MAX_SPHERE_RADIUS.defaultValue(), ServerConfig.maxSphereRadius());
        assertFalse(ServerConfig.resetScope("missing"));
    }

    /** 测试用内存后端：未写入的键读取配置默认值，模拟服务端 TOML。 */
    private static final class MemoryBackend implements ConfigBackend {
        private final java.util.Map<String, Object> values = new java.util.HashMap<>();

        @Override
        public boolean getBoolean(String key, boolean defaultValue) {
            return values.get(key) instanceof Boolean ? ((Boolean) values.get(key)) : defaultValue;
        }

        @Override
        public int getInt(String key, int defaultValue) {
            return values.get(key) instanceof Integer ? ((Integer) values.get(key)) : defaultValue;
        }

        @Override
        public double getDouble(String key, double defaultValue) {
            return values.get(key) instanceof Double ? ((Double) values.get(key)) : defaultValue;
        }

        @Override
        public String getString(String key, String defaultValue) {
            return values.get(key) instanceof String ? ((String) values.get(key)) : defaultValue;
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

    private static CommandSourceStack source() {
        return new CommandSourceStack(
                CommandSource.NULL, Vec3.ZERO, Vec2.ZERO, null, 2, "test",
                new TextComponent("test"), null, null);
    }

    private static CommandNode<CommandSourceStack> child(CommandNode<CommandSourceStack> parent, String name) {
        CommandNode<CommandSourceStack> child = parent.getChild(name);
        assertNotNull(child, () -> "缺少命令节点：" + name);
        return child;
    }
}
