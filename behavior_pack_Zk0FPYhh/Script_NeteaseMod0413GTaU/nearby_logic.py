# -*- coding: utf-8 -*-
"""附近自动插火把的纯逻辑，不依赖网易 API，便于单元测试。"""

import math

TORCH = "minecraft:torch"

# 脚本 tick 为 30 次/秒：15 次约等于 Java 版的 10 游戏 tick，60 次约等于 40 游戏 tick
SCAN_INTERVAL_TICKS = 15
RETRY_DELAY_TICKS = 60
LIGHT_CONFIRMATION_SCANS = 2
HORIZONTAL_RADIUS = 2
MIN_Y_OFFSET = -2
MAX_Y_OFFSET = 1

PLAYER_HALF_WIDTH = 0.3
PLAYER_HEIGHT = 1.8
PLAYER_EYE_HEIGHT = 1.62
MAX_REACH_SQUARED = 20.25

HOTBAR_SIZE = 9
SOURCE_OFFHAND = "offhand"
SOURCE_INVENTORY = "inventory"

# 抵消脚部坐标的浮点误差，避免站在方块上时被判到下一格
_FOOT_EPSILON = 1e-3


# 未打开任何界面时 GetTopUI 可能返回的名称
IN_GAME_SCREENS = ("hud_screen", "in_game_play_screen")


def is_screen_open(top_ui):
    """根据 GetTopUI 的返回值判断是否打开了界面，打开界面时暂停自动放置。"""
    return bool(top_ui) and top_ui not in IN_GAME_SCREENS


def block_origin(foot_pos):
    x, y, z = foot_pos
    return int(math.floor(x)), int(math.floor(y + _FOOT_EPSILON)), int(math.floor(z))


def intersects_player(foot_pos, target):
    """判断目标方块与玩家碰撞箱是否相交（边界贴合不算相交）。"""
    fx, fy, fz = foot_pos
    tx, ty, tz = target
    return (tx < fx + PLAYER_HALF_WIDTH and tx + 1 > fx - PLAYER_HALF_WIDTH
            and ty < fy + PLAYER_HEIGHT and ty + 1 > fy
            and tz < fz + PLAYER_HALF_WIDTH and tz + 1 > fz - PLAYER_HALF_WIDTH)


def within_reach(foot_pos, target):
    """判断玩家眼睛到下方支撑方块顶面中心的距离是否在交互范围内。"""
    fx, fy, fz = foot_pos
    tx, ty, tz = target
    dx = tx + 0.5 - fx
    dy = ty - (fy + PLAYER_EYE_HEIGHT)
    dz = tz + 0.5 - fz
    return dx * dx + dy * dy + dz * dz <= MAX_REACH_SQUARED


def _distance_squared(foot_pos, target):
    dx = target[0] + 0.5 - foot_pos[0]
    dy = target[1] + 0.5 - foot_pos[1]
    dz = target[2] + 0.5 - foot_pos[2]
    return dx * dx + dy * dy + dz * dz


def candidates(foot_pos):
    """按距离从近到远返回扫描范围内的方块坐标，距离相同时保持 Java 版的遍历顺序。"""
    ox, oy, oz = block_origin(foot_pos)
    positions = []
    for dy in range(MIN_Y_OFFSET, MAX_Y_OFFSET + 1):
        for dx in range(-HORIZONTAL_RADIUS, HORIZONTAL_RADIUS + 1):
            for dz in range(-HORIZONTAL_RADIUS, HORIZONTAL_RADIUS + 1):
                positions.append((ox + dx, oy + dy, oz + dz))
    positions.sort(key=lambda pos: _distance_squared(foot_pos, pos))
    return positions


def find_target(foot_pos, is_waiting, is_placeable, light_at, threshold,
                is_light_stable=None):
    """返回最近的可放置暗处坐标；先做几何判断，再调用开销较大的方块与光照查询。"""
    for pos in candidates(foot_pos):
        if (is_waiting(pos)
                or intersects_player(foot_pos, pos)
                or not within_reach(foot_pos, pos)
                or not is_placeable(pos)):
            continue
        light = light_at(pos)
        if is_light_stable is not None and not is_light_stable(pos, light):
            continue
        if light >= threshold:
            continue
        return pos
    return None


def is_torch(item):
    return bool(item) and item.get("itemName") == TORCH and item.get("count", 0) > 0


def find_torch(offhand_item, selected_slot, hotbar_items):
    """按副手、当前槽、快捷栏顺序寻找火把，返回 (来源, 槽位) 或 None。"""
    if is_torch(offhand_item):
        return SOURCE_OFFHAND, 0
    if 0 <= selected_slot < len(hotbar_items) and is_torch(hotbar_items[selected_slot]):
        return SOURCE_INVENTORY, selected_slot
    for slot, item in enumerate(hotbar_items[:HOTBAR_SIZE]):
        if is_torch(item):
            return SOURCE_INVENTORY, slot
    return None


class NearbyTracker(object):
    """单个玩家的扫描间隔与失败重试计时。"""

    def __init__(self):
        self.reset()

    def reset(self):
        self._ticks_until_scan = 0
        self._last_attempt = None
        self._last_attempt_age = RETRY_DELAY_TICKS
        self._scan_number = 0
        self._light_observations = {}

    def age(self):
        """每 tick 调用一次，推进重试计时。"""
        if self._last_attempt is not None and self._last_attempt_age < RETRY_DELAY_TICKS:
            self._last_attempt_age += 1

    def should_scan(self):
        """功能可用时每 tick 调用一次，到达扫描间隔时返回 True。"""
        if self._ticks_until_scan > 0:
            self._ticks_until_scan -= 1
            return False
        self._ticks_until_scan = SCAN_INTERVAL_TICKS - 1
        self._scan_number += 1
        for pos, observation in list(self._light_observations.items()):
            if observation[0] < self._scan_number - 1:
                del self._light_observations[pos]
        return True

    def confirm_dark(self, pos, light, threshold):
        """确认位置连续多个扫描周期都低于阈值，避免读取到光照更新前的瞬时 0。"""
        pos = tuple(pos)
        if light >= threshold:
            self._light_observations.pop(pos, None)
            return False

        previous = self._light_observations.get(pos)
        if previous is not None and previous[0] == self._scan_number - 1:
            count = previous[1] + 1
        else:
            count = 1
        self._light_observations[pos] = (self._scan_number, count)
        return count >= LIGHT_CONFIRMATION_SCANS

    def record_attempt(self, pos):
        self._last_attempt = tuple(pos)
        self._last_attempt_age = 0
        self._light_observations.pop(self._last_attempt, None)

    def is_waiting(self, pos):
        return (self._last_attempt is not None
                and self._last_attempt == tuple(pos)
                and self._last_attempt_age < RETRY_DELAY_TICKS)
