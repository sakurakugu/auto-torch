package com.sakurakugu.autotorch.server;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import com.sakurakugu.autotorch.AutoTorchRules;
import com.sakurakugu.autotorch.config.ConfigDefinitions;
import com.sakurakugu.autotorch.network.AreaShape;
import com.sakurakugu.autotorch.network.AreaZone;
import com.sakurakugu.autotorch.network.StartLightingPayload;
import com.sakurakugu.autotorch.network.TaskStatusPayload;
import com.sakurakugu.autotorch.compat.BlockPos;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.play.server.S02PacketChat;
import net.minecraft.server.MinecraftServer;

/** 按玩家管理照明任务，并在每个服务端刻推进任务。 */
public final class LightingTaskManager {
    private static final Map<UUID, LightingTask> TASKS = new HashMap<>();
    private static int roundRobinStart;

    private LightingTaskManager() {
    }

    static void sendSystemMessage(EntityPlayerMP player, IChatComponent message) {
        sendSystemMessage(player, message, false);
    }

    static void sendSystemMessage(EntityPlayerMP player, IChatComponent message, boolean overlay) {
        player.playerNetServerHandler.sendPacket(new S02PacketChat(message, !overlay));
    }

    public static void start(EntityPlayerMP player, StartLightingPayload payload) {
        // 网络载荷不可信，所有会影响扫描范围和资源消耗的参数都在服务端校验。
        if (!player.capabilities.allowEdit) {
            sendSystemMessage(player, new ChatComponentTranslation("message.autotorch.no_build_permission"));
            return;
        }

        AreaZone selection = payload.selection();
        BlockPos min = selection.min();
        BlockPos max = selection.max();

        long sizeX = (long) max.getX() - min.getX() + 1L;
        long sizeY = (long) max.getY() - min.getY() + 1L;
        long sizeZ = (long) max.getZ() - min.getZ() + 1L;
        long volume = sizeX * sizeY * sizeZ;
        int maxSelectionBoundAxis = Math.max(
                ServerConfig.maxBoxAxisLength(), ServerConfig.maxSphereRadius() * 2 + 1);
        long maxSelectionBoundVolume = (long) maxSelectionBoundAxis
                * maxSelectionBoundAxis * maxSelectionBoundAxis;

        if (!isValidZone(selection) || sizeX > maxSelectionBoundAxis || sizeY > maxSelectionBoundAxis
                || sizeZ > maxSelectionBoundAxis || volume > maxSelectionBoundVolume) {
            sendSystemMessage(player, new ChatComponentTranslation("message.autotorch.area_too_large",
                    ServerConfig.maxBoxAxisLength(), ServerConfig.maxSphereRadius()));
            return;
        }
        int scanMinY = Math.max(min.getY(), 0);
        int scanMaxY = Math.min(max.getY(), player.getServerForPlayer().getHeight() - 1);
        if (scanMinY > scanMaxY) {
            sendSystemMessage(player, new ChatComponentTranslation("message.autotorch.outside_world"));
            return;
        }
        BlockPos scanMin = new BlockPos(min.getX(), scanMinY, min.getZ());
        BlockPos scanMax = new BlockPos(max.getX(), scanMaxY, max.getZ());
        if (!isValidWorldPosition(scanMin) || !isValidWorldPosition(scanMax)) {
            sendSystemMessage(player, new ChatComponentTranslation("message.autotorch.outside_world"));
            return;
        }
        long scanVolume = sizeX * ((long) scanMaxY - scanMinY + 1L) * sizeZ;

        if (payload.maxTorches() < 0
                || payload.maxTorches() > ServerConfig.maxTorchesPerTask()
                || (payload.maxTorches() == 0 && !ServerConfig.allowsUnlimitedTorches())) {
            sendSystemMessage(player, new ChatComponentTranslation("message.autotorch.invalid_settings"));
            return;
        }
        if (payload.minSpacing() < ServerConfig.minSpacing()
                || payload.minSpacing() > ServerConfig.maxSpacing()) {
            sendSystemMessage(player, new ChatComponentTranslation("message.autotorch.invalid_settings"));
            return;
        }
        if (payload.lightThreshold() < ConfigDefinitions.TASK_DEFAULT_LIGHT_THRESHOLD.minValue()
                || payload.lightThreshold() > ConfigDefinitions.TASK_DEFAULT_LIGHT_THRESHOLD.maxValue()) {
            sendSystemMessage(player, new ChatComponentTranslation("message.autotorch.invalid_settings"));
            return;
        }
        if (payload.exclusions().size() > ServerConfig.maxExclusions()
                || payload.exclusions().stream().anyMatch(zone -> !isValidZone(zone))) {
            sendSystemMessage(player, new ChatComponentTranslation("message.autotorch.invalid_settings"));
            return;
        }
        if (!TASKS.containsKey(player.getUniqueID()) && TASKS.size() >= ServerConfig.maxConcurrentTasks()) {
            sendSystemMessage(player, new ChatComponentTranslation("message.autotorch.server_busy"));
            return;
        }

        int maxTorches = payload.maxTorches();
        int minSpacing = payload.minSpacing();
        int lightThreshold = payload.lightThreshold();
        boolean consumeTorches = AutoTorchRules.consumesInventoryTorches(
                player.capabilities.isCreativeMode, payload.consumeTorches(), ServerConfig.survivalConsumesTorches(),
                player.mcServer.isSinglePlayer()
                        && Objects.equals(player.mcServer.getServerOwner(),
                        player.getGameProfile().getName()));

        LightingTask task = new LightingTask(
                player.getServerForPlayer(), selection, scanMin, scanMax, maxTorches, minSpacing, lightThreshold,
                consumeTorches, payload.undergroundOnly(),
                payload.exclusions(), player.getUniqueID()
        );
        // 同一玩家只保留一个任务，新任务会替换尚未完成的旧任务。
        TASKS.put(player.getUniqueID(), task);
        sendSystemMessage(player, new ChatComponentTranslation("message.autotorch.started", scanVolume));
        task.showInitialProgress(player);
    }

    private static boolean isValidZone(AreaZone zone) {
        if (zone.shape() == AreaShape.SPHERE) {
            long maxRadiusSquared = (long) ServerConfig.maxSphereRadius() * ServerConfig.maxSphereRadius();
            return zone.radiusSquared() > 0L && zone.radiusSquared() <= maxRadiusSquared;
        }
        BlockPos min = zone.min();
        BlockPos max = zone.max();
        int maxAxisLength = ServerConfig.maxBoxAxisLength();
        return (long) max.getX() - min.getX() + 1L <= maxAxisLength
                && (long) max.getY() - min.getY() + 1L <= maxAxisLength
                && (long) max.getZ() - min.getZ() + 1L <= maxAxisLength;
    }

    private static boolean isValidWorldPosition(BlockPos pos) {
        return pos.getX() >= -30000000 && pos.getX() < 30000000
                && pos.getZ() >= -30000000 && pos.getZ() < 30000000
                && pos.getY() >= 0 && pos.getY() < 256;
    }

    public static void cancel(EntityPlayerMP player) {
        if (TASKS.remove(player.getUniqueID()) != null) {
            sendSystemMessage(player, new ChatComponentText(""), true);
            sendSystemMessage(player, new ChatComponentTranslation("message.autotorch.cancelled"));
        } else {
            sendSystemMessage(player, new ChatComponentTranslation("message.autotorch.no_task"));
        }
    }

    public static TaskStatusPayload status(EntityPlayerMP player) {
        LightingTask task = TASKS.get(player.getUniqueID());
        return task == null
                ? new TaskStatusPayload(false, 0, 0)
                : new TaskStatusPayload(true, task.progressPercent(), task.placedCount());
    }

    public static void onServerTick(MinecraftServer server) {
        if (TASKS.isEmpty()) {
            roundRobinStart = 0;
            return;
        }

        List<UUID> players = new ArrayList<>(TASKS.keySet());
        int taskCount = players.size();
        int scanRemaining = ServerConfig.globalScanBudgetPerTick();
        int placeRemaining = ServerConfig.globalPlaceBudgetPerTick();
        List<UUID> completed = new ArrayList<>();

        for (UUID playerId : players) {
            LightingTask task = TASKS.get(playerId);
            EntityPlayerMP player = findPlayer(server, playerId);
            if (task != null && player != null) {
                task.tickProgress(player);
            }
        }

        for (int offset = 0; offset < taskCount && scanRemaining > 0 && placeRemaining > 0; offset++) {
            UUID playerId = players.get((roundRobinStart + offset) % taskCount);
            LightingTask task = TASKS.get(playerId);
            EntityPlayerMP player = findPlayer(server, playerId);
            if (task == null || player == null) {
                completed.add(playerId);
                continue;
            }

            int tasksRemaining = taskCount - offset;
            int scanBudget = Math.min(ServerConfig.scanBudgetPerTaskTick(),
                    AutoTorchRules.divideRoundUp(scanRemaining, tasksRemaining));
            int placeBudget = Math.min(ServerConfig.placeBudgetPerTaskTick(),
                    AutoTorchRules.divideRoundUp(placeRemaining, tasksRemaining));
            LightingTask.TickResult result = task.tick(player, scanBudget, placeBudget);
            scanRemaining -= result.scanned();
            placeRemaining -= result.placed();
            if (result.done()) {
                completed.add(playerId);
            }
        }
        completed.forEach(TASKS::remove);
        roundRobinStart = (roundRobinStart + 1) % Math.max(1, taskCount);
    }

    private static EntityPlayerMP findPlayer(MinecraftServer server, UUID playerId) {
        for (Object entry : server.getConfigurationManager().playerEntityList) {
            EntityPlayerMP player = (EntityPlayerMP) entry;
            if (playerId.equals(player.getUniqueID())) return player;
        }
        return null;
    }

}
