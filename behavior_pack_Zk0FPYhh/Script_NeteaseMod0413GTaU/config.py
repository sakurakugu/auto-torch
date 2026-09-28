# -*- coding: utf-8 -*-
"""客户端与服务端共用的常量与配置定义，不依赖网易 API。"""

MOD_NAMESPACE = "AutoTorch"
CLIENT_SYSTEM_NAME = "AutoTorchClient"
SERVER_SYSTEM_NAME = "AutoTorchServer"

# 客户端 -> 服务端事件
EVENT_SYNC_NEARBY_CONFIG = "SyncNearbyConfig"
EVENT_SET_SCREEN_OPEN = "SetScreenOpen"

# 本地存储名称，只能包含字母、数字和下划线
CLIENT_CONFIG_NAME = "autotorch_client_config"

# 界面注册信息，画布定义为“json 命名空间.画布名”
SETTINGS_UI_KEY = "AutoTorchSettings"
SETTINGS_UI_SCREEN_DEF = "autotorch.main"
HUD_UI_KEY = "AutoTorchHud"
HUD_UI_SCREEN_DEF = "autotorch_hud.main"

NEARBY_ENABLED = "nearbyAutoTorchEnabled"
NEARBY_LIGHT_THRESHOLD = "nearbyAutoTorchLightThreshold"
NEARBY_INCLUDE_SKY_LIGHT = "nearbyAutoTorchIncludeSkyLight"

NEARBY_LIGHT_THRESHOLD_DEFAULT = 4
NEARBY_LIGHT_THRESHOLD_MIN = 1
NEARBY_LIGHT_THRESHOLD_MAX = 16


def normalize_nearby(data):
    """把本地存储或网络事件中的附近插火把配置规范为合法值。"""
    data = data or {}
    try:
        threshold = int(data.get(NEARBY_LIGHT_THRESHOLD, NEARBY_LIGHT_THRESHOLD_DEFAULT))
    except (TypeError, ValueError):
        threshold = NEARBY_LIGHT_THRESHOLD_DEFAULT
    threshold = max(NEARBY_LIGHT_THRESHOLD_MIN, min(NEARBY_LIGHT_THRESHOLD_MAX, threshold))
    return {
        NEARBY_ENABLED: bool(data.get(NEARBY_ENABLED, False)),
        NEARBY_LIGHT_THRESHOLD: threshold,
        NEARBY_INCLUDE_SKY_LIGHT: bool(data.get(NEARBY_INCLUDE_SKY_LIGHT, True)),
    }


def threshold_slider_steps():
    """亮度阈值滑动条的格数，每个可选阈值占一格。"""
    return NEARBY_LIGHT_THRESHOLD_MAX - NEARBY_LIGHT_THRESHOLD_MIN + 1


def threshold_to_slider(threshold):
    return float(threshold - NEARBY_LIGHT_THRESHOLD_MIN)


def slider_to_threshold(value):
    """把固定格滑动条的值换算为阈值，超出范围时取边界值。"""
    try:
        threshold = NEARBY_LIGHT_THRESHOLD_MIN + int(round(float(value)))
    except (TypeError, ValueError):
        return NEARBY_LIGHT_THRESHOLD_DEFAULT
    return max(NEARBY_LIGHT_THRESHOLD_MIN, min(NEARBY_LIGHT_THRESHOLD_MAX, threshold))
