package com.sakurakugu.autotorch.network;

import java.util.LinkedHashMap;
import java.util.Map;

import com.sakurakugu.autotorch.AutoTorch;
import com.sakurakugu.autotorch.config.ConfigDefinitions;
import com.sakurakugu.autotorch.server.ServerConfig;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

/**
 * 服务端向客户端同步的权威配置快照，覆盖全部服务端配置项。
 * 用键值对传输，新增服务端配置时不需要再改载荷结构。
 */
public record ServerConfigPayload(Map<String, String> values) implements AutoTorchPayload {
    public static final ResourceLocation ID = new ResourceLocation(AutoTorch.MOD_ID, "server_config");

    public ServerConfigPayload {
        values = Map.copyOf(values);
    }

    public static ServerConfigPayload decode(FriendlyByteBuf buffer) {
        return new ServerConfigPayload(buffer);
    }

    private ServerConfigPayload(FriendlyByteBuf buffer) {
        this(readValues(buffer));
    }

    private static Map<String, String> readValues(FriendlyByteBuf buffer) {
        int size = buffer.readVarInt();
        Map<String, String> read = new LinkedHashMap<>(Math.max(1, size));
        for (int index = 0; index < size; index++) {
            read.put(buffer.readUtf(), buffer.readUtf());
        }
        return read;
    }

    public void write(FriendlyByteBuf buffer) {
        buffer.writeVarInt(values.size());
        values.forEach((key, value) -> {
            buffer.writeUtf(key);
            buffer.writeUtf(value);
        });
    }

    /** 当前服务端实际生效的配置（包含间距上下限的归一化结果）。 */
    public static ServerConfigPayload current() {
        Map<String, String> values = new LinkedHashMap<>();
        for (ConfigDefinitions.Value definition : ServerConfig.definitions()) {
            values.put(definition.key(), String.valueOf(ServerConfig.get(definition.key())));
        }
        return new ServerConfigPayload(values);
    }

    /** 全部服务端配置的默认值，用于客户端在登录前显示。 */
    public static ServerConfigPayload defaults() {
        Map<String, String> values = new LinkedHashMap<>();
        for (ConfigDefinitions.Value definition : ServerConfig.definitions()) {
            values.put(definition.key(), definition.defaultValueText());
        }
        return new ServerConfigPayload(values);
    }

    @Override
    public ResourceLocation id() {
        return ID;
    }
}
