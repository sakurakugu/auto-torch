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
import com.sakurakugu.autotorch.config.ConfigDefinitions;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** 仅注册在服务端的管理员配置命令。 */
public final class AutoTorchServerCommands {
    private AutoTorchServerCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        register(dispatcher, ServerConfigNetwork::broadcast);
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher,
                                Consumer<MinecraftServer> configChanged) {
        dispatcher.register(literal("autotorch")
                .requires(source -> source.hasPermission(2))
                .executes(context -> showHelp(context))
                .then(literal("help").executes(AutoTorchServerCommands::showHelp))
                .then(literal("serverconfig")
                        .then(literal("gui").executes(AutoTorchServerCommands::openGui))
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

    /** 打开管理员设置界面；控制台没有客户端界面，只提示改用 get/set/defaults。 */
    private static int openGui(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = context.getSource().getPlayer();
        if (player == null) return error(context, "command.autotorch.server.gui_player_only");
        ServerConfigNetwork.sendOpenScreen(player);
        context.getSource().sendSuccess(Component.translatable(
                "command.autotorch.server.gui_opened"), false);
        return 1;
    }

    private static int showHelp(CommandContext<CommandSourceStack> context) {
        context.getSource().sendSuccess(Component.translatable("command.autotorch.server.help"), false);
        return 1;
    }

    private static int get(CommandContext<CommandSourceStack> context) {
        String key = StringArgumentType.getString(context, "key");
        var definition = ServerConfig.definition(key);
        if (definition == null) return error(context, "command.autotorch.server.unknown_key", key);
        context.getSource().sendSuccess(Component.translatable(
                "command.autotorch.server.value", key, ServerConfig.get(key)), false);
        return 1;
    }

    private static int set(CommandContext<CommandSourceStack> context,
                           Consumer<MinecraftServer> configChanged) {
        String key = StringArgumentType.getString(context, "key");
        String raw = StringArgumentType.getString(context, "value");
        ConfigDefinitions.Value definition = ServerConfig.definition(key);
        // 解析、范围与间距交叉约束统一交给配置门面，保证命令与管理员界面行为一致。
        ServerConfig.SetResult result = ServerConfig.setFromText(key, raw);
        switch (result.status()) {
            case OK -> {
                configChanged.accept(context.getSource().getServer());
                context.getSource().sendSuccess(Component.translatable(
                        "command.autotorch.server.value", key, result.value()), true);
                return 1;
            }
            case UNKNOWN_KEY -> {
                return error(context, "command.autotorch.server.unknown_key", key);
            }
            case UNSUPPORTED_TYPE -> {
                return error(context, "command.autotorch.server.unsupported_type");
            }
            case INVALID_VALUE -> {
                return definition instanceof ConfigDefinitions.BooleanValue
                        ? error(context, "command.autotorch.server.expected_boolean")
                        : error(context, "command.autotorch.server.expected_integer");
            }
            case OUT_OF_RANGE -> {
                ConfigDefinitions.IntValue intValue = (ConfigDefinitions.IntValue) definition;
                return error(context, "command.autotorch.server.out_of_range",
                        intValue.minValue(), intValue.maxValue());
            }
            case SPACING_ORDER -> {
                return error(context, "command.autotorch.server.spacing_order");
            }
            default -> {
                return error(context, "command.autotorch.server.unsupported_type");
            }
        }
    }

    private static int defaults(CommandContext<CommandSourceStack> context,
                                Consumer<MinecraftServer> configChanged) {
        ServerConfig.resetDefaults();
        configChanged.accept(context.getSource().getServer());
        context.getSource().sendSuccess(Component.translatable(
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
