package me.zerep.auctionhouse.gui;

import me.zerep.auctionhouse.AuctionHousePlugin;
import me.zerep.auctionhouse.delivery.Delivery;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.util.List;

public class DeliveryGui {
    private final AuctionHousePlugin plugin;

    public DeliveryGui(AuctionHousePlugin plugin) { this.plugin = plugin; }

    public void open(Player player, int page) {
        Inventory inv = Bukkit.createInventory(new AhHolder(GuiTag.DELIVERY, "delivery"), 54, "Delivery Box");
        for (int i = 0; i < 54; i++) inv.setItem(i, Gfx.filler());

        List<Delivery> list = plugin.getDeliveryService().getUnclaimed(player.getUniqueId());
        int slot = 0;
        for (Delivery d : list) {
            if (slot >= 45) break;
            if (d.isItemDelivery()) {
                inv.setItem(slot++, Gfx.item(d.item().getType(), "&f" + d.item().getType().name().replace('_', ' ') + " x" + d.item().getAmount(), "&7Click to claim"));
            } else {
                Material mat = plugin.getCurrencyRegistry().get(d.currency()) != null ? plugin.getCurrencyRegistry().get(d.currency()).material() : Material.DIAMOND;
                inv.setItem(slot++, Gfx.item(mat, "&f" + d.amount() + "x " + plugin.getCurrencyRegistry().displayName(d.currency()), "&7Click to claim"));
            }
        }

        inv.setItem(49, Gfx.item(Material.LIME_DYE, "&aClaim All", "&7Claim everything at once"));
        inv.setItem(53, Gfx.item(Material.ARROW, "&cBack"));
        player.openInventory(inv);
    }
}
