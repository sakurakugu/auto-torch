package com.sakurakugu.autotorch.server;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.tree.CommandNode;
import net.minecraft.commands.CommandSourceStack;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class AutoTorchServerCommandsTest {
    @Test
    void registersServerCommandTree() {
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        AutoTorchServerCommands.register(dispatcher);

        CommandNode<CommandSourceStack> root = child(dispatcher.getRoot(), "autotorch");
        child(root, "help");
        CommandNode<CommandSourceStack> serverConfig = child(root, "serverconfig");
        child(child(serverConfig, "get"), "key");
        child(child(child(serverConfig, "set"), "key"), "value");
        child(serverConfig, "defaults");
    }

    private static CommandNode<CommandSourceStack> child(CommandNode<CommandSourceStack> parent, String name) {
        CommandNode<CommandSourceStack> child = parent.getChild(name);
        assertNotNull(child, () -> "缺少命令节点：" + name);
        return child;
    }
}
