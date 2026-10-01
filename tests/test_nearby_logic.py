# -*- coding: utf-8 -*-
"""附近自动插火把纯逻辑测试，使用 Python 2.7 运行：python -m unittest discover -s tests"""

import os
import sys
import unittest

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "behavior_pack_Zk0FPYhh"))

from Script_NeteaseMod0413GTaU import config
from Script_NeteaseMod0413GTaU import nearby_logic


def torch(count=1):
    return {"itemName": nearby_logic.TORCH, "count": count, "auxValue": 0}


class ConfigTest(unittest.TestCase):

    def test_defaults(self):
        self.assertEqual(config.normalize_nearby(None), {
            config.NEARBY_ENABLED: False,
            config.NEARBY_LIGHT_THRESHOLD: 4,
            config.NEARBY_INCLUDE_SKY_LIGHT: True,
        })

    def test_clamps_threshold_and_coerces_types(self):
        data = config.normalize_nearby({
            config.NEARBY_ENABLED: 1,
            config.NEARBY_LIGHT_THRESHOLD: 99,
            config.NEARBY_INCLUDE_SKY_LIGHT: 0,
        })
        self.assertTrue(data[config.NEARBY_ENABLED] is True)
        self.assertEqual(data[config.NEARBY_LIGHT_THRESHOLD], 16)
        # 网易 API 暂不支持区分天空光，配置始终固定为启用状态。
        self.assertTrue(data[config.NEARBY_INCLUDE_SKY_LIGHT] is True)
        self.assertEqual(config.normalize_nearby({config.NEARBY_LIGHT_THRESHOLD: -3})[config.NEARBY_LIGHT_THRESHOLD], 1)

    def test_invalid_threshold_uses_default(self):
        data = config.normalize_nearby({config.NEARBY_LIGHT_THRESHOLD: "abc"})
        self.assertEqual(data[config.NEARBY_LIGHT_THRESHOLD], 4)

    def test_threshold_slider_round_trip(self):
        self.assertEqual(config.threshold_slider_steps(), 16)
        for threshold in range(config.NEARBY_LIGHT_THRESHOLD_MIN, config.NEARBY_LIGHT_THRESHOLD_MAX + 1):
            self.assertEqual(config.slider_to_threshold(config.threshold_to_slider(threshold)), threshold)

    def test_slider_value_rounds_and_clamps(self):
        self.assertEqual(config.slider_to_threshold(2.6), 4)
        self.assertEqual(config.slider_to_threshold(-1.0), 1)
        self.assertEqual(config.slider_to_threshold(40.0), 16)
        self.assertEqual(config.slider_to_threshold(None), 4)


class GeometryTest(unittest.TestCase):

    def test_block_origin_floors_coordinates(self):
        self.assertEqual(nearby_logic.block_origin((-0.5, 64.0, 3.7)), (-1, 64, 3))
        # 浮点误差不应让玩家落到下一格
        self.assertEqual(nearby_logic.block_origin((0.5, 63.99999, 0.5)), (0, 64, 0))

    def test_player_collision(self):
        foot = (0.5, 64.0, 0.5)
        self.assertTrue(nearby_logic.intersects_player(foot, (0, 64, 0)))
        self.assertTrue(nearby_logic.intersects_player(foot, (0, 65, 0)))
        self.assertFalse(nearby_logic.intersects_player(foot, (0, 66, 0)))
        self.assertFalse(nearby_logic.intersects_player(foot, (1, 64, 0)))
        self.assertFalse(nearby_logic.intersects_player(foot, (0, 63, 0)))
        # 贴着玩家碰撞箱边缘的方块
        self.assertTrue(nearby_logic.intersects_player((0.8, 64.0, 0.5), (1, 64, 0)))

    def test_reach(self):
        foot = (0.5, 64.0, 0.5)
        self.assertTrue(nearby_logic.within_reach(foot, (1, 63, 1)))
        self.assertFalse(nearby_logic.within_reach(foot, (2, 62, 2)))


class FindTargetTest(unittest.TestCase):

    def test_candidates_cover_scan_range(self):
        candidates = list(nearby_logic.candidates((0.5, 64.0, 0.5)))
        self.assertEqual(len(candidates), 5 * 5 * 4)
        ys = set(pos[1] for pos in candidates)
        self.assertEqual(ys, set([62, 63, 64, 65]))

    def test_picks_nearest_dark_valid_target(self):
        foot = (0.5, 64.0, 0.5)
        # 地面在 y=63，只有 y=64 的空气可以放置
        target = nearby_logic.find_target(
            foot, lambda pos: False, lambda pos: pos[1] == 64, lambda pos: 0, 4)
        # 最近且不与玩家相交的位置在同一高度的相邻格
        self.assertEqual(target[1], 64)
        self.assertEqual(abs(target[0]) + abs(target[2]), 1)

    def test_skips_bright_waiting_and_unplaceable(self):
        foot = (0.5, 64.0, 0.5)
        only = (2, 63, 2)
        self.assertEqual(nearby_logic.find_target(
            foot, lambda pos: False, lambda pos: pos == only, lambda pos: 0, 4), only)
        self.assertIsNone(nearby_logic.find_target(
            foot, lambda pos: pos == only, lambda pos: pos == only, lambda pos: 0, 4))
        self.assertIsNone(nearby_logic.find_target(
            foot, lambda pos: False, lambda pos: pos == only, lambda pos: 4, 4))

    def test_checks_light_only_for_placeable_positions(self):
        light_checked = []

        def light(pos):
            light_checked.append(pos)
            return 0

        nearby_logic.find_target((0.5, 64.0, 0.5), lambda pos: False, lambda pos: False, light, 4)
        self.assertEqual(light_checked, [])

    def test_requires_stable_light_before_selecting_target(self):
        tracker = nearby_logic.NearbyTracker()
        foot = (0.5, 64.0, 0.5)
        only = (2, 63, 2)
        stable = lambda pos, light: tracker.confirm_dark(pos, light, 4)

        self.assertTrue(tracker.should_scan())
        self.assertIsNone(nearby_logic.find_target(
            foot, lambda pos: False, lambda pos: pos == only, lambda pos: 0, 4, stable))
        for _ in range(nearby_logic.SCAN_INTERVAL_TICKS - 1):
            self.assertFalse(tracker.should_scan())

        self.assertTrue(tracker.should_scan())
        self.assertEqual(nearby_logic.find_target(
            foot, lambda pos: False, lambda pos: pos == only, lambda pos: 0, 4, stable), only)


class TorchSourceTest(unittest.TestCase):

    def test_prefers_offhand_then_selected_then_hotbar(self):
        hotbar = [None] * 9
        hotbar[5] = torch()
        hotbar[2] = torch()
        self.assertEqual(nearby_logic.find_torch(torch(), 5, hotbar), (nearby_logic.SOURCE_OFFHAND, 0))
        self.assertEqual(nearby_logic.find_torch(None, 5, hotbar), (nearby_logic.SOURCE_INVENTORY, 5))
        self.assertEqual(nearby_logic.find_torch(None, 0, hotbar), (nearby_logic.SOURCE_INVENTORY, 2))

    def test_ignores_other_items_and_empty_stacks(self):
        hotbar = [{"itemName": "minecraft:soul_torch", "count": 1}, torch(0)] + [None] * 7
        self.assertIsNone(nearby_logic.find_torch(None, -1, hotbar))


class ScreenTest(unittest.TestCase):

    def test_in_game_screens_are_not_open(self):
        for name in ("hud_screen", "in_game_play_screen", "", None):
            self.assertFalse(nearby_logic.is_screen_open(name))

    def test_other_screens_are_open(self):
        for name in ("inventory_screen", "pause_screen", "netease_chat_screen", "main"):
            self.assertTrue(nearby_logic.is_screen_open(name))


class TrackerTest(unittest.TestCase):

    def test_scan_interval(self):
        tracker = nearby_logic.NearbyTracker()
        results = [tracker.should_scan() for _ in range(nearby_logic.SCAN_INTERVAL_TICKS + 1)]
        self.assertEqual(results, [True] + [False] * (nearby_logic.SCAN_INTERVAL_TICKS - 1) + [True])

    def test_retry_delay(self):
        tracker = nearby_logic.NearbyTracker()
        pos = (1, 2, 3)
        tracker.record_attempt(pos)
        self.assertTrue(tracker.is_waiting(pos))
        self.assertFalse(tracker.is_waiting((1, 2, 4)))
        for _ in range(nearby_logic.RETRY_DELAY_TICKS):
            tracker.age()
        self.assertFalse(tracker.is_waiting(pos))

    def test_confirms_darkness_across_scans(self):
        tracker = nearby_logic.NearbyTracker()
        pos = (1, 2, 3)

        self.assertTrue(tracker.should_scan())
        self.assertFalse(tracker.confirm_dark(pos, 0, 4))
        for _ in range(nearby_logic.SCAN_INTERVAL_TICKS - 1):
            self.assertFalse(tracker.should_scan())

        self.assertTrue(tracker.should_scan())
        self.assertTrue(tracker.confirm_dark(pos, 0, 4))
        self.assertFalse(tracker.confirm_dark(pos, 4, 4))
        self.assertFalse(tracker.confirm_dark(pos, 0, 4))

    def test_bright_scan_breaks_darkness_confirmation(self):
        tracker = nearby_logic.NearbyTracker()
        pos = (1, 2, 3)

        self.assertTrue(tracker.should_scan())
        self.assertFalse(tracker.confirm_dark(pos, 0, 4))
        for _ in range(nearby_logic.SCAN_INTERVAL_TICKS - 1):
            self.assertFalse(tracker.should_scan())

        self.assertTrue(tracker.should_scan())
        self.assertFalse(tracker.confirm_dark(pos, 4, 4))
        for _ in range(nearby_logic.SCAN_INTERVAL_TICKS - 1):
            self.assertFalse(tracker.should_scan())

        self.assertTrue(tracker.should_scan())
        self.assertFalse(tracker.confirm_dark(pos, 0, 4))

    def test_reset(self):
        tracker = nearby_logic.NearbyTracker()
        tracker.should_scan()
        tracker.record_attempt((0, 0, 0))
        tracker.reset()
        self.assertFalse(tracker.is_waiting((0, 0, 0)))
        self.assertTrue(tracker.should_scan())


if __name__ == "__main__":
    unittest.main()
