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
