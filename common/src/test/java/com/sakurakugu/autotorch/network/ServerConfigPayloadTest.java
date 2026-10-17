package com.sakurakugu.autotorch.network;

import java.util.List;

import com.sakurakugu.autotorch.config.ConfigDefinitions;
import com.sakurakugu.autotorch.server.ServerConfig;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 服务端配置快照必须覆盖全部服务端配置项，管理员界面才能完整显示。 */
class ServerConfigPayloadTest {
    @Test
    void payloadsCoverEveryServerSetting() {
        for (ServerConfigPayload payload : List.of(ServerConfigPayload.defaults(), ServerConfigPayload.current())) {
            assertEquals(ServerConfig.definitions().size(), payload.values().size());
            for (ConfigDefinitions.Value definition : ServerConfig.definitions()) {
                assertTrue(payload.values().containsKey(definition.key()), definition.key());
                assertNotNull(payload.values().get(definition.key()), definition.key());
            }
        }
    }

    @Test
    void defaultsUseDefinitionDefaultText() {
        ServerConfigPayload payload = ServerConfigPayload.defaults();
        for (ConfigDefinitions.Value definition : ServerConfig.definitions()) {
            assertEquals(definition.defaultValueText(), payload.values().get(definition.key()), definition.key());
        }
    }

    @Test
    void codecRoundTripsConfigurationSnapshot() {
        ServerConfigPayload payload = ServerConfigPayload.current();
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        ServerConfigPayload.STREAM_CODEC.encode(buffer, payload);
        assertEquals(payload, ServerConfigPayload.STREAM_CODEC.decode(buffer));
    }

    @Test
    void codecRoundTripsAdminScreenPackets() {
        ServerConfigSetPayload set = new ServerConfigSetPayload("limits.maxSphereRadius", "80");
        ServerConfigResetPayload reset = new ServerConfigResetPayload("LIMITS");
        ServerConfigResultPayload result = new ServerConfigResultPayload(true,
                "screen.autotorch.server_config_saved", "limits.maxSphereRadius");

        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        ServerConfigSetPayload.STREAM_CODEC.encode(buffer, set);
        ServerConfigResetPayload.STREAM_CODEC.encode(buffer, reset);
        ServerConfigResultPayload.STREAM_CODEC.encode(buffer, result);

        assertEquals(set, ServerConfigSetPayload.STREAM_CODEC.decode(buffer));
        assertEquals(reset, ServerConfigResetPayload.STREAM_CODEC.decode(buffer));
        assertEquals(result, ServerConfigResultPayload.STREAM_CODEC.decode(buffer));
    }
}
