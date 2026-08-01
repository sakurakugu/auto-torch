package com.sakurakugu.autotorch.network;

import io.netty.buffer.Unpooled;
import net.minecraft.network.PacketBuffer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TaskStatusPayloadTest {
    @Test
    void roundTripsRunningStatus() {
        PacketBuffer buffer = new PacketBuffer(Unpooled.buffer());

        try {
            new TaskStatusPayload(true, 73, 128).write(buffer);
            TaskStatusPayload decoded = TaskStatusPayload.decode(buffer);

            assertTrue(decoded.running());
            assertEquals(73, decoded.percent());
            assertEquals(128, decoded.placed());
        } finally {
            buffer.release();
        }
    }

    @Test
    void roundTripsEmptyRequestAndIdleStatus() {
        PacketBuffer buffer = new PacketBuffer(Unpooled.buffer());

        try {
            new TaskStatusRequestPayload().write(buffer);
            TaskStatusRequestPayload.decode(buffer);
            new TaskStatusPayload(false, 0, 0).write(buffer);
            TaskStatusPayload decoded = TaskStatusPayload.decode(buffer);

            assertFalse(decoded.running());
            assertEquals(0, decoded.percent());
            assertEquals(0, decoded.placed());
        } finally {
            buffer.release();
        }
    }
}
