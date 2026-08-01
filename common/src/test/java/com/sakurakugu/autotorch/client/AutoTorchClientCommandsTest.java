package com.sakurakugu.autotorch.client;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.tree.CommandNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AutoTorchClientCommandsTest {
    @Test
    void registersClientCommandTree() {
        CommandDispatcher<Object> dispatcher = new CommandDispatcher<>();
        AutoTorchClientCommands.register(dispatcher);

        CommandNode<Object> root = child(dispatcher.getRoot(), "autotorch");
        child(root, "gui");
        child(root, "help");
        child(root, "status");
        child(child(root, "config"), "defaults");

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
