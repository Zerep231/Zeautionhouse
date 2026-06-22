package me.zerep.auctionhouse.gui;

import me.zerep.auctionhouse.AuctionHousePlugin;
import me.zerep.auctionhouse.delivery.Delivery;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.util.List;

/**
 * P1.1 Fix – Maps slot → delivery id so individual claims are always correct.
 * P2.2 Fix – Simplified navigation (Claim All, Back) friendly to Bedrock players.
 */
public class DeliveryGui {

    static final int SLOT_CLAIM_ALL = 49;
    static final int SLOT_BACK      = 53;

    private final AuctionHousePlugin plugin;

    public DeliveryGui(AuctionHousePlugin plugin) { this.plugin = plugin; }

    public void open(Player player, int page) {
        AhHolder holder = new AhHolder(GuiTag.DELIVERY, "delivery");
        Inventory inv = Bukkit.createInventory(holder, 54,
                Gfx.color("&bDelivery Box"));

        for (int i = 0; i < 54; i++) inv.setItem(i, Gfx.filler());

        List<Delivery> list = plugin.getDeliveryService().getUnclaimed(player.getUniqueId());
        int slot = 0;
        for (Delivery d : list) {
            if (slot >= 45) break;
            if (d.isItemDelivery()) {
                Material mat = d.item() != null ? d.item().getType() : Material.PAPER;
                int amt = d.item() != null ? d.item().getAmount() : 1;
                inv.setItem(slot, Gfx.item(mat,
                        "&f" + mat.name().replace('_', ' ') + " x" + amt,
                        "&7Type: &e" + pretty(d.type()),
                        "&aClick &7to claim"));
            } else {
                Material mat = plugin.getCurrencyRegistry().get(d.currency()) != null
                        ? plugin.getCurrencyRegistry().get(d.currency()).material()
                        : Material.DIAMOND;
                inv.setItem(slot, Gfx.item(mat,
                        "&f" + d.amount() + "x " + plugin.getCurrencyRegistry().displayName(d.currency()),
                        "&7Type: &e" + pretty(d.type()),
                        "&aClick &7to claim"));
            }
            holder.mapDelivery(slot, d.id());
            slot++;
        }

        inv.setItem(SLOT_CLAIM_ALL, Gfx.item(Material.LIME_DYE,
                "&aClaim All (" + list.size() + ")",
                "&7Claim everything at once"));
        inv.setItem(SLOT_BACK, Gfx.item(Material.ARROW, "&cBack", "&7Return to browse"));

        player.openInventory(inv);
    }

    private String pretty(Delivery.Type type) {
        return switch (type) {
            case SALE           -> "Sale Revenue";
            case ITEM_RETURN    -> "Purchase";
            case CANCEL_RETURN  -> "Cancelled Listing";
            case EXPIRED_RETURN -> "Expired Listing";
            case SHOP_PURCHASE  -> "Shop Purchase";
        };
    }
}
