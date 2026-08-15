# -*- coding: utf-8 -*-

from mod.common.mod import Mod


@Mod.Binding(name="Script_NeteaseMod0413GTaU", version="0.0.1")
class Script_NeteaseMod0413GTaU(object):

    def __init__(self):
        pass

    @Mod.InitServer()
    def Script_NeteaseMod0413GTaUServerInit(self):
        pass

    @Mod.DestroyServer()
    def Script_NeteaseMod0413GTaUServerDestroy(self):
        pass

    @Mod.InitClient()
    def Script_NeteaseMod0413GTaUClientInit(self):
        pass

    @Mod.DestroyClient()
    def Script_NeteaseMod0413GTaUClientDestroy(self):
        pass
