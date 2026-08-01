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
import com.sakurakugu.autotorch.config.ConfigDefinitions.Value;
import net.minecraft.command.CommandSource;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.server.MinecraftServer;

/** 仅注册在服务端的管理员配置命令。 */
public final class AutoTorchServerCommands {
    private AutoTorchServerCommands() {}

    public static void register(CommandDispatcher<CommandSource> dispatcher) {
        register(dispatcher, server -> {});
    }

    public static void register(CommandDispatcher<CommandSource> dispatcher,
                                Consumer<MinecraftServer> configChanged) {
        dispatcher.register(literal("autotorch")
                .requires(source -> source.hasPermissionLevel(2))
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

    private static int showHelp(CommandContext<CommandSource> context) {
        context.getSource().sendFeedback(new TextComponentTranslation("command.autotorch.server.help"), false);
        return 1;
    }

    private static int get(CommandContext<CommandSource> context) {
        String key = StringArgumentType.getString(context, "key");
        Value definition = ServerConfig.definition(key);
        if (definition == null) return error(context, "command.autotorch.server.unknown_key", key);
        context.getSource().sendFeedback(new TextComponentTranslation(
                "command.autotorch.server.value", key, ServerConfig.get(key)), false);
        return 1;
    }

    private static int set(CommandContext<CommandSource> context,
                           Consumer<MinecraftServer> configChanged) {
        String key = StringArgumentType.getString(context, "key");
        String raw = StringArgumentType.getString(context, "value");
        Value definition = ServerConfig.definition(key);
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
        if (definition instanceof IntValue) {
            IntValue intValue = (IntValue) definition;
            if ((Integer) value < intValue.minValue() || (Integer) value > intValue.maxValue()) {
                return error(context, "command.autotorch.server.out_of_range",
                        intValue.minValue(), intValue.maxValue());
            }
        }
        ServerConfig.set(key, value);
        configChanged.accept(context.getSource().getServer());
        context.getSource().sendFeedback(new TextComponentTranslation(
                "command.autotorch.server.value", key, ServerConfig.get(key)), true);
        return 1;
    }

    private static int defaults(CommandContext<CommandSource> context,
                                Consumer<MinecraftServer> configChanged) {
        ServerConfig.resetDefaults();
        configChanged.accept(context.getSource().getServer());
        context.getSource().sendFeedback(new TextComponentTranslation(
                "command.autotorch.server.defaults"), true);
        return 1;
    }

    private static int error(CommandContext<CommandSource> context, String key, Object... args) {
        context.getSource().sendErrorMessage(new TextComponentTranslation(key, args));
        return 0;
    }

    private static CompletableFuture<Suggestions> suggestKeys(
            CommandContext<CommandSource> context, SuggestionsBuilder builder) {
        ServerConfig.definitions().stream().map(value -> value.key()).forEach(builder::suggest);
        return builder.buildFuture();
    }

    private static LiteralArgumentBuilder<CommandSource> literal(String name) {
        return LiteralArgumentBuilder.literal(name);
    }

    private static <T> RequiredArgumentBuilder<CommandSource, T> argument(
            String name, ArgumentType<T> type) {
        return RequiredArgumentBuilder.argument(name, type);
    }
}
