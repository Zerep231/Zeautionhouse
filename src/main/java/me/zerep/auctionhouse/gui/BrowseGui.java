package me.zerep.auctionhouse.gui;

import me.zerep.auctionhouse.AuctionHousePlugin;
import me.zerep.auctionhouse.listing.Listing;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.util.List;

/**
 * P1.1 Fix – Builds a slot→listingId map on the AhHolder so click handlers
 * always look up the correct listing regardless of page or skipped filler slots.
 *
 * Layout: slots 0-44 = listings (45 per page), bottom row = navigation.
 *   45 = Deliveries  46-47 = filler  48 = Mine  49 = Refresh  50-52 = filler  53 = Shop
 */
public class BrowseGui {

    static final int ITEMS_PER_PAGE = 45;
    static final int SLOT_DELIVERIES = 45;
    static final int SLOT_MINE       = 48;
    static final int SLOT_REFRESH    = 49;
    static final int SLOT_SHOP       = 53;
    static final int SLOT_PREV       = 46;
    static final int SLOT_NEXT       = 52;

    private final AuctionHousePlugin plugin;

    public BrowseGui(AuctionHousePlugin plugin) { this.plugin = plugin; }

    public void open(Player player, int page) {
        AhHolder holder = new AhHolder(GuiTag.BROWSE, "browse", page); // P0-5: store page in holder
        Inventory inv = Bukkit.createInventory(holder, 54,
                Gfx.color("&6AuctionHouse &7– Page " + (page + 1)));

        for (int i = 0; i < 54; i++) inv.setItem(i, Gfx.filler());

        List<Listing> listings = plugin.getListingService().getActivePage(page * ITEMS_PER_PAGE, ITEMS_PER_PAGE);

        for (int i = 0; i < listings.size() && i < ITEMS_PER_PAGE; i++) {
            Listing l = listings.get(i);
            inv.setItem(i, Gfx.item(
                    safeType(l),
                    "&f" + l.displayName(),
                    "&7Seller: &e" + l.sellerName(),
                    "&7Price: &e" + l.price() + " &7" + plugin.getCurrencyRegistry().displayName(l.currency()),
                    "&aLeft-click &7to buy"));
            holder.mapListing(i, l.id());
        }

        // Navigation
        inv.setItem(SLOT_DELIVERIES, Gfx.item(Material.ENDER_PEARL,  "&bDeliveries",  "&7Claim items & currency"));
        inv.setItem(SLOT_MINE,       Gfx.item(Material.WRITABLE_BOOK, "&fMy Listings", "&7Manage your active listings"));
        inv.setItem(SLOT_REFRESH,    Gfx.item(Material.COMPASS,       "&eRefresh",     "&7Reload listing list"));
        inv.setItem(SLOT_SHOP,       Gfx.item(Material.HOPPER,        "&aBuild Shop",  "&7Server building blocks"));

        if (page > 0)
            inv.setItem(SLOT_PREV, Gfx.item(Material.ARROW, "&7&l◀ Previous", "&7Page " + page));
        if (listings.size() == ITEMS_PER_PAGE)
            inv.setItem(SLOT_NEXT, Gfx.item(Material.ARROW, "&7Next &l▶", "&7Page " + (page + 2)));

        // Store current page so click handler can navigate
        // (encode page in AhHolder key)
        player.openInventory(inv);
    }

    private Material safeType(Listing l) {
        try { return l.item() != null ? l.item().getType() : Material.PAPER; }
        catch (Exception e) { return Material.PAPER; }
    }
}
