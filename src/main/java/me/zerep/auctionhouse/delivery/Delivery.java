package me.zerep.auctionhouse.delivery;

import org.bukkit.inventory.ItemStack;

import java.util.UUID;

public record Delivery(
        int id,
        UUID playerUuid,
        Type type,
        ItemStack item,
        int amount,
        String currency,
        long createdAt
) {
    public enum Type { SALE, ITEM_RETURN, CANCEL_RETURN, EXPIRED_RETURN, SHOP_PURCHASE }

    public boolean isItemDelivery() {
        return item != null;
    }
}
