package com.sakurakugu.autotorch.forge;

import java.util.Collections;
import java.util.List;

import com.sakurakugu.autotorch.config.ConfigDefinitions.BooleanValue;
import com.sakurakugu.autotorch.config.ConfigDefinitions.IntValue;
import com.sakurakugu.autotorch.config.ConfigDefinitions.Value;
import com.sakurakugu.autotorch.network.ServerConfigPayload;
import com.sakurakugu.autotorch.server.ServerConfig;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;

/** 将服务端配置命令接入 Forge 1.10.2 的 ICommand API。 */
final class LegacyAutoTorchServerCommand extends CommandBase {
    @Override public String getCommandName() { return "autotorch"; }
    @Override public String getCommandUsage(ICommandSender sender) { return "command.autotorch.server.help"; }
    @Override public int getRequiredPermissionLevel() { return 2; }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] arguments) throws CommandException {
        if (arguments.length == 0 || "help".equals(arguments[0])) {
            sender.addChatMessage(new TextComponentTranslation("command.autotorch.server.help"));
            return;
        }
        if (!"serverconfig".equals(arguments[0]) || arguments.length < 2) {
            throw new CommandException("command.autotorch.server.help");
        }
        String action = arguments[1];
        if ("defaults".equals(action)) {
            ServerConfig.resetDefaults();
            sync(server);
            notifyCommandListener(sender, this, "command.autotorch.server.defaults");
            return;
        }
        if (arguments.length < 3) throw new CommandException("command.autotorch.server.help");
        String key = arguments[2];
        Value definition = ServerConfig.definition(key);
        if (definition == null) throw new CommandException("command.autotorch.server.unknown_key", key);
        if ("get".equals(action)) {
            sender.addChatMessage(new TextComponentTranslation(
                    "command.autotorch.server.value", key, ServerConfig.get(key)));
            return;
        }
        if (!"set".equals(action) || arguments.length < 4) {
            throw new CommandException("command.autotorch.server.help");
        }
        Object value = parseValue(definition, arguments[3]);
        ServerConfig.set(key, value);
        sync(server);
        notifyCommandListener(sender, this, "command.autotorch.server.value", key, ServerConfig.get(key));
    }

    private static Object parseValue(Value definition, String raw) throws CommandException {
        if (definition instanceof BooleanValue) {
            if (!"true".equalsIgnoreCase(raw) && !"false".equalsIgnoreCase(raw)) {
                throw new CommandException("command.autotorch.server.expected_boolean");
            }
            return Boolean.parseBoolean(raw);
        }
        if (definition instanceof IntValue) {
            IntValue intValue = (IntValue) definition;
            return parseInt(raw, intValue.minValue(), intValue.maxValue());
        }
        throw new CommandException("command.autotorch.server.unsupported_type");
    }

    private static void sync(MinecraftServer server) {
        for (Object player : server.getPlayerList().getPlayerList()) {
            ForgeNetworking.sendToPlayer((EntityPlayerMP) player, ServerConfigPayload.current());
        }
    }

    @Override
    public List<String> getTabCompletionOptions(
            MinecraftServer server, ICommandSender sender, String[] arguments, BlockPos targetPos) {
        if (arguments.length == 1) return getListOfStringsMatchingLastWord(arguments, "help", "serverconfig");
        if (arguments.length == 2 && "serverconfig".equals(arguments[0])) {
            return getListOfStringsMatchingLastWord(arguments, "get", "set", "defaults");
        }
        if (arguments.length == 3 && "serverconfig".equals(arguments[0])) {
            return getListOfStringsMatchingLastWord(arguments,
                    ServerConfig.definitions().stream().map(Value::key).toArray(String[]::new));
        }
        return Collections.emptyList();
    }
}
