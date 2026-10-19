package com.sakurakugu.autotorch.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;

/** 判断非透视模式下溺尸标记是否可以忽略水体显示。 */
@FunctionalInterface
public interface DrownedMarkerVisibility {
    boolean isVisible(ClientLevel level, Vec3 camera, LightOverlayState.Marker marker);
}
