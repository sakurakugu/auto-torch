# -*- coding: utf-8 -*-

from mod.common.mod import Mod
import mod.client.extraClientApi as clientApi
import mod.server.extraServerApi as serverApi

from Script_NeteaseMod0413GTaU import config

_SCRIPT_ROOT = "Script_NeteaseMod0413GTaU"


@Mod.Binding(name="Script_NeteaseMod0413GTaU", version="0.0.1")
class Script_NeteaseMod0413GTaU(object):

    def __init__(self):
        pass

    @Mod.InitServer()
    def Script_NeteaseMod0413GTaUServerInit(self):
        serverApi.RegisterSystem(config.MOD_NAMESPACE, config.SERVER_SYSTEM_NAME,
                                 _SCRIPT_ROOT + ".server_system.AutoTorchServer")

    @Mod.DestroyServer()
    def Script_NeteaseMod0413GTaUServerDestroy(self):
        pass

    @Mod.InitClient()
    def Script_NeteaseMod0413GTaUClientInit(self):
        clientApi.RegisterSystem(config.MOD_NAMESPACE, config.CLIENT_SYSTEM_NAME,
                                 _SCRIPT_ROOT + ".client_system.AutoTorchClient")

    @Mod.DestroyClient()
    def Script_NeteaseMod0413GTaUClientDestroy(self):
        pass
