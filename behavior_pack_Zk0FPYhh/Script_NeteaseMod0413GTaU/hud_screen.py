# -*- coding: utf-8 -*-
"""移动端 HUD 按钮：没有键盘时用来打开设置面板。"""

import mod.client.extraClientApi as clientApi

from Script_NeteaseMod0413GTaU import config

ScreenNode = clientApi.GetScreenNodeCls()

_OPEN_BUTTON = "/open_button"


class AutoTorchHudScreen(ScreenNode):

    def __init__(self, namespace, name, param):
        ScreenNode.__init__(self, namespace, name, param)
        self._client = clientApi.GetSystem(config.MOD_NAMESPACE, config.CLIENT_SYSTEM_NAME)

    def Create(self):
        button = self.GetBaseUIControl(_OPEN_BUTTON).asButton()
        button.AddTouchEventParams({"isSwallow": True})
        button.SetButtonTouchUpCallback(self.OnOpenClick)

    def OnOpenClick(self, args):
        self._client.open_settings()
