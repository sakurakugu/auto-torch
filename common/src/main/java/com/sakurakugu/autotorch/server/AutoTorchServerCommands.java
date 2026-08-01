package com.sakurakugu.autotorch.server;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.sakurakugu.autotorch.config.ConfigDefinitions.BooleanValue;
import com.sakurakugu.autotorch.config.ConfigDefinitions.IntValue;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.permissions.Permissions;

/** 仅注册在服务端的管理员配置命令。 */
public final class AutoTorchServerCommands {
    private AutoTorchServerCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        register(dispatcher, server -> {});
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher,
                                Consumer<MinecraftServer> configChanged) {
        dispatcher.register(literal("autotorch")
                .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
                .executes(context -> showHelp(context))
                .then(literal("help").executes(AutoTorchServerCommands::showHelp))
                .then(literal("serverconfig")
                        .then(literal("get")
                                .then(argument("key", StringArgumentType.word())
                                        .suggests(AutoTorchServerCommands::suggestKeys)
                                        .executes(AutoTorchServerCommands::get)))
                        .then(literal("set")
                                .then(argument("key", StringArgumentType.word())
                                        .suggests(AutoTorchServerCommands::suggestKeys)
                                        .then(argument("value", StringArgumentType.word())
                                                .executes(context -> set(context, configChanged)))))
                        .then(literal("defaults")
                                .executes(context -> defaults(context, configChanged)))));
    }

    private static int showHelp(CommandContext<CommandSourceStack> context) {
        context.getSource().sendSuccess(() -> Component.translatable("command.autotorch.server.help"), false);
        return 1;
    }

    private static int get(CommandContext<CommandSourceStack> context) {
        String key = StringArgumentType.getString(context, "key");
        var definition = ServerConfig.definition(key);
        if (definition == null) return error(context, "command.autotorch.server.unknown_key", key);
        context.getSource().sendSuccess(() -> Component.translatable(
                "command.autotorch.server.value", key, ServerConfig.get(key)), false);
        return 1;
    }

    private static int set(CommandContext<CommandSourceStack> context,
                           Consumer<MinecraftServer> configChanged) {
        String key = StringArgumentType.getString(context, "key");
        String raw = StringArgumentType.getString(context, "value");
        var definition = ServerConfig.definition(key);
        if (definition == null) return error(context, "command.autotorch.server.unknown_key", key);
        Object value;
        try {
            if (definition instanceof BooleanValue) value = Boolean.parseBoolean(raw);
            else if (definition instanceof IntValue) value = Integer.parseInt(raw);
            else return error(context, "command.autotorch.server.unsupported_type");
            if (definition instanceof BooleanValue && !raw.equalsIgnoreCase("true") && !raw.equalsIgnoreCase("false"))
                return error(context, "command.autotorch.server.expected_boolean");
        } catch (NumberFormatException exception) {
            return error(context, "command.autotorch.server.expected_integer");
        }
        if (definition instanceof IntValue intValue
                && ((Integer) value < intValue.minValue() || (Integer) value > intValue.maxValue()))
            return error(context, "command.autotorch.server.out_of_range", intValue.minValue(), intValue.maxValue());
        ServerConfig.set(key, value);
        configChanged.accept(context.getSource().getServer());
        context.getSource().sendSuccess(() -> Component.translatable(
                "command.autotorch.server.value", key, ServerConfig.get(key)), true);
        return 1;
    }

    private static int defaults(CommandContext<CommandSourceStack> context,
                                Consumer<MinecraftServer> configChanged) {
        ServerConfig.resetDefaults();
        configChanged.accept(context.getSource().getServer());
        context.getSource().sendSuccess(() -> Component.translatable(
                "command.autotorch.server.defaults"), true);
        return 1;
    }

    private static int error(CommandContext<CommandSourceStack> context, String key, Object... args) {
        context.getSource().sendFailure(Component.translatable(key, args));
        return 0;
    }

    private static CompletableFuture<Suggestions> suggestKeys(
            CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggest(
                ServerConfig.definitions().stream().map(value -> value.key()), builder);
    }

    private static LiteralArgumentBuilder<CommandSourceStack> literal(String name) {
        return LiteralArgumentBuilder.literal(name);
    }

    private static <T> RequiredArgumentBuilder<CommandSourceStack, T> argument(
            String name, ArgumentType<T> type) {
        return RequiredArgumentBuilder.argument(name, type);
    }
}
