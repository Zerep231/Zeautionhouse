package dev.zerep.zeah.gui;

import dev.zerep.zeah.ZeAuctionHouse;
import dev.zerep.zeah.models.Listing;
import dev.zerep.zeah.utils.ColorUtil;
import dev.zerep.zeah.utils.ItemSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

public class ConfirmBuyGUI extends AuctionGUI {

    private final int listingId;

    public ConfirmBuyGUI(ZeAuctionHouse plugin, Player player, int listingId) {
        super(plugin, player);
        this.listingId = listingId;
    }

    @Override
    public void open() {
        plugin.getDb().getListingById(listingId).thenAccept(listing -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (listing == null || listing.getStatus() != Listing.Status.ACTIVE) {
                    player.sendMessage(plugin.getLang().format("auction.listing-not-found"));
                    new MainAuctionGUI(plugin, player, 0).open();
                    return;
                }
                String title = plugin.getLang().getNoPrefix("gui.confirm-title");
                inventory = Bukkit.createInventory(new ZeAHHolder(), 27, ColorUtil.color(title));

                // Fill with dark glass
                for (int i = 0; i < 27; i++) inventory.setItem(i, filler());

                // Show item preview at center top
                try {
                    ItemStack preview = ItemSerializer.deserialize(listing.getItemData()).clone();
                    ItemMeta meta = preview.getItemMeta();
                    List<String> loreTpl = plugin.getLang().getList("gui.confirm-buy-lore");
                    String itemName = meta.hasDisplayName()
                        ? ColorUtil.strip(meta.displayName().toString())
                        : ColorUtil.formatMaterial(preview.getType().name());
                    java.util.List<net.kyori.adventure.text.Component> lore = new java.util.ArrayList<>();
                    for (String line : loreTpl) {
                        lore.add(ColorUtil.color(line
                            .replace("{item}", itemName)
                            .replace("{price}", plugin.getEconomy().format((int) listing.getPrice()))
                            .replace("{seller}", listing.getSellerName())));
                    }
                    meta.lore(lore);
                    preview.setItemMeta(meta);
                    inventory.setItem(13, preview);
                } catch (Exception ignored) {}

                // YES button (green)
                inventory.setItem(11, buildItem(Material.LIME_STAINED_GLASS_PANE,
                    plugin.getLang().getNoPrefix("gui.confirm-yes"),
                    List.of("&7Cost: &6" + plugin.getEconomy().format((int) listing.getPrice()))));

                // NO button (red)
                inventory.setItem(15, buildItem(Material.RED_STAINED_GLASS_PANE,
                    plugin.getLang().getNoPrefix("gui.confirm-no"),
                    List.of("&7Return to auction house")));

                player.openInventory(inventory);
                register();
            });
        });
    }

    @Override
    public void handleClick(int slot, ClickType clickType, InventoryClickEvent event) {
        event.setCancelled(true);
        if (slot == 11) {
            player.closeInventory();
            plugin.getAuctionManager().purchase(player, listingId);
        } else if (slot == 15) {
            player.closeInventory();
            new MainAuctionGUI(plugin, player, 0).open();
        }
    }
}
