# -*- coding: utf-8 -*-
"""服务端 System：按玩家扫描附近暗处，并模拟玩家使用火把放置。"""

import mod.server.extraServerApi as serverApi

from Script_NeteaseMod0413GTaU import config
from Script_NeteaseMod0413GTaU import nearby_logic

ServerSystem = serverApi.GetServerSystemCls()
compFactory = serverApi.GetEngineCompFactory()
minecraftEnum = serverApi.GetMinecraftEnum()


class _PlayerState(object):
    """单个玩家的附近插火把配置、界面状态与组件缓存。"""

    def __init__(self, player_id):
        self.player_id = player_id
        self.nearby = config.normalize_nearby(None)
        self.screen_open = False
        self.dimension = None
        self.tracker = nearby_logic.NearbyTracker()
        self.pos_comp = compFactory.CreatePos(player_id)
        self.item_comp = compFactory.CreateItem(player_id)
        self.dimension_comp = compFactory.CreateDimension(player_id)
        # PlayerUseItemToPos 需要以玩家 id 创建的组件
        self.use_comp = compFactory.CreateBlockInfo(player_id)


class AutoTorchServer(ServerSystem):

    def __init__(self, namespace, system_name):
        ServerSystem.__init__(self, namespace, system_name)
        level_id = serverApi.GetLevelId()
        self._block_info = compFactory.CreateBlockInfo(level_id)
        self._game = compFactory.CreateGame(level_id)
        self._players = {}

        engine_ns = serverApi.GetEngineNamespace()
        engine_name = serverApi.GetEngineSystemName()
        self.ListenForEvent(engine_ns, engine_name, "OnScriptTickServer", self, self.OnScriptTickServer)
        self.ListenForEvent(engine_ns, engine_name, "DelServerPlayerEvent", self, self.OnDelServerPlayer)
        self.ListenForEvent(config.MOD_NAMESPACE, config.CLIENT_SYSTEM_NAME,
                            config.EVENT_SYNC_NEARBY_CONFIG, self, self.OnSyncNearbyConfig)
        self.ListenForEvent(config.MOD_NAMESPACE, config.CLIENT_SYSTEM_NAME,
                            config.EVENT_SET_SCREEN_OPEN, self, self.OnSetScreenOpen)

    def Destroy(self):
        self.UnListenAllEvents()
        self._players.clear()

    def _state(self, player_id):
        state = self._players.get(player_id)
        if state is None:
            state = _PlayerState(player_id)
            self._players[player_id] = state
        return state

    def OnSyncNearbyConfig(self, args):
        self._state(args["__id__"]).nearby = config.normalize_nearby(args)

    def OnSetScreenOpen(self, args):
        self._state(args["__id__"]).screen_open = bool(args.get("open", False))

    def OnDelServerPlayer(self, args):
        self._players.pop(args.get("id"), None)

    def OnScriptTickServer(self):
        for state in self._players.values():
            state.tracker.age()
            if not state.nearby[config.NEARBY_ENABLED] or state.screen_open:
                continue
            if not self._game.IsEntityAlive(state.player_id):
                continue
            dimension = state.dimension_comp.GetEntityDimensionId()
            if dimension != state.dimension:
                state.dimension = dimension
                state.tracker.reset()
            if not state.tracker.should_scan():
                continue
            self._scan_and_place(state)

    def _scan_and_place(self, state):
        torch = self._find_torch(state)
        if torch is None:
            return
        foot_pos = state.pos_comp.GetFootPos()
        if not foot_pos:
            return
        dimension = state.dimension
        threshold = state.nearby[config.NEARBY_LIGHT_THRESHOLD]
        target = nearby_logic.find_target(
            foot_pos,
            state.tracker.is_waiting,
            lambda pos: self._is_placeable(pos, dimension),
            # 网易仅提供综合光照等级，“计入天空光”选项暂无法区分，待游戏内验证后再调整
            lambda pos: self._block_info.GetBlockLightLevel(pos, dimension),
            threshold,
            lambda pos, light: state.tracker.confirm_dark(pos, light, threshold))
        if target is None:
            return

        source, slot = torch
        pos_type = (minecraftEnum.ItemPosType.OFFHAND if source == nearby_logic.SOURCE_OFFHAND
                    else minecraftEnum.ItemPosType.INVENTORY)
        support = (target[0], target[1] - 1, target[2])
        state.use_comp.PlayerUseItemToPos(support, pos_type, slot, minecraftEnum.Facing.Up)
        state.tracker.record_attempt(target)

    def _find_torch(self, state):
        item_comp = state.item_comp
        item_pos = minecraftEnum.ItemPosType
        offhand = item_comp.GetPlayerItem(item_pos.OFFHAND, 0)
        hotbar = [item_comp.GetPlayerItem(item_pos.INVENTORY, slot)
                  for slot in range(nearby_logic.HOTBAR_SIZE)]
        return nearby_logic.find_torch(offhand, item_comp.GetSelectSlotId(), hotbar)

    def _is_placeable(self, pos, dimension):
        block = self._block_info.GetBlockNew(pos, dimension)
        if not block or block.get("name") != "minecraft:air":
            return False
        if self._block_info.GetLiquidBlock(pos, dimension) is not None:
            return False
        return self._block_info.MayPlace(nearby_logic.TORCH, pos, minecraftEnum.Facing.Up, dimension)
