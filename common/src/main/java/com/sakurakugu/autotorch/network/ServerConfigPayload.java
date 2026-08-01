package com.sakurakugu.autotorch.network;

import com.sakurakugu.autotorch.AutoTorch;
import com.sakurakugu.autotorch.config.ConfigDefinitions;
import com.sakurakugu.autotorch.server.ServerConfig;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

/** 服务端在玩家登录后同步会影响客户端显示的权威配置。 */
public record ServerConfigPayload(
        boolean lightingTaskEnabled,
        boolean survivalConsumesTorches,
        int maxBoxAxisLength,
        int maxSphereRadius,
        int maxExclusions,
        int maxTorchesPerTask,
        boolean allowsUnlimitedTorches,
        int minSpacing,
        int maxSpacing
) implements AutoTorchPayload {
    public static final ResourceLocation ID = ResourceLocation.tryBuild(AutoTorch.MOD_ID, "server_config");

    public static ServerConfigPayload decode(FriendlyByteBuf buffer) {
        return new ServerConfigPayload(
                buffer.readBoolean(),
                buffer.readBoolean(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readBoolean(),
                buffer.readVarInt(),
                buffer.readVarInt()
        );
    }

    @Override
    public void write(FriendlyByteBuf buffer) {
        buffer.writeBoolean(lightingTaskEnabled);
        buffer.writeBoolean(survivalConsumesTorches);
        buffer.writeVarInt(maxBoxAxisLength);
        buffer.writeVarInt(maxSphereRadius);
        buffer.writeVarInt(maxExclusions);
        buffer.writeVarInt(maxTorchesPerTask);
        buffer.writeBoolean(allowsUnlimitedTorches);
        buffer.writeVarInt(minSpacing);
        buffer.writeVarInt(maxSpacing);
    }

    public static ServerConfigPayload current() {
        return new ServerConfigPayload(
                ServerConfig.lightingTaskEnabled(),
                ServerConfig.survivalConsumesTorches(),
                ServerConfig.maxBoxAxisLength(),
                ServerConfig.maxSphereRadius(),
                ServerConfig.maxExclusions(),
                ServerConfig.maxTorchesPerTask(),
                ServerConfig.allowsUnlimitedTorches(),
                ServerConfig.minSpacing(),
                ServerConfig.maxSpacing()
        );
    }

    public static ServerConfigPayload defaults() {
        return new ServerConfigPayload(
                ConfigDefinitions.LIGHTING_TASK_ENABLED.defaultValue(),
                ConfigDefinitions.GAMEPLAY_SURVIVAL_CONSUMES_TORCHES.defaultValue(),
                ConfigDefinitions.LIMIT_MAX_BOX_AXIS_LENGTH.defaultValue(),
                ConfigDefinitions.LIMIT_MAX_SPHERE_RADIUS.defaultValue(),
                ConfigDefinitions.LIMIT_MAX_EXCLUSIONS.defaultValue(),
                ConfigDefinitions.LIMIT_MAX_TORCHES_PER_TASK.defaultValue(),
                ConfigDefinitions.LIMIT_ALLOW_UNLIMITED_TORCHES.defaultValue(),
                ConfigDefinitions.LIMIT_MIN_SPACING.defaultValue(),
                ConfigDefinitions.LIMIT_MAX_SPACING.defaultValue()
        );
    }

    @Override
    public ResourceLocation id() {
        return ID;
    }
}
