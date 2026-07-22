package com.sakurakugu.autotorch.server;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.sakurakugu.autotorch.AutoTorchRules;
import com.sakurakugu.autotorch.network.AreaShape;
import com.sakurakugu.autotorch.network.AreaZone;
import com.sakurakugu.autotorch.network.StartLightingPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.MinecraftServer;

/** 按玩家管理照明任务，并在每个服务端刻推进任务。 */
public final class LightingTaskManager {
    private static final Map<UUID, LightingTask> TASKS = new HashMap<>();
    private static int roundRobinStart;

    private LightingTaskManager() {
    }

    public static void start(ServerPlayer player, StartLightingPayload payload) {
        // 网络载荷不可信，所有会影响扫描范围和资源消耗的参数都在服务端校验。
        if (!player.mayBuild()) {
            player.sendSystemMessage(Component.translatable("message.autotorch.no_build_permission"));
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
            player.sendSystemMessage(Component.translatable("message.autotorch.area_too_large",
                    ServerConfig.maxBoxAxisLength(), ServerConfig.maxSphereRadius()));
            return;
        }
        if (!player.level().isInWorldBounds(min) || !player.level().isInWorldBounds(max)) {
            player.sendSystemMessage(Component.translatable("message.autotorch.outside_world"));
            return;
        }

        if (payload.maxTorches() < 0
                || payload.maxTorches() > ServerConfig.maxTorchesPerTask()
                || (payload.maxTorches() == 0 && !ServerConfig.allowsUnlimitedTorches())) {
            player.sendSystemMessage(Component.translatable("message.autotorch.invalid_settings"));
            return;
        }
        if (payload.minSpacing() < ServerConfig.minSpacing()
                || payload.minSpacing() > ServerConfig.maxSpacing()) {
            player.sendSystemMessage(Component.translatable("message.autotorch.invalid_settings"));
            return;
        }
        if (payload.exclusions().size() > ServerConfig.maxExclusions()
                || payload.exclusions().stream().anyMatch(zone -> !isValidZone(zone))) {
            player.sendSystemMessage(Component.translatable("message.autotorch.invalid_settings"));
            return;
        }
        if (!TASKS.containsKey(player.getUUID()) && TASKS.size() >= ServerConfig.maxConcurrentTasks()) {
            player.sendSystemMessage(Component.translatable("message.autotorch.server_busy"));
            return;
        }

        int maxTorches = payload.maxTorches();
        int minSpacing = payload.minSpacing();
        boolean consumeTorches = AutoTorchRules.consumesInventoryTorches(
                player.isCreative(), payload.consumeTorches(), ServerConfig.survivalConsumesTorches(),
                player.level().getServer().isSingleplayerOwner(player.nameAndId()));

        LightingTask task = new LightingTask(
                player.level(), selection, maxTorches, minSpacing,
                consumeTorches, payload.undergroundOnly(),
                payload.exclusions(), player.getUUID()
        );
        // 同一玩家只保留一个任务，新任务会替换尚未完成的旧任务。
        TASKS.put(player.getUUID(), task);
        player.sendSystemMessage(Component.translatable("message.autotorch.started", volume));
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

    public static void cancel(ServerPlayer player) {
        if (TASKS.remove(player.getUUID()) != null) {
            player.sendSystemMessage(Component.empty(), true);
            player.sendSystemMessage(Component.translatable("message.autotorch.cancelled"));
        } else {
            player.sendSystemMessage(Component.translatable("message.autotorch.no_task"));
        }
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
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (task != null && player != null) {
                task.tickProgress(player);
            }
        }

        for (int offset = 0; offset < taskCount && scanRemaining > 0 && placeRemaining > 0; offset++) {
            UUID playerId = players.get((roundRobinStart + offset) % taskCount);
            LightingTask task = TASKS.get(playerId);
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
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

}
