package com.sakurakugu.autotorch.network;

import com.sakurakugu.autotorch.AutoTorch;
import com.sakurakugu.autotorch.config.ConfigDefinitions;
import com.sakurakugu.autotorch.server.ServerConfig;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** 服务端在玩家登录后同步会影响客户端显示的权威配置。 */
public record ServerConfigPayload(
        boolean survivalConsumesTorches,
        int maxBoxAxisLength,
        int maxSphereRadius,
        int maxExclusions,
        int maxTorchesPerTask,
        boolean allowsUnlimitedTorches,
        int minSpacing,
        int maxSpacing
) implements CustomPacketPayload {
    public static final Type<ServerConfigPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(AutoTorch.MOD_ID, "server_config")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, ServerConfigPayload> STREAM_CODEC =
            CustomPacketPayload.codec(ServerConfigPayload::write, ServerConfigPayload::new);

    private ServerConfigPayload(RegistryFriendlyByteBuf buffer) {
        this(
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

    private void write(RegistryFriendlyByteBuf buffer) {
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
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
