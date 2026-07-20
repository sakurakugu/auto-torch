package com.sakurakugu.autotorch.server;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** 在服务端拦截作为选区工具的木斧交互，防止误破坏或使用方块。 */
public final class SelectionToolEvents {
    private static final Set<UUID> DISABLED_PLAYERS = new HashSet<>();

    private SelectionToolEvents() {
    }

    public static boolean handlesInteraction(ServerPlayer player, ItemStack stack) {
        return isEnabled(player.getUUID()) && stack.is(Items.WOODEN_AXE);
    }

    public static void setEnabled(ServerPlayer player, boolean enabled) {
        if (enabled) {
            DISABLED_PLAYERS.remove(player.getUUID());
        } else {
            DISABLED_PLAYERS.add(player.getUUID());
        }
    }

    public static void onLogout(ServerPlayer player) {
        DISABLED_PLAYERS.remove(player.getUUID());
    }

    private static boolean isEnabled(UUID playerId) {
        return !DISABLED_PLAYERS.contains(playerId);
    }
}
