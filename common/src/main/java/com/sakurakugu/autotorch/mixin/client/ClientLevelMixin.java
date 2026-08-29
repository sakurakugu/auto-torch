package com.sakurakugu.autotorch.mixin.client;

import com.sakurakugu.autotorch.client.LightOverlayState;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 将原版客户端方块与光照失效通知转交给光照覆盖层。 */
@Mixin(ClientLevel.class)
abstract class ClientLevelMixin {
    @Inject(method = "setBlocksDirty", at = @At("TAIL"))
    private void autoTorch$markBlockDirty(
            BlockPos pos, BlockState oldState, BlockState newState, CallbackInfo callback
    ) {
        LightOverlayState.markBlockDirty((ClientLevel) (Object) this, pos);
    }

    @Inject(method = "setSectionDirtyWithNeighbors", at = @At("TAIL"))
    private void autoTorch$markSectionDirty(int sectionX, int sectionY, int sectionZ, CallbackInfo callback) {
        LightOverlayState.markSectionDirty((ClientLevel) (Object) this, sectionX, sectionY, sectionZ);
    }

    // 部分版本没有该批量失效方法，缺少目标时跳过注入即可。
    @Inject(method = "setSectionRangeDirty", at = @At("TAIL"), require = 0)
    private void autoTorch$markSectionRangeDirty(
            int minSectionX, int minSectionY, int minSectionZ,
            int maxSectionX, int maxSectionY, int maxSectionZ,
            CallbackInfo callback
    ) {
        LightOverlayState.markSectionRangeDirty((ClientLevel) (Object) this,
                minSectionX, minSectionY, minSectionZ, maxSectionX, maxSectionY, maxSectionZ);
    }
}
