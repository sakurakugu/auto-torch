package com.sakurakugu.autotorch.network;

import com.sakurakugu.autotorch.compat.BlockPos;
import io.netty.buffer.Unpooled;
import net.minecraft.network.PacketBuffer;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StartLightingPayloadTest {
    @Test
    void roundTripsLegacyIntegerCoordinates() {
        AreaZone selection = new AreaZone(
                AreaShape.BOX, new BlockPos(-123, 4, 567), new BlockPos(321, 200, -765));
        AreaZone exclusion = new AreaZone(
                AreaShape.SPHERE, new BlockPos(-8, 64, 12), new BlockPos(-3, 64, 12));
        StartLightingPayload original = new StartLightingPayload(
                selection, 256, 7, 3, true, false, Collections.singletonList(exclusion));
        PacketBuffer buffer = new PacketBuffer(Unpooled.buffer());

        try {
            original.write(buffer);
            StartLightingPayload decoded = StartLightingPayload.decode(buffer);

            assertEquals(selection, decoded.selection());
            assertEquals(256, decoded.maxTorches());
            assertEquals(7, decoded.minSpacing());
            assertEquals(3, decoded.lightThreshold());
            assertTrue(decoded.consumeTorches());
            assertFalse(decoded.undergroundOnly());
            assertEquals(Collections.singletonList(exclusion), decoded.exclusions());
        } finally {
            buffer.release();
        }
    }
}
