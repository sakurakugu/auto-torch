# -*- coding: utf-8 -*-
"""客户端 System：保存本地配置、管理设置面板，并把配置与界面状态同步给服务端。"""

import mod.client.extraClientApi as clientApi

from Script_NeteaseMod0413GTaU import config
from Script_NeteaseMod0413GTaU import nearby_logic
from Script_NeteaseMod0413GTaU.settings_screen import AutoTorchSettingsScreen

ClientSystem = clientApi.GetClientSystemCls()
compFactory = clientApi.GetEngineCompFactory()
_KEYS = clientApi.GetMinecraftEnum().KeyBoardType

_SCRIPT_ROOT = "Script_NeteaseMod0413GTaU"
# 与 Java 版一致，G 键打开设置面板
OPEN_SETTINGS_KEY = str(_KEYS.KEY_G)
ESCAPE_KEY = str(_KEYS.KEY_ESCAPE)
# GetPlatform 返回 0 表示 Windows，其余平台没有键盘，需要 HUD 按钮打开面板
_PLATFORM_WINDOWS = 0


class AutoTorchClient(ClientSystem):

    def __init__(self, namespace, system_name):
        ClientSystem.__init__(self, namespace, system_name)
        level_id = clientApi.GetLevelId()
        self._config_comp = compFactory.CreateConfigClient(level_id)
        self._nearby = self._load_nearby()
        self._screen_open = None
        self._ui_registered = False

        engine_ns = clientApi.GetEngineNamespace()
        engine_name = clientApi.GetEngineSystemName()
        self.ListenForEvent(engine_ns, engine_name, "UiInitFinished", self, self.OnUiInitFinished)
        self.ListenForEvent(engine_ns, engine_name, "OnKeyPressInGame", self, self.OnKeyPressInGame)
        self.ListenForEvent(engine_ns, engine_name, "OnScriptTickClient", self, self.OnScriptTickClient)

    def Destroy(self):
        self.UnListenAllEvents()

    def nearby_config(self):
        return self._nearby

    def update_nearby(self, changes):
        """合并并校验配置，保存到本地后同步给服务端。"""
        data = dict(self._nearby)
        data.update(changes)
        self._nearby = config.normalize_nearby(data)
        self._save_nearby()
        self._sync_nearby()

    def reset_nearby(self):
        self.update_nearby(config.normalize_nearby(None))

    def _load_nearby(self):
        return config.normalize_nearby(self._config_comp.GetConfigData(config.CLIENT_CONFIG_NAME, True))

    def _save_nearby(self):
        self._config_comp.SetConfigData(config.CLIENT_CONFIG_NAME, dict(self._nearby), True)

    def _sync_nearby(self):
        data = self.CreateEventData()
        data.update(self._nearby)
        self.NotifyToServer(config.EVENT_SYNC_NEARBY_CONFIG, data)

    def _register_ui(self):
        if self._ui_registered:
            return
        clientApi.RegisterUI(config.MOD_NAMESPACE, config.SETTINGS_UI_KEY,
                             _SCRIPT_ROOT + ".settings_screen.AutoTorchSettingsScreen",
                             config.SETTINGS_UI_SCREEN_DEF)
        clientApi.RegisterUI(config.MOD_NAMESPACE, config.HUD_UI_KEY,
                             _SCRIPT_ROOT + ".hud_screen.AutoTorchHudScreen",
                             config.HUD_UI_SCREEN_DEF)
        self._ui_registered = True

    def _is_settings_on_top(self):
        return isinstance(clientApi.GetTopScreen(), AutoTorchSettingsScreen)

    def open_settings(self):
        if not self._is_settings_on_top():
            clientApi.PushScreen(config.MOD_NAMESPACE, config.SETTINGS_UI_KEY)

    def close_settings(self):
        if self._is_settings_on_top():
            clientApi.PopScreen()

    def OnUiInitFinished(self, args):
        # 切换维度后也会触发，重新同步可覆盖服务端可能丢失的状态
        self._screen_open = None
        self._sync_nearby()
        self._register_ui()
        if clientApi.GetPlatform() != _PLATFORM_WINDOWS and clientApi.GetUI(
                config.MOD_NAMESPACE, config.HUD_UI_KEY) is None:
            clientApi.CreateUI(config.MOD_NAMESPACE, config.HUD_UI_KEY, {"isHud": 1})

    def OnKeyPressInGame(self, args):
        key = args.get("key")
        is_down = args.get("isDown") == "1"
        if self._is_settings_on_top():
            # Esc 在弹起时处理，若引擎已在按下时关闭面板则此处不会重复关闭
            if (key == OPEN_SETTINGS_KEY and is_down) or (key == ESCAPE_KEY and not is_down):
                self.close_settings()
            return
        if key != OPEN_SETTINGS_KEY or not is_down:
            return
        if nearby_logic.is_screen_open(args.get("screenName")):
            return
        self.open_settings()

    def OnScriptTickClient(self):
        screen_open = nearby_logic.is_screen_open(clientApi.GetTopUI())
        if screen_open == self._screen_open:
            return
        self._screen_open = screen_open
        data = self.CreateEventData()
        data["open"] = screen_open
        self.NotifyToServer(config.EVENT_SET_SCREEN_OPEN, data)
