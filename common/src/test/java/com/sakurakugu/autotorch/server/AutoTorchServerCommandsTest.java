package com.sakurakugu.autotorch.server;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.tree.CommandNode;
import net.minecraft.command.CommandSource;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.text.TextComponentString;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    @Test
    void suggestsServerConfigKeys() {
        CommandDispatcher<CommandSource> dispatcher = new CommandDispatcher<>();
        AutoTorchServerCommands.register(dispatcher);
        CommandSource source = new CommandSource(
                null, Vec3d.ZERO, Vec2f.ZERO, null, 2, "test",
                new TextComponentString("test"), null, null);

        Suggestions suggestions = dispatcher.getCompletionSuggestions(
                dispatcher.parse("autotorch serverconfig get ", source)).join();

        assertTrue(suggestions.getList().stream()
                .anyMatch(suggestion -> suggestion.getText().equals("lightingTask.enabled")));
    }

    private static CommandNode<CommandSource> child(CommandNode<CommandSource> parent, String name) {
        CommandNode<CommandSource> child = parent.getChild(name);
        assertNotNull(child, () -> "缺少命令节点：" + name);
        return child;
    }
}
