package com.sakurakugu.autotorch.forge;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.sakurakugu.autotorch.client.AutoTorchClientCommands;
import net.minecraft.client.Minecraft;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;

/**
 * 将共享客户端命令接入 Forge 1.11.2 的本地执行与补全 API。
 * serverconfig 由服务端执行，本地只转发到服务端并透传键名补全。
 */
final class LegacyAutoTorchClientCommand extends CommandBase {
    @Override public String getName() { return "autotorch"; }
    @Override public String getUsage(ICommandSender sender) { return "command.autotorch.help.title"; }
    @Override public int getRequiredPermissionLevel() { return 0; }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] arguments) throws CommandException {
        String message = "/autotorch" + (arguments.length == 0 ? "" : " " + String.join(" ", arguments));
        if (arguments.length > 0 && "serverconfig".equals(arguments[0])) {
            // 客户端同名命令优先于服务端命令，这一分支必须绕过本地命令处理器。
            if (Minecraft.getMinecraft().player == null) return;
            Minecraft.getMinecraft().player.sendChatMessage(message);
            return;
        }
        AutoTorchClientCommands.tryExecute(message);
    }

    @Override
    public List<String> getTabCompletions(
            MinecraftServer server, ICommandSender sender, String[] arguments, BlockPos targetPos) {
        if (arguments.length == 0) return Collections.emptyList();
        if (arguments.length == 1) {
            // 本地命令树里没有 serverconfig，补全时手动并入，方便管理员输入服务端命令。
            Set<String> suggestions = new LinkedHashSet<>(
                    AutoTorchClientCommands.suggestions("autotorch " + arguments[0]));
            suggestions.addAll(LegacyCommandCompletions.matching(arguments[0], "help", "serverconfig"));
            return new ArrayList<>(suggestions);
        }
        if ("serverconfig".equals(arguments[0])) {
            return LegacyCommandCompletions.serverConfig(arguments);
        }
        return AutoTorchClientCommands.suggestions("autotorch " + String.join(" ", arguments));
    }
}
