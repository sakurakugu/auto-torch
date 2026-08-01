package com.sakurakugu.autotorch.client;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.tree.CommandNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class AutoTorchClientCommandsTest {
    @Test
    void registersClientCommandTree() {
        CommandDispatcher<Object> dispatcher = new CommandDispatcher<>();
        AutoTorchClientCommands.register(dispatcher);

        CommandNode<Object> root = child(dispatcher.getRoot(), "autotorch");
        child(root, "gui");
        child(root, "help");
        child(root, "status");

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
    }

    private static void assertToggle(CommandNode<Object> parent) {
        child(parent, "on");
        child(parent, "off");
    }

    private static CommandNode<Object> child(CommandNode<Object> parent, String name) {
        CommandNode<Object> child = parent.getChild(name);
        assertNotNull(child, () -> "缺少命令节点：" + name);
        return child;
    }
}
