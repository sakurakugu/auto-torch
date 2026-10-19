package com.sakurakugu.autotorch.network;

import java.util.List;

import com.sakurakugu.autotorch.config.ConfigDefinitions;
import com.sakurakugu.autotorch.server.ServerConfig;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 服务端配置快照必须覆盖全部服务端配置项，管理员界面才能完整显示。 */
class ServerConfigPayloadTest {
    @Test
    void payloadsCoverEveryServerSetting() {
        for (ServerConfigPayload payload : java.util.Arrays.asList(ServerConfigPayload.defaults(), ServerConfigPayload.current())) {
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
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        payload.write(buffer);
        assertEquals(payload, ServerConfigPayload.decode(buffer));
    }

    @Test
    void codecRoundTripsAdminScreenPackets() {
        ServerConfigSetPayload set = new ServerConfigSetPayload("limits.maxSphereRadius", "80");
        ServerConfigResetPayload reset = new ServerConfigResetPayload("LIMITS");
        ServerConfigResultPayload result = new ServerConfigResultPayload(true,
                "screen.autotorch.server_config_saved", "limits.maxSphereRadius");

        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        set.write(buffer);
        reset.write(buffer);
        result.write(buffer);

        assertEquals(set, ServerConfigSetPayload.decode(buffer));
        assertEquals(reset, ServerConfigResetPayload.decode(buffer));
        assertEquals(result, ServerConfigResultPayload.decode(buffer));
    }
}
