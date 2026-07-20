package dev.sakurakugu.autotorch.server;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Items;
import net.neoforged.fml.LogicalSide;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/** 在服务端拦截作为选区工具的木斧交互，防止误破坏或使用方块。 */
public final class SelectionToolEvents {
    private static final Set<UUID> DISABLED_PLAYERS = new HashSet<>();

    private SelectionToolEvents() {
    }

    public static void onLeftClick(PlayerInteractEvent.LeftClickBlock event) {
        if (event.getSide() == LogicalSide.SERVER
                && isEnabled(event.getEntity().getUUID())
                && event.getItemStack().is(Items.WOODEN_AXE)) {
            event.setCanceled(true);
        }
    }

    public static void onRightClick(PlayerInteractEvent.RightClickBlock event) {
        if (event.getSide() == LogicalSide.SERVER
                && isEnabled(event.getEntity().getUUID())
                && event.getHand() == InteractionHand.MAIN_HAND
                && event.getItemStack().is(Items.WOODEN_AXE)) {
            event.setCancellationResult(InteractionResult.SUCCESS_SERVER);
            event.setCanceled(true);
        }
    }

    public static void setEnabled(ServerPlayer player, boolean enabled) {
        if (enabled) {
            DISABLED_PLAYERS.remove(player.getUUID());
        } else {
            DISABLED_PLAYERS.add(player.getUUID());
        }
    }

    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        DISABLED_PLAYERS.remove(event.getEntity().getUUID());
    }

    private static boolean isEnabled(UUID playerId) {
        return !DISABLED_PLAYERS.contains(playerId);
    }
}
