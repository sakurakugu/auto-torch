package com.sakurakugu.autotorch.client;

import com.sakurakugu.autotorch.network.ServerConfigPayload;

/** 客户端保存的最近一次服务端权威配置，仅用于界面显示。 */
public final class ServerConfigState {
    private static volatile ServerConfigPayload config = ServerConfigPayload.defaults();

    private ServerConfigState() {
    }

    public static boolean survivalConsumesTorches() {
        return config.survivalConsumesTorches();
    }

    public static int maxBoxAxisLength() { return config.maxBoxAxisLength(); }
    public static int maxSphereRadius() { return config.maxSphereRadius(); }
    public static int maxExclusions() { return config.maxExclusions(); }
    public static int maxTorchesPerTask() { return config.maxTorchesPerTask(); }
    public static boolean allowsUnlimitedTorches() { return config.allowsUnlimitedTorches(); }
    public static int minSpacing() { return config.minSpacing(); }
    public static int maxSpacing() { return config.maxSpacing(); }

    public static void update(ServerConfigPayload value) { config = value; }
}
