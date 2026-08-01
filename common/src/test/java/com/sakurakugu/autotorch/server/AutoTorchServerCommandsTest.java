package com.sakurakugu.autotorch.server;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.tree.CommandNode;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

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
        child(child(serverConfig, "get"), "key");
        child(child(child(serverConfig, "set"), "key"), "value");
        child(serverConfig, "defaults");
    }

    @Test
    void suggestsServerConfigKeys() {
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        AutoTorchServerCommands.register(dispatcher);
        CommandSourceStack source = new CommandSourceStack(
                CommandSource.NULL, Vec3.ZERO, Vec2.ZERO, null, 2, "test",
                new TextComponent("test"), null, null);

        Suggestions suggestions = dispatcher.getCompletionSuggestions(
                dispatcher.parse("autotorch serverconfig get ", source)).join();

        assertTrue(suggestions.getList().stream()
                .anyMatch(suggestion -> suggestion.getText().equals("lightingTask.enabled")));
    }

    private static CommandNode<CommandSourceStack> child(CommandNode<CommandSourceStack> parent, String name) {
        CommandNode<CommandSourceStack> child = parent.getChild(name);
        assertNotNull(child, () -> "缺少命令节点：" + name);
        return child;
    }
}
