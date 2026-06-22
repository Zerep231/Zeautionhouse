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
 * P1-5 Fix – Full pagination: PREV/NEXT buttons appear when needed.
 */
public class DeliveryGui {

    static final int ITEMS_PER_PAGE = 45;
    static final int SLOT_PREV      = 46;
    static final int SLOT_CLAIM_ALL = 49;
    static final int SLOT_NEXT      = 52;
    static final int SLOT_BACK      = 53;

    private final AuctionHousePlugin plugin;

    public DeliveryGui(AuctionHousePlugin plugin) { this.plugin = plugin; }

    public void open(Player player, int page) {
        AhHolder holder = new AhHolder(GuiTag.DELIVERY, "delivery", page); // P0-5
        Inventory inv = Bukkit.createInventory(holder, 54,
                Gfx.color("&bDelivery Box &7– Page " + (page + 1)));

        for (int i = 0; i < 54; i++) inv.setItem(i, Gfx.filler());

        // P1-5: paginated load
        List<Delivery> list = plugin.getDeliveryService()
                .getUnclaimedPage(player.getUniqueId(), page * ITEMS_PER_PAGE, ITEMS_PER_PAGE);

        int slot = 0;
        for (Delivery d : list) {
            if (slot >= ITEMS_PER_PAGE) break;
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

        int total = plugin.getDeliveryService().countUnclaimed(player.getUniqueId());

        inv.setItem(SLOT_CLAIM_ALL, Gfx.item(Material.LIME_DYE,
                "&aClaim All (" + total + ")",
                "&7Claim everything at once"));
        inv.setItem(SLOT_BACK, Gfx.item(Material.ARROW, "&cBack", "&7Return to browse"));

        if (page > 0)
            inv.setItem(SLOT_PREV, Gfx.item(Material.ARROW, "&7&l◀ Previous", "&7Page " + page));
        if (list.size() == ITEMS_PER_PAGE)
            inv.setItem(SLOT_NEXT, Gfx.item(Material.ARROW, "&7Next &l▶", "&7Page " + (page + 2)));

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
