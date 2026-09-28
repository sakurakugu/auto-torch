# -*- coding: utf-8 -*-
"""设置面板：对应 Java 版主面板中的“附近自动插火把”分区。"""

import mod.client.extraClientApi as clientApi

from Script_NeteaseMod0413GTaU import config

ViewBinder = clientApi.GetViewBinderCls()
ViewRequest = clientApi.GetViewViewRequestCls()
ScreenNode = clientApi.GetScreenNodeCls()

_WINDOW = "/window"
_ENABLED_BUTTON = _WINDOW + "/enabled_button"
_THRESHOLD_LABEL = _WINDOW + "/threshold_label"
_THRESHOLD_SLIDER = _WINDOW + "/threshold_slider"
_SKY_LIGHT_BUTTON = _WINDOW + "/sky_light_button"
_RESET_BUTTON = _WINDOW + "/reset_button"
_DONE_BUTTON = _WINDOW + "/done_button"
_BUTTON_LABEL = "/button_label"


def _enabled_text(enabled):
    return "自动放置：开" if enabled else "自动放置：关"


def _sky_light_text(include):
    return "亮度计算包括天空光：是" if include else "亮度计算包括天空光：否"


def _threshold_text(threshold):
    return "亮度低于：%d" % threshold


class AutoTorchSettingsScreen(ScreenNode):

    def __init__(self, namespace, name, param):
        ScreenNode.__init__(self, namespace, name, param)
        self._client = clientApi.GetSystem(config.MOD_NAMESPACE, config.CLIENT_SYSTEM_NAME)
        self._slider_value = config.threshold_to_slider(self._nearby()[config.NEARBY_LIGHT_THRESHOLD])

    def _nearby(self):
        return self._client.nearby_config()

    def Create(self):
        self._bind_button(_ENABLED_BUTTON, self.OnEnabledClick)
        self._bind_button(_SKY_LIGHT_BUTTON, self.OnSkyLightClick)
        self._bind_button(_RESET_BUTTON, self.OnResetClick)
        self._bind_button(_DONE_BUTTON, self.OnDoneClick)
        self._refresh()

    def _bind_button(self, path, callback):
        button = self.GetBaseUIControl(path).asButton()
        button.AddTouchEventParams({"isSwallow": True})
        button.SetButtonTouchUpCallback(callback)

    def _set_text(self, path, text):
        self.GetBaseUIControl(path).asLabel().SetText(text)

    def _refresh(self):
        nearby = self._nearby()
        threshold = nearby[config.NEARBY_LIGHT_THRESHOLD]
        self._set_text(_ENABLED_BUTTON + _BUTTON_LABEL, _enabled_text(nearby[config.NEARBY_ENABLED]))
        self._set_text(_SKY_LIGHT_BUTTON + _BUTTON_LABEL, _sky_light_text(nearby[config.NEARBY_INCLUDE_SKY_LIGHT]))
        self._set_text(_THRESHOLD_LABEL, _threshold_text(threshold))
        self._slider_value = config.threshold_to_slider(threshold)
        self.GetBaseUIControl(_THRESHOLD_SLIDER).asSlider().SetSliderValue(self._slider_value)

    def OnEnabledClick(self, args):
        enabled = not self._nearby()[config.NEARBY_ENABLED]
        self._client.update_nearby({config.NEARBY_ENABLED: enabled})
        self._refresh()

    def OnSkyLightClick(self, args):
        include = not self._nearby()[config.NEARBY_INCLUDE_SKY_LIGHT]
        self._client.update_nearby({config.NEARBY_INCLUDE_SKY_LIGHT: include})
        self._refresh()

    def OnResetClick(self, args):
        self._client.reset_nearby()
        self._refresh()

    def OnDoneClick(self, args):
        clientApi.PopScreen()

    @ViewBinder.binding(ViewBinder.BF_SliderChanged | ViewBinder.BF_SliderFinished)
    def OnThresholdSliderChanged(self, value, _is_finished, _unused):
        self._slider_value = value
        threshold = config.slider_to_threshold(value)
        self._set_text(_THRESHOLD_LABEL, _threshold_text(threshold))
        # 拖动时每帧都会回调，只在阈值变化时保存并同步
        if threshold != self._nearby()[config.NEARBY_LIGHT_THRESHOLD]:
            self._client.update_nearby({config.NEARBY_LIGHT_THRESHOLD: threshold})
        return ViewRequest.Refresh

    @ViewBinder.binding(ViewBinder.BF_BindFloat)
    def ReturnThresholdSliderValue(self):
        return self._slider_value

    @ViewBinder.binding(ViewBinder.BF_BindInt)
    def ReturnThresholdSliderSteps(self):
        return config.threshold_slider_steps()
