package dev.zerep.zeah.gui;

import dev.zerep.zeah.ZeAuctionHouse;
import dev.zerep.zeah.session.CreateSession;
import dev.zerep.zeah.utils.ColorUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class SellGUI extends AuctionGUI {
    private ItemStack selectedItem = null;
    private int selectedInvSlot = -1;

    public SellGUI(ZeAuctionHouse plugin, Player player) {
        super(plugin, player);
    }

    @Override
    public void open() {
        inventory = Bukkit.createInventory(new ZeAHHolder(), 27, ColorUtil.color(plugin.getLang().getNoPrefix("gui.sell.title")));
        render();
        player.openInventory(inventory);
        register();
    }

    private void render() {
        // Fill background
        ItemStack bg = filler();
        for (int i = 0; i < 27; i++) {
            inventory.setItem(i, bg);
        }

        // Slot 13 is where the item goes
        if (selectedItem != null) {
            inventory.setItem(13, selectedItem);
        } else {
            inventory.setItem(13, buildItem(Material.BARRIER, "&c&lEmpty", List.of("&7Click an item in your inventory", "&7to place it here")));
        }

        // Slot 15 is Continue
        if (selectedItem != null) {
            inventory.setItem(15, buildItem(Material.LIME_STAINED_GLASS_PANE, "&a&lContinue", List.of("&7Proceed to set price")));
        } else {
            inventory.setItem(15, buildItem(Material.GRAY_STAINED_GLASS_PANE, "&7&lContinue", List.of("&cPlace an item first!")));
        }
    }

    @Override
    public void handleClick(int slot, ClickType clickType, InventoryClickEvent event) {
        event.setCancelled(true);
        
        if (event.getClickedInventory() != null && event.getClickedInventory().equals(player.getInventory())) {
            int bukkit = event.getSlot();
            ItemStack item = player.getInventory().getItem(bukkit);
            if (item == null || item.getType().isAir()) return;

            Set<String> blacklist = plugin.getConfig().getStringList("blacklist").stream().map(String::toUpperCase).collect(Collectors.toSet());
            if (blacklist.contains(item.getType().name()) && !player.hasPermission("zeah.bypass.blacklist")) {
                player.sendMessage(ColorUtil.color("&cCannot sell this item."));
                return;
            }

            // Check if it's a currency item
            for (var c : plugin.getCurrencyManager().getCurrencies().values()) {
                if (c.material() == item.getType()) {
                    player.sendMessage(ColorUtil.color("&cCannot sell currency items."));
                    return;
                }
            }

            selectedItem = item.clone();
            selectedInvSlot = bukkit;
            render();
            return;
        }

        if (slot == 13) {
            if (selectedItem != null) {
                selectedItem = null;
                selectedInvSlot = -1;
                render();
            }
        } else if (slot == 15) {
            if (selectedItem != null) {
                // Verify item still in inventory
                ItemStack real = player.getInventory().getItem(selectedInvSlot);
                if (real == null || real.getType() != selectedItem.getType()) {
                    player.sendMessage(plugin.getLang().format("auction.hold-item"));
                    selectedItem = null;
                    selectedInvSlot = -1;
                    render();
                    return;
                }

                if (!plugin.getSessionManager().createSession(player, selectedItem)) {
                    player.sendMessage(plugin.getLang().format("auction.session-active"));
                    return;
                }
                plugin.getSessionManager().getSession(player.getUniqueId()).ifPresent(session -> {
                    player.getInventory().setItem(selectedInvSlot, null);
                    player.closeInventory();
                    Bukkit.getScheduler().runTask(plugin, () -> new PriceSelectionGUI(plugin, player, session).open());
                });
            }
        }
    }
}
