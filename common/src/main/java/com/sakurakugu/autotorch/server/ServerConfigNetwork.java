package com.sakurakugu.autotorch.server;

import java.util.Objects;
import java.util.function.BiConsumer;

import com.sakurakugu.autotorch.network.OpenServerConfigScreenPayload;
import com.sakurakugu.autotorch.network.ServerConfigPayload;
import com.sakurakugu.autotorch.network.ServerConfigResetPayload;
import com.sakurakugu.autotorch.network.ServerConfigResultPayload;
import com.sakurakugu.autotorch.network.ServerConfigSetPayload;
import com.sakurakugu.autotorch.network.AutoTorchPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * 服务端配置同步与管理员界面载荷处理。
 * 发送器由各加载器安装；未安装时（例如单元测试）所有发送会被忽略。
 */
public final class ServerConfigNetwork {
    private static final String MESSAGE_SAVED = "screen.autotorch.server_config_saved";
    private static final String MESSAGE_RESET_DONE = "screen.autotorch.server_config_reset_done";
    private static final String MESSAGE_INVALID = "screen.autotorch.server_config_invalid_value";
    private static final String MESSAGE_UNKNOWN_KEY = "screen.autotorch.server_config_unknown_key";
    private static final String MESSAGE_UNSUPPORTED = "screen.autotorch.server_config_unsupported_type";
    private static final String MESSAGE_SPACING = "screen.autotorch.server_config_spacing_order";
    private static final String MESSAGE_DENIED = "screen.autotorch.server_config_permission_denied";

    private static volatile BiConsumer<ServerPlayer, AutoTorchPayload> sender = (player, payload) -> { };

    private ServerConfigNetwork() {
    }

    public static void installSender(BiConsumer<ServerPlayer, AutoTorchPayload> value) {
        sender = Objects.requireNonNull(value);
    }

    /** 向指定玩家同步最新的全部服务端配置。 */
    public static void sendConfig(ServerPlayer player) {
        sender.accept(player, ServerConfigPayload.current());
    }

    /** 先同步配置再请求客户端打开管理员界面，保证界面初始化时读到最新值。 */
    public static void sendOpenScreen(ServerPlayer player) {
        sendConfig(player);
        sender.accept(player, new OpenServerConfigScreenPayload());
    }

    /** 配置变化后广播给全部在线玩家，客户端据此刷新限制与界面。 */
    public static void broadcast(MinecraftServer server) {
        if (server == null) return;
        server.getPlayerList().getPlayers().forEach(ServerConfigNetwork::sendConfig);
    }

    /** 管理员界面请求修改一项配置。 */
    public static void handleSet(ServerPlayer player, ServerConfigSetPayload payload) {
        if (!canManage(player)) {
            sendResult(player, false, MESSAGE_DENIED, "");
            return;
        }
        ServerConfig.SetResult result = ServerConfig.setFromText(payload.key(), payload.value());
        switch (result.status()) {
            case OK -> {
                broadcast(player.getServer());
                sendResult(player, true, MESSAGE_SAVED, payload.key());
            }
            case UNKNOWN_KEY -> sendResult(player, false, MESSAGE_UNKNOWN_KEY, payload.key());
            case UNSUPPORTED_TYPE -> sendResult(player, false, MESSAGE_UNSUPPORTED, payload.key());
            case OUT_OF_RANGE, INVALID_VALUE -> sendResult(player, false, MESSAGE_INVALID, payload.key());
            case SPACING_ORDER -> sendResult(player, false, MESSAGE_SPACING, "");
        }
    }

    /** 管理员界面请求恢复默认值。 */
    public static void handleReset(ServerPlayer player, ServerConfigResetPayload payload) {
        if (!canManage(player)) {
            sendResult(player, false, MESSAGE_DENIED, "");
            return;
        }
        if (!ServerConfig.resetScope(payload.scope())) {
            sendResult(player, false, MESSAGE_UNKNOWN_KEY, payload.scope());
            return;
        }
        broadcast(player.getServer());
        sendResult(player, true, MESSAGE_RESET_DONE, payload.scope());
    }

    /** 管理员界面只允许拥有游戏管理员权限的玩家修改服务端配置。 */
    public static boolean canManage(ServerPlayer player) {
        return player != null && player.hasPermissions(2);
    }

    private static void sendResult(ServerPlayer player, boolean success, String messageKey, String detail) {
        sender.accept(player, new ServerConfigResultPayload(success, messageKey, detail));
    }
}

