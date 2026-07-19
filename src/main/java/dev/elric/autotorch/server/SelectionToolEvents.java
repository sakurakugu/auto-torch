package dev.elric.autotorch.server;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Items;
import net.neoforged.fml.LogicalSide;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/** 在服务端拦截作为选区工具的木斧交互，防止误破坏或使用方块。 */
public final class SelectionToolEvents {
    private SelectionToolEvents() {
    }

    public static void onLeftClick(PlayerInteractEvent.LeftClickBlock event) {
        if (event.getSide() == LogicalSide.SERVER && event.getItemStack().is(Items.WOODEN_AXE)) {
            event.setCanceled(true);
        }
    }

    public static void onRightClick(PlayerInteractEvent.RightClickBlock event) {
        if (event.getSide() == LogicalSide.SERVER
                && event.getHand() == InteractionHand.MAIN_HAND
                && event.getItemStack().is(Items.WOODEN_AXE)) {
            event.setCancellationResult(InteractionResult.SUCCESS_SERVER);
            event.setCanceled(true);
        }
    }
}
