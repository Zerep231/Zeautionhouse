package me.zerep.auctionhouse.gui;

import me.zerep.auctionhouse.AuctionHousePlugin;
import me.zerep.auctionhouse.shop.ShopService;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

public class ShopGui {
    private final AuctionHousePlugin plugin;

    public ShopGui(AuctionHousePlugin plugin) {
        this.plugin = plugin;
    }

    public void openHome(Player player) {
        Inventory inv = Bukkit.createInventory(new AhHolder(GuiTag.SHOP_HOME, "shop-home"), 27, "Build Shop");
        for (int i = 0; i < 27; i++) inv.setItem(i, Gfx.filler());
        int slot = 10;
        for (ShopService.ShopCategory cat : plugin.getShopService().getCategories()) {
            inv.setItem(slot++, Gfx.item(Material.CHEST, "&a" + cat.title(), "&7Click to browse"));
        }
        player.openInventory(inv);
    }
}
