package com.sakurakugu.autotorch.client;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.tree.CommandNode;
import com.sakurakugu.autotorch.config.ConfigDefinitions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AutoTorchClientCommandsTest {
    @Test
    void resetCommandsCoverEverySettingSectionAndFeatureGroup() {
        CommandDispatcher<Object> dispatcher = new CommandDispatcher<>();
        AutoTorchClientCommands.register(dispatcher);
        CommandNode<Object> config = child(child(dispatcher.getRoot(), "autotorch"), "config");
        for (String alias : new String[] {"defaults", "reset"}) {
            CommandNode<Object> root = child(config, alias);
            assertNotNull(root.getCommand());
            for (ConfigDefinitions.Value definition : ConfigDefinitions.CLIENT) {
                String[] path = definition.key().split("\\.");
                CommandNode<Object> section = child(root, path[0]);
                assertNotNull(section.getCommand(), path[0]);
                assertNotNull(child(section, path[1]).getCommand(), definition.key());
                assertFalse(dispatcher.parse("autotorch config " + alias + " "
                        + definition.key().replace('.', ' '), new Object()).getReader().canRead());
            }
            CommandNode<Object> group = child(root, "group");
            assertNull(group.getCommand());
            for (String name : new String[] {"area", "overlay", "nearby"}) {
                assertNotNull(child(group, name).getCommand());
            }
        }
    }

    @Test
    void everyClientSettingHasGetAndSetCommands() {
        CommandDispatcher<Object> dispatcher = new CommandDispatcher<>();
        AutoTorchClientCommands.register(dispatcher);
        for (ConfigDefinitions.Value definition : ConfigDefinitions.CLIENT) {
            CommandNode<Object> query = child(child(child(dispatcher.getRoot(), "autotorch"), "config"), "get");
            CommandNode<Object> setter = child(child(child(dispatcher.getRoot(), "autotorch"), "config"), "set");
            for (String segment : definition.key().split("\\.")) {
                query = child(query, segment);
                setter = child(setter, segment);
            }
            assertNotNull(query.getCommand(), definition.key());
            // set 必须带 value，节点本身不再承担查询。
            assertNull(setter.getCommand(), definition.key());
            assertNotNull(child(setter, "value").getCommand(), definition.key());
        }
    }

    @Test
    void leavesServerConfigCommandsForServer() {
        // 1.9.4 的 serverconfig 命令由服务端执行，本地入口必须放行，交给 Forge 层转发。
        assertFalse(AutoTorchClientCommands.tryExecute("/autotorch serverconfig"));
        assertFalse(AutoTorchClientCommands.tryExecute("/autotorch serverconfig get enabled"));
    }

    @Test
    void registersClientCommandTree() {
        CommandDispatcher<Object> dispatcher = new CommandDispatcher<>();
        AutoTorchClientCommands.register(dispatcher);

        CommandNode<Object> root = child(dispatcher.getRoot(), "autotorch");
        child(root, "gui");
        child(root, "help");
        child(child(root, "status"), "json");
        child(child(root, "config"), "defaults");
        child(child(root, "config"), "reset");
        child(child(root, "config"), "get");
        child(child(root, "config"), "set");

        CommandNode<Object> nearby = child(root, "nearby");
        child(nearby, "on");
        child(nearby, "off");
        child(child(nearby, "threshold"), "value");
        CommandNode<Object> skylight = child(nearby, "skylight");
        child(skylight, "on");
        child(skylight, "off");

        CommandNode<Object> overlay = child(root, "overlay");
        child(overlay, "on");
        child(overlay, "off");
        child(child(overlay, "range"), "value");
        child(child(overlay, "down_range"), "value");
        child(child(overlay, "up_range"), "value");
        assertToggle(child(overlay, "render_through"));
        assertToggle(child(overlay, "number_rotation"));
        CommandNode<Object> mode = child(overlay, "mode");
        child(mode, "crosses");
        child(mode, "numbers");
        CommandNode<Object> detect = child(overlay, "detect");
        assertToggle(child(detect, "swamp_slime"));
        assertToggle(child(detect, "drowned"));

        CommandNode<Object> selection = child(root, "selection");
        assertPoint(child(selection, "pos1"));
        assertPoint(child(selection, "pos2"));
        child(selection, "swap");
        child(selection, "clear");
        child(child(child(selection, "box"), "first"), "second");
        child(child(child(selection, "sphere"), "center"), "radius");
        assertToggle(child(selection, "tool"));
        assertToggle(child(selection, "wooden_axe"));
        child(selection, "list");

        CommandNode<Object> zone = child(root, "zone");
        child(child(zone, "list"), "number");
        child(zone, "clear");
        CommandNode<Object> lighting = child(zone, "lighting");
        child(lighting, "set");
        child(lighting, "load");
        child(lighting, "clear");
        CommandNode<Object> exclusion = child(zone, "exclusion");
        child(exclusion, "add");
        child(child(exclusion, "load"), "number");
        child(child(exclusion, "replace"), "number");
        child(child(exclusion, "remove"), "number");
        child(exclusion, "clear");

        CommandNode<Object> task = child(root, "task");
        child(task, "start");
        child(task, "cancel");
        child(task, "status");

        // serverconfig 只声明语法供补全，不绑定客户端执行器。
        CommandNode<Object> serverConfig = child(root, "serverconfig");
        assertNull(serverConfig.getCommand());
        child(serverConfig, "gui");
        child(child(serverConfig, "get"), "key");
        child(child(child(serverConfig, "set"), "key"), "value");
        child(serverConfig, "defaults");
    }

    @Test
    void suggestsClientCommands() {
        assertTrue(AutoTorchClientCommands.suggestions("autotorch ").contains("nearby"));
        assertTrue(AutoTorchClientCommands.suggestions("autotorch overlay ").contains("range"));
        assertTrue(AutoTorchClientCommands.suggestions("autotorch nearby skylight ").contains("on"));
    }

    private static void assertToggle(CommandNode<Object> parent) {
        child(parent, "on");
        child(parent, "off");
    }

    private static void assertPoint(CommandNode<Object> parent) {
        child(parent, "here");
        child(parent, "target");
        child(parent, "pos");
    }

    private static CommandNode<Object> child(CommandNode<Object> parent, String name) {
        CommandNode<Object> child = parent.getChild(name);
        assertNotNull(child, () -> "缺少命令节点：" + name);
        return child;
    }
}
