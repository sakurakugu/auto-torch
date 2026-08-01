package com.sakurakugu.autotorch.forge;

import java.util.List;

import com.sakurakugu.autotorch.client.AutoTorchClientCommands;
import net.minecraft.client.Minecraft;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;

/** 将共享客户端命令接入 Forge 1.9.4 的本地执行与补全 API。 */
final class LegacyAutoTorchClientCommand extends CommandBase {
    @Override public String getCommandName() { return "autotorch"; }
    @Override public String getCommandUsage(ICommandSender sender) { return "command.autotorch.help.title"; }
    @Override public int getRequiredPermissionLevel() { return 0; }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] arguments) throws CommandException {
        String message = "/autotorch" + (arguments.length == 0 ? "" : " " + String.join(" ", arguments));
        if (arguments.length > 0 && "serverconfig".equals(arguments[0])) {
            // 客户端同名命令优先于服务端命令，这一分支必须绕过本地命令处理器。
            Minecraft.getMinecraft().thePlayer.sendChatMessage(message);
            return;
        }
        AutoTorchClientCommands.tryExecute(message);
    }

    @Override
    public List<String> getTabCompletionOptions(
            MinecraftServer server, ICommandSender sender, String[] arguments, BlockPos targetPos) {
        String command = "autotorch " + String.join(" ", arguments);
        return AutoTorchClientCommands.suggestions(command);
    }
}
