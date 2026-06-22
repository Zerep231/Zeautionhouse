package me.zerep.auctionhouse.gui;

import me.zerep.auctionhouse.AuctionHousePlugin;
import me.zerep.auctionhouse.listing.Listing;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.util.List;

/**
 * P1.1 Fix – Uses AhHolder slot map for safe listing lookups.
 */
public class MineGui {

    static final int ITEMS_PER_PAGE = 45;
    static final int SLOT_BACK      = 49;
    static final int SLOT_PREV      = 46;
    static final int SLOT_NEXT      = 52;

    private final AuctionHousePlugin plugin;

    public MineGui(AuctionHousePlugin plugin) { this.plugin = plugin; }

    public void open(Player player, int page) {
        AhHolder holder = new AhHolder(GuiTag.MINE, "mine", page); // P0-5
        Inventory inv = Bukkit.createInventory(holder, 54,
                Gfx.color("&fYour Listings &7– Page " + (page + 1)));

        for (int i = 0; i < 54; i++) inv.setItem(i, Gfx.filler());

        List<Listing> listings = plugin.getListingRepository()
                .getByPlayer(player.getUniqueId(), page * ITEMS_PER_PAGE, ITEMS_PER_PAGE);

        for (int i = 0; i < listings.size() && i < ITEMS_PER_PAGE; i++) {
            Listing l = listings.get(i);
            Material type = l.item() != null ? l.item().getType() : Material.PAPER;
            inv.setItem(i, Gfx.item(type,
                    "&f" + l.displayName(),
                    "&7Price: &e" + l.price() + " &7" + plugin.getCurrencyRegistry().displayName(l.currency()),
                    "&cLeft-click &7to cancel & retrieve"));
            holder.mapListing(i, l.id());
        }

        inv.setItem(SLOT_BACK, Gfx.item(Material.ARROW, "&cBack", "&7Return to browse"));
        if (page > 0)
            inv.setItem(SLOT_PREV, Gfx.item(Material.ARROW, "&7&l◀ Previous", "&7Page " + page));
        if (listings.size() == ITEMS_PER_PAGE)
            inv.setItem(SLOT_NEXT, Gfx.item(Material.ARROW, "&7Next &l▶", "&7Page " + (page + 2)));

        player.openInventory(inv);
    }
}
