package me.zerep.auctionhouse.gui;

import me.zerep.auctionhouse.AuctionHousePlugin;
import me.zerep.auctionhouse.currency.Currency;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public class CreateListingGui {
    public static final int INPUT_SLOT = 13;
    public static final int NEXT_SLOT = 24;
    public static final int CANCEL_SLOT = 20;

    private final AuctionHousePlugin plugin;

    public CreateListingGui(AuctionHousePlugin plugin) {
        this.plugin = plugin;
    }

    public void openStep1(Player player) {
        Inventory inv = Bukkit.createInventory(new AhHolder(GuiTag.CREATE_1, "create1"), 27, "Create Listing");
        for (int i = 0; i < 27; i++) inv.setItem(i, Gfx.filler());
        inv.setItem(4, Gfx.item(Material.PAPER, "&fPlace item in the center", "&7Slot 13 must stay empty"));
        inv.setItem(INPUT_SLOT, null);
        inv.setItem(CANCEL_SLOT, Gfx.red());
        inv.setItem(NEXT_SLOT, Gfx.green());
        player.openInventory(inv);
    }

    public void openStep2(Player player, ItemStack item, String selectedCurrency, int price) {
        Inventory inv = Bukkit.createInventory(new AhHolder(GuiTag.CREATE_2, "create2"), 54, "Set Price");
        for (int i = 0; i < 54; i++) inv.setItem(i, Gfx.filler());

        inv.setItem(4, Gfx.item(item.getType(), "&f" + item.getType().name().replace('_', ' '), "&7x" + item.getAmount()));
        inv.setItem(20, Gfx.item(Material.REDSTONE_BLOCK, "&cCancel"));
        inv.setItem(24, Gfx.item(Material.LIME_DYE, "&aConfirm"));

        List<Currency> currencies = plugin.getCurrencyRegistry().getAll();
        int slot = 19;
        for (Currency c : currencies) {
            inv.setItem(slot++, Gfx.item(c.material(), "&b" + c.displayName(), "&7Select as payment"));
            if (slot > 25) break;
        }

        inv.setItem(30, Gfx.item(Material.REDSTONE, "&c-10"));
        inv.setItem(31, Gfx.item(Material.REDSTONE, "&c-1"));
        inv.setItem(32, Gfx.item(Material.PAPER, "&fPrice: &e" + price));
        inv.setItem(33, Gfx.item(Material.GLOWSTONE_DUST, "&a+1"));
        inv.setItem(34, Gfx.item(Material.GLOWSTONE_DUST, "&a+10"));
        player.openInventory(inv);
    }
}
