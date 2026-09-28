# -*- coding: utf-8 -*-
"""客户端 System：保存本地配置、处理开关键，并把配置与界面状态同步给服务端。"""

import mod.client.extraClientApi as clientApi

from Script_NeteaseMod0413GTaU import config
from Script_NeteaseMod0413GTaU import nearby_logic

ClientSystem = clientApi.GetClientSystemCls()
compFactory = clientApi.GetEngineCompFactory()

# 设置面板完成前，临时用 H 键开关附近自动插火把
TOGGLE_NEARBY_KEY = str(clientApi.GetMinecraftEnum().KeyBoardType.KEY_H)


class AutoTorchClient(ClientSystem):

    def __init__(self, namespace, system_name):
        ClientSystem.__init__(self, namespace, system_name)
        level_id = clientApi.GetLevelId()
        self._config_comp = compFactory.CreateConfigClient(level_id)
        self._game_comp = compFactory.CreateGame(level_id)
        self._nearby = self._load_nearby()
        self._screen_open = None

        engine_ns = clientApi.GetEngineNamespace()
        engine_name = clientApi.GetEngineSystemName()
        self.ListenForEvent(engine_ns, engine_name, "UiInitFinished", self, self.OnUiInitFinished)
        self.ListenForEvent(engine_ns, engine_name, "OnKeyPressInGame", self, self.OnKeyPressInGame)
        self.ListenForEvent(engine_ns, engine_name, "OnScriptTickClient", self, self.OnScriptTickClient)

    def Destroy(self):
        self.UnListenAllEvents()

    def _load_nearby(self):
        return config.normalize_nearby(self._config_comp.GetConfigData(config.CLIENT_CONFIG_NAME, True))

    def _save_nearby(self):
        self._config_comp.SetConfigData(config.CLIENT_CONFIG_NAME, dict(self._nearby), True)

    def _sync_nearby(self):
        data = self.CreateEventData()
        data.update(self._nearby)
        self.NotifyToServer(config.EVENT_SYNC_NEARBY_CONFIG, data)

    def OnUiInitFinished(self, args):
        # 切换维度后也会触发，重新同步可覆盖服务端可能丢失的状态
        self._screen_open = None
        self._sync_nearby()

    def OnKeyPressInGame(self, args):
        if args.get("key") != TOGGLE_NEARBY_KEY or args.get("isDown") != "1":
            return
        if nearby_logic.is_screen_open(args.get("screenName")):
            return
        enabled = not self._nearby[config.NEARBY_ENABLED]
        self._nearby[config.NEARBY_ENABLED] = enabled
        self._save_nearby()
        self._sync_nearby()
        if enabled:
            message = clientApi.GenerateColor("GREEN") + "附近自动插火把：已开启"
        else:
            message = clientApi.GenerateColor("RED") + "附近自动插火把：已关闭"
        self._game_comp.SetTipMessage(message)

    def OnScriptTickClient(self):
        screen_open = nearby_logic.is_screen_open(clientApi.GetTopUI())
        if screen_open == self._screen_open:
            return
        self._screen_open = screen_open
        data = self.CreateEventData()
        data["open"] = screen_open
        self.NotifyToServer(config.EVENT_SET_SCREEN_OPEN, data)
