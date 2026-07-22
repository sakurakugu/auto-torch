package com.sakurakugu.autotorch.client;

/** 客户端保存的最近一次服务端权威配置，仅用于界面显示。 */
public final class ServerConfigState {
    private static volatile boolean survivalConsumesTorches = true;

    private ServerConfigState() {
    }

    public static boolean survivalConsumesTorches() {
        return survivalConsumesTorches;
    }

    public static void setSurvivalConsumesTorches(boolean value) {
        survivalConsumesTorches = value;
    }
}
