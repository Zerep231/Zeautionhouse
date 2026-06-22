package me.zerep.auctionhouse.util;

import org.bukkit.inventory.ItemStack;

import java.util.Base64;

/**
 * P2.3 adjacent fix – Uses Paper 1.21+'s native byte serialization
 * (ItemStack#serializeAsBytes / ItemStack.deserializeBytes) instead of the
 * legacy BukkitObjectOutputStream hack.  Result is a Base64 string safe to
 * store in SQLite TEXT columns.
 */
public final class ItemSerializer {
    private ItemSerializer() {}

    public static String serialize(ItemStack item) {
        if (item == null) return null;
        return Base64.getEncoder().encodeToString(item.serializeAsBytes());
    }

    public static ItemStack deserialize(String data) {
        if (data == null || data.isBlank()) return null;
        return ItemStack.deserializeBytes(Base64.getDecoder().decode(data));
    }
}
