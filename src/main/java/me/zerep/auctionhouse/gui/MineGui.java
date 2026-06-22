package me.zerep.auctionhouse.gui;

import me.zerep.auctionhouse.AuctionHousePlugin;
import me.zerep.auctionhouse.listing.Listing;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.util.List;

public class MineGui {
    private final AuctionHousePlugin plugin;

    public MineGui(AuctionHousePlugin plugin) { this.plugin = plugin; }

    public void open(Player player, int page) {
        Inventory inv = Bukkit.createInventory(new AhHolder(GuiTag.MINE, "mine"), 54, "Your Listings");
        for (int i = 0; i < 54; i++) inv.setItem(i, Gfx.filler());
        List<Listing> listings = plugin.getListingRepository().getByPlayer(player.getUniqueId(), page * 45, 45);
        int slot = 0;
        for (Listing l : listings) {
            inv.setItem(slot++, Gfx.item(l.item().getType(), "&f" + l.displayName(),
                    "&7Price: &e" + l.price() + "x " + plugin.getCurrencyRegistry().displayName(l.currency()),
                    "&7Click to manage"));
        }
        inv.setItem(49, Gfx.item(Material.ARROW, "&cBack"));
        player.openInventory(inv);
    }
}
