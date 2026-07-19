package dev.elric.autotorch;

import dev.elric.autotorch.network.ModNetworking;
import dev.elric.autotorch.server.LightingTaskManager;
import dev.elric.autotorch.server.SelectionToolEvents;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;

/** 模组的公共入口，负责注册网络协议与仅在服务端执行的事件监听器。 */
@Mod(AutoTorchMod.MOD_ID)
public final class AutoTorchMod {
    public static final String MOD_ID = "autotorch";

    public AutoTorchMod(IEventBus modBus) {
        // 网络载荷注册在模组事件总线上，游戏逻辑事件注册在 NeoForge 全局事件总线上。
        modBus.addListener(ModNetworking::register);
        NeoForge.EVENT_BUS.addListener(LightingTaskManager::onServerTick);
        NeoForge.EVENT_BUS.addListener(SelectionToolEvents::onLeftClick);
        NeoForge.EVENT_BUS.addListener(SelectionToolEvents::onRightClick);
    }
}
