package com.sakurakugu.autotorch.server;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.init.Items;

/** 在服务端拦截作为选区工具的木斧交互，防止误破坏或使用方块。 */
public final class SelectionToolEvents {
    private static final Set<UUID> DISABLED_PLAYERS = new HashSet<>();

    private SelectionToolEvents() {
    }

    public static boolean handlesInteraction(EntityPlayerMP player, ItemStack stack) {
        return isEnabled(player.getUniqueID()) && stack != null && stack.getItem() == Items.WOODEN_AXE;
    }

    public static void setEnabled(EntityPlayerMP player, boolean enabled) {
        if (enabled) {
            DISABLED_PLAYERS.remove(player.getUniqueID());
        } else {
            DISABLED_PLAYERS.add(player.getUniqueID());
        }
    }

    public static void onLogout(EntityPlayerMP player) {
        DISABLED_PLAYERS.remove(player.getUniqueID());
    }

    private static boolean isEnabled(UUID playerId) {
        return !DISABLED_PLAYERS.contains(playerId);
    }
}
