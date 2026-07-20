package dev.sakurakugu.autotorch;

import dev.sakurakugu.autotorch.network.ModNetworking;
import dev.sakurakugu.autotorch.server.LightingTaskManager;
import dev.sakurakugu.autotorch.server.SelectionToolEvents;
import dev.sakurakugu.autotorch.server.ServerConfig;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;

/** 模组的公共入口，负责注册网络协议与仅在服务端执行的事件监听器。 */
@Mod(AutoTorchMod.MOD_ID)
public final class AutoTorchMod {
    public static final String MOD_ID = "autotorch";

    public AutoTorchMod(IEventBus modBus, ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.SERVER, ServerConfig.SPEC);
        // 网络载荷注册在模组事件总线上，游戏逻辑事件注册在 NeoForge 全局事件总线上。
        modBus.addListener(ModNetworking::register);
        NeoForge.EVENT_BUS.addListener(LightingTaskManager::onServerTick);
        NeoForge.EVENT_BUS.addListener(SelectionToolEvents::onLeftClick);
        NeoForge.EVENT_BUS.addListener(SelectionToolEvents::onRightClick);
    }
}
