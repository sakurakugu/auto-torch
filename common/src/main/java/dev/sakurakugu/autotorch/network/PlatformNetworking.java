package dev.sakurakugu.autotorch.network;

import java.util.Objects;
import java.util.function.Consumer;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client-to-server packet bridge installed by the active loader. */
public final class PlatformNetworking {
    private static Consumer<CustomPacketPayload> sender = payload -> {
        throw new IllegalStateException("Platform networking has not been initialized");
    };

    private PlatformNetworking() {
    }

    public static void installSender(Consumer<CustomPacketPayload> value) {
        sender = Objects.requireNonNull(value);
    }

    public static void sendToServer(CustomPacketPayload payload) {
        sender.accept(payload);
    }
}
