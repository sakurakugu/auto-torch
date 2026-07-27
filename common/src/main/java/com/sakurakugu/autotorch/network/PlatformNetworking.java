package com.sakurakugu.autotorch.network;

import java.util.Objects;
import java.util.function.Consumer;

/** 由当前加载器安装的客户端到服务端网络桥。 */
public final class PlatformNetworking {
    private static Consumer<AutoTorchPayload> sender = payload -> {
        throw new IllegalStateException("Platform networking has not been initialized");
    };

    private PlatformNetworking() {
    }

    public static void installSender(Consumer<AutoTorchPayload> value) {
        sender = Objects.requireNonNull(value);
    }

    public static void sendToServer(AutoTorchPayload payload) {
        sender.accept(payload);
    }
}
