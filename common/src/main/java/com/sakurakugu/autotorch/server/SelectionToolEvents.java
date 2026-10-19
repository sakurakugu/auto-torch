package com.sakurakugu.autotorch.server;

import java.util.HashSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** 在服务端拦截玩家配置的选区工具交互，防止误破坏或使用方块。 */
public final class SelectionToolEvents {
    private static final Set<UUID> DISABLED_PLAYERS = new HashSet<>();
    private static final Map<UUID, Item> TOOL_ITEMS = new HashMap<>();

    private SelectionToolEvents() {
    }

    public static boolean handlesInteraction(ServerPlayer player, ItemStack stack) {
        return isEnabled(player.getUUID()) && stack.is(TOOL_ITEMS.getOrDefault(player.getUUID(), Items.WOODEN_AXE));
    }

    public static void setEnabled(ServerPlayer player, boolean enabled, String toolItemId) {
        if (enabled) {
            ResourceLocation id = ResourceLocation.tryParse(toolItemId);
            Item item = id == null ? null : Registry.ITEM.getOptional(id).orElse(null);
            if (item == null || item == Items.AIR) {
                DISABLED_PLAYERS.add(player.getUUID());
                TOOL_ITEMS.remove(player.getUUID());
                return;
            }
            TOOL_ITEMS.put(player.getUUID(), item);
            DISABLED_PLAYERS.remove(player.getUUID());
        } else {
            DISABLED_PLAYERS.add(player.getUUID());
            TOOL_ITEMS.remove(player.getUUID());
        }
    }

    public static void onLogout(ServerPlayer player) {
        DISABLED_PLAYERS.remove(player.getUUID());
        TOOL_ITEMS.remove(player.getUUID());
    }

    private static boolean isEnabled(UUID playerId) {
        return !DISABLED_PLAYERS.contains(playerId);
    }
}

