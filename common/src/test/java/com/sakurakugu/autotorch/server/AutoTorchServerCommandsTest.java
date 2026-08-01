package com.sakurakugu.autotorch.server;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.tree.CommandNode;
import net.minecraft.command.CommandSource;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class AutoTorchServerCommandsTest {
    @Test
    void registersServerCommandTree() {
        CommandDispatcher<CommandSource> dispatcher = new CommandDispatcher<>();
        AutoTorchServerCommands.register(dispatcher);

        CommandNode<CommandSource> root = child(dispatcher.getRoot(), "autotorch");
        child(root, "help");
        CommandNode<CommandSource> serverConfig = child(root, "serverconfig");
        child(child(serverConfig, "get"), "key");
        child(child(child(serverConfig, "set"), "key"), "value");
        child(serverConfig, "defaults");
    }

    private static CommandNode<CommandSource> child(CommandNode<CommandSource> parent, String name) {
        CommandNode<CommandSource> child = parent.getChild(name);
        assertNotNull(child, () -> "缺少命令节点：" + name);
        return child;
    }
}
