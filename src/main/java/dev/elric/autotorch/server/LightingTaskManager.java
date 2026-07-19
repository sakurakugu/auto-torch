package dev.elric.autotorch.server;

import dev.elric.autotorch.network.StartLightingPayload;
import dev.elric.autotorch.network.ExclusionZone;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** 按玩家管理照明任务，并在每个服务端刻推进任务。 */
public final class LightingTaskManager {
    private static final long MAX_VOLUME = 16_777_216L;
    private static final int MAX_AXIS_LENGTH = 256;
    private static final Map<UUID, LightingTask> TASKS = new HashMap<>();

    private LightingTaskManager() {
    }

    public static void start(ServerPlayer player, StartLightingPayload payload) {
        // 网络载荷不可信，所有会影响扫描范围和资源消耗的参数都在服务端校验。
        if (!player.mayBuild()) {
            player.sendSystemMessage(Component.translatable("message.autotorch.no_build_permission"));
            return;
        }

        BlockPos min = new BlockPos(
                Math.min(payload.first().getX(), payload.second().getX()),
                Math.min(payload.first().getY(), payload.second().getY()),
                Math.min(payload.first().getZ(), payload.second().getZ())
        );
        BlockPos max = new BlockPos(
                Math.max(payload.first().getX(), payload.second().getX()),
                Math.max(payload.first().getY(), payload.second().getY()),
                Math.max(payload.first().getZ(), payload.second().getZ())
        );

        long sizeX = (long) max.getX() - min.getX() + 1L;
        long sizeY = (long) max.getY() - min.getY() + 1L;
        long sizeZ = (long) max.getZ() - min.getZ() + 1L;
        long volume = sizeX * sizeY * sizeZ;

        if (sizeX > MAX_AXIS_LENGTH || sizeY > MAX_AXIS_LENGTH || sizeZ > MAX_AXIS_LENGTH || volume > MAX_VOLUME) {
            player.sendSystemMessage(Component.translatable("message.autotorch.area_too_large", MAX_AXIS_LENGTH, MAX_VOLUME));
            return;
        }
        if (!player.level().isInWorldBounds(min) || !player.level().isInWorldBounds(max)) {
            player.sendSystemMessage(Component.translatable("message.autotorch.outside_world"));
            return;
        }

        if (payload.maxTorches() < 0 || payload.maxTorches() > 4096) {
            player.sendSystemMessage(Component.translatable("message.autotorch.invalid_settings"));
            return;
        }
        if (payload.exclusions().size() > StartLightingPayload.MAX_EXCLUSIONS
                || payload.exclusions().stream().mapToInt(ExclusionZone::radius).anyMatch(radius -> radius < 1 || radius > 128)) {
            player.sendSystemMessage(Component.translatable("message.autotorch.invalid_settings"));
            return;
        }

        int maxTorches = payload.maxTorches();
        int minSpacing = Math.clamp(payload.minSpacing(), 3, 12);

        LightingTask task = new LightingTask(
                player.level(), min, max, maxTorches, minSpacing,
                payload.consumeTorches(), payload.undergroundOnly(),
                payload.exclusions(), player.getUUID()
        );
        // 同一玩家只保留一个任务，新任务会替换尚未完成的旧任务。
        TASKS.put(player.getUUID(), task);
        player.sendSystemMessage(Component.translatable("message.autotorch.started", volume));
    }

    public static void cancel(ServerPlayer player) {
        if (TASKS.remove(player.getUUID()) != null) {
            player.sendSystemMessage(Component.translatable("message.autotorch.cancelled"));
        } else {
            player.sendSystemMessage(Component.translatable("message.autotorch.no_task"));
        }
    }

    public static void onServerTick(ServerTickEvent.Post event) {
        Iterator<Map.Entry<UUID, LightingTask>> iterator = TASKS.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, LightingTask> entry = iterator.next();
            ServerPlayer player = event.getServer().getPlayerList().getPlayer(entry.getKey());
            // 玩家离线或 tick 返回已完成时，立即释放对应任务。
            if (player == null || entry.getValue().tick(player)) {
                iterator.remove();
            }
        }
    }
}
