package me.zerep.auctionhouse.listing;

import org.bukkit.inventory.ItemStack;

import java.util.UUID;

public record Listing(
        int id,
        UUID sellerUuid,
        String sellerName,
        ItemStack item,
        int quantity,
        int price,
        String currency,
        Status status,
        long createdAt
) {
    public enum Status { ACTIVE, SOLD, CANCELLED, EXPIRED }

    public String displayName() {
        if (item == null) return "Unknown";
        String base = item.hasItemMeta() && item.getItemMeta().hasDisplayName()
                ? item.getItemMeta().getDisplayName()
                : item.getType().name().replace('_', ' ');
        return base + " x" + quantity;
    }
}
