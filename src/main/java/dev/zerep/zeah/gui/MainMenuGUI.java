package dev.zerep.zeah.gui;

import dev.zerep.zeah.ZeAuctionHouse;
import dev.zerep.zeah.utils.ColorUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.util.List;

/**
 * /ah — main hub menu. All features accessible from one place.
 */
public class MainMenuGUI extends AuctionGUI {

    public MainMenuGUI(ZeAuctionHouse plugin, Player player) {
        super(plugin, player);
    }

    @Override
    public void open() {
        inventory = Bukkit.createInventory(null, 36,
            ColorUtil.color(plugin.getLang().getNoPrefix("gui.main-menu.title")));

        for (int i = 0; i < 36; i++) inventory.setItem(i, filler());

        // Row 1 ─ core market
        inventory.setItem(10, buildItem(Material.BOOK,
            plugin.getLang().getNoPrefix("gui.main-menu.browse"),
            List.of(
                "&7Browse all active listings",
                "&7Find items others are selling",
                "",
                "&eClick to open"
            )));

        inventory.setItem(12, buildItem(Material.GOLD_INGOT,
            plugin.getLang().getNoPrefix("gui.main-menu.sell"),
            List.of(
                "&7List an item for sale",
                "&7Hold the item you want to sell",
                "",
                "&eClick to open",
                "&7Requires: &ezeah.sell"
            )));

        inventory.setItem(14, buildItem(Material.PAPER,
            plugin.getLang().getNoPrefix("gui.main-menu.my-listings"),
            List.of(
                "&7View and cancel your listings",
                "",
                "&eClick to open"
            )));

        inventory.setItem(16, buildItem(Material.CHEST,
            plugin.getLang().getNoPrefix("gui.main-menu.claim"),
            List.of(
                "&7Collect items from your mailbox",
                "&7Items go here after a purchase",
                "&7or when a listing expires",
                "",
                "&eClick to open"
            )));

        // Row 2 ─ shop (only if enabled)
        if (plugin.getConfig().getBoolean("shop.enabled", true)) {
            inventory.setItem(21, buildItem(Material.BRICKS,
                plugin.getLang().getNoPrefix("gui.main-menu.shop"),
                List.of(
                    "&7Buy building materials directly",
                    "&7Stone, Wood, Glass, Plants...",
                    "",
                    "&eClick to open",
                    "&7Requires: &ezeah.shop"
                )));
        }

        // Row 3 ─ close
        inventory.setItem(31, buildItem(Material.BARRIER,
            plugin.getLang().getNoPrefix("gui.close"), null));

        player.openInventory(inventory);
        register();
    }

    @Override
    public void handleClick(int slot, ClickType click, InventoryClickEvent event) {
        event.setCancelled(true);
        switch (slot) {
            case 10 -> { player.closeInventory(); new MainAuctionGUI(plugin, player, 0).open(); }
            case 12 -> {
                if (!player.hasPermission("zeah.sell")) {
                    player.sendMessage(plugin.getLang().format("no-permission")); return;
                }
                player.closeInventory();
                new SellGUI(plugin, player).open();
            }
            case 14 -> { player.closeInventory(); new MyListingsGUI(plugin, player).open(); }
            case 16 -> { player.closeInventory(); plugin.getDeliveryManager().claimAll(player); }
            case 21 -> {
                if (!plugin.getConfig().getBoolean("shop.enabled", true)) return;
                if (!player.hasPermission("zeah.shop")) {
                    player.sendMessage(plugin.getLang().format("no-permission")); return;
                }
                player.closeInventory();
                new ShopGUI(plugin, player).open();
            }
            case 31 -> player.closeInventory();
        }
    }
}
