package com.sakurakugu.autotorch.compat;

import net.minecraft.item.Item;

/** 将 1.7.10 的物品注册表查询收敛到统一入口，替代新版的 Item.getByNameOrId。 */
public final class ItemIds {
    private ItemIds() {
    }

    /** 按 模组ID:注册名 解析物品，失败返回 null；1.7.10 的注册表里没有空气物品。 */
    public static Item resolve(String rawId) {
        if (rawId == null || rawId.trim().isEmpty()) return null;
        try {
            // 1.7.10 的物品注册表以注册名字符串为键，getObject(String) 会补全 minecraft: 前缀。
            Object registered = Item.itemRegistry.getObject(rawId.trim());
            return registered instanceof Item ? (Item) registered : null;
        } catch (RuntimeException exception) {
            return null;
        }
    }

    /** 返回物品的注册名，取不到时返回空字符串。 */
    public static String idOf(Item item) {
        if (item == null) return "";
        Object name = Item.itemRegistry.getNameForObject(item);
        return name == null ? "" : name.toString();
    }
}
