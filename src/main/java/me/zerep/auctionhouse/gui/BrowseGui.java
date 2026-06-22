package me.zerep.auctionhouse.gui;

import me.zerep.auctionhouse.AuctionHousePlugin;
import me.zerep.auctionhouse.listing.Listing;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.util.List;

public class BrowseGui {
    private final AuctionHousePlugin plugin;

    public BrowseGui(AuctionHousePlugin plugin) {
        this.plugin = plugin;
    }

    public void open(Player player, int page) {
        Inventory inv = Bukkit.createInventory(new AhHolder(GuiTag.BROWSE, "browse"), 54, "AuctionHouse");
        for (int i = 0; i < 54; i++) inv.setItem(i, Gfx.filler());

        inv.setItem(49, Gfx.item(Material.CHEST, "&eMarketplace", "&7Player listings"));
        inv.setItem(53, Gfx.item(Material.HOPPER, "&aBuild Shop", "&7Server building blocks"));
        inv.setItem(45, Gfx.item(Material.ENDER_PEARL, "&bDeliveries", "&7Claim items and currency"));
        inv.setItem(48, Gfx.item(Material.WRITABLE_BOOK, "&fMine", "&7Your listings"));

        List<Listing> listings = plugin.getListingRepository().getActive(page * 28, 28);
        int slot = 0;
        for (Listing l : listings) {
            if (slot == 45 || slot == 48 || slot == 49 || slot == 53) slot++;
            if (slot >= 45) break;
            inv.setItem(slot, Gfx.item(l.item().getType(), "&f" + l.displayName(),
                    "&7Seller: &e" + l.sellerName(),
                    "&7Price: &e" + l.price() + "x " + plugin.getCurrencyRegistry().displayName(l.currency()),
                    "&7Click to buy"));
            slot++;
        }
        player.openInventory(inv);
    }
}
