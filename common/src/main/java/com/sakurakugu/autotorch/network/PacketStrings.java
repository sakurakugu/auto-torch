package com.sakurakugu.autotorch.network;

import net.minecraft.network.PacketBuffer;

/** 1.7.10 的字符串读写会抛出受检 IOException，统一在这里消化为运行时异常。 */
final class PacketStrings {
    private PacketStrings() {
    }

    static String read(PacketBuffer buffer, int maxLength) {
        try {
            return buffer.readStringFromBuffer(maxLength);
        } catch (Exception exception) {
            throw new RuntimeException(exception);
        }
    }

    static void write(PacketBuffer buffer, String value) {
        try {
            buffer.writeStringToBuffer(value);
        } catch (Exception exception) {
            throw new RuntimeException(exception);
        }
    }
}
