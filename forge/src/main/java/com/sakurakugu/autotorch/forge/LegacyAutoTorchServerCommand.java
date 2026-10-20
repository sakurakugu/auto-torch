package com.sakurakugu.autotorch.forge;

import java.util.Collections;
import java.util.List;

import com.sakurakugu.autotorch.config.ConfigDefinitions.BooleanValue;
import com.sakurakugu.autotorch.config.ConfigDefinitions.IntValue;
import com.sakurakugu.autotorch.config.ConfigDefinitions.Value;
import com.sakurakugu.autotorch.server.ServerConfig;
import com.sakurakugu.autotorch.server.ServerConfigNetwork;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.BlockPos;
import net.minecraft.util.ChatComponentTranslation;

/**
 * 将共享服务端配置命令接入 Forge 1.8.9 的 ICommand API。
 * 1.13.2 的 Brigadier 命令在这里等价实现为：help、serverconfig gui/get/set/defaults/reset。
 */
final class LegacyAutoTorchServerCommand extends CommandBase {
    @Override public String getCommandName() { return "autotorch"; }
    @Override public String getCommandUsage(ICommandSender sender) { return "command.autotorch.server.help"; }
    @Override public int getRequiredPermissionLevel() { return 2; }

    @Override
    public void processCommand(ICommandSender sender, String[] arguments) throws CommandException {
        MinecraftServer server = MinecraftServer.getServer();
        if (arguments.length == 0 || "help".equals(arguments[0])) {
            sender.addChatMessage(new ChatComponentTranslation("command.autotorch.server.help"));
            return;
        }
        if (!"serverconfig".equals(arguments[0]) || arguments.length < 2) {
            throw new CommandException("command.autotorch.server.help");
        }
        String action = arguments[1];
        if ("gui".equals(action)) {
            openServerConfigScreen(sender);
            return;
        }
        if ("defaults".equals(action)) {
            reset(server, sender, "");
            return;
        }
        if ("reset".equals(action)) {
            reset(server, sender, arguments.length >= 3 ? arguments[2] : "");
            return;
        }
        if (arguments.length < 3) throw new CommandException("command.autotorch.server.help");
        String key = arguments[2];
        Value definition = ServerConfig.definition(key);
        if (definition == null) throw new CommandException("command.autotorch.server.unknown_key", key);
        if ("get".equals(action)) {
            sender.addChatMessage(new ChatComponentTranslation(
                    "command.autotorch.server.value", key, ServerConfig.get(key)));
            return;
        }
        if (!"set".equals(action) || arguments.length < 4) {
            throw new CommandException("command.autotorch.server.help");
        }
        set(server, sender, definition, arguments[3]);
    }

    /** 给游戏内管理员发送打开服务端配置界面的请求；控制台无法打开界面。 */
    private static void openServerConfigScreen(ICommandSender sender) throws CommandException {
        Entity entity = sender.getCommandSenderEntity();
        if (!(entity instanceof EntityPlayerMP)) {
            throw new CommandException("command.autotorch.server.gui_player_only");
        }
        ServerConfigNetwork.sendOpenScreen((EntityPlayerMP) entity);
        sender.addChatMessage(new ChatComponentTranslation("command.autotorch.server.gui_opened"));
    }

    /**
     * 恢复默认值：scope 为空表示全部，分组枚举名、配置键或 TOML 配置节前缀都按服务端的
     * 范围解析规则处理。未知范围给出与管理员界面一致的提示。
     */
    private void reset(MinecraftServer server, ICommandSender sender, String scope) throws CommandException {
        if (!ServerConfig.resetScope(scope)) {
            throw new CommandException("screen.autotorch.server_config_unknown_key", scope);
        }
        ServerConfigNetwork.broadcast(server);
        if (scope.isEmpty()) {
            notifyOperators(sender, this, "command.autotorch.server.defaults");
        } else {
            notifyOperators(sender, this, "screen.autotorch.server_config_reset_done", scope);
        }
    }

    /**
     * 先按 1.13.2 命令的顺序校验类型、取值与间距交叉约束，再交给 ServerConfig 统一写入，
     * 保证命令与管理员界面使用同一份校验逻辑。
     */
    private void set(MinecraftServer server, ICommandSender sender, Value definition, String raw)
            throws CommandException {
        IntValue intDefinition = null;
        if (definition instanceof BooleanValue) {
            if (!"true".equalsIgnoreCase(raw) && !"false".equalsIgnoreCase(raw)) {
                throw new CommandException("command.autotorch.server.expected_boolean");
            }
        } else if (definition instanceof IntValue) {
            intDefinition = (IntValue) definition;
            int parsed;
            try {
                parsed = Integer.parseInt(raw);
            } catch (NumberFormatException exception) {
                throw new CommandException("command.autotorch.server.expected_integer");
            }
            if (parsed < intDefinition.minValue() || parsed > intDefinition.maxValue()) {
                throw new CommandException("command.autotorch.server.out_of_range",
                        intDefinition.minValue(), intDefinition.maxValue());
            }
        } else {
            throw new CommandException("command.autotorch.server.unsupported_type");
        }
        ServerConfig.SetResult result = ServerConfig.setFromText(definition.key(), raw);
        switch (result.status()) {
            case OK:
                ServerConfigNetwork.broadcast(server);
                notifyOperators(sender, this,
                        "command.autotorch.server.value", definition.key(), ServerConfig.get(definition.key()));
                return;
            case UNKNOWN_KEY:
                throw new CommandException("command.autotorch.server.unknown_key", definition.key());
            case UNSUPPORTED_TYPE:
                throw new CommandException("command.autotorch.server.unsupported_type");
            case OUT_OF_RANGE:
                if (intDefinition == null) {
                    throw new CommandException("command.autotorch.server.unsupported_type");
                }
                throw new CommandException("command.autotorch.server.out_of_range",
                        intDefinition.minValue(), intDefinition.maxValue());
            case SPACING_ORDER:
                throw new CommandException("command.autotorch.server.spacing_order");
            case INVALID_VALUE:
            default:
                throw new CommandException(definition instanceof BooleanValue
                        ? "command.autotorch.server.expected_boolean"
                        : "command.autotorch.server.expected_integer");
        }
    }

    @Override
    public List<String> addTabCompletionOptions(ICommandSender sender, String[] arguments, BlockPos targetPos) {
        if (arguments.length == 0) return Collections.emptyList();
        if (arguments.length == 1) return LegacyCommandCompletions.matching(arguments[0], "help", "serverconfig");
        if ("serverconfig".equals(arguments[0])) return LegacyCommandCompletions.serverConfig(arguments);
        return Collections.emptyList();
    }
}
