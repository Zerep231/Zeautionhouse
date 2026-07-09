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
                inventory = Bukkit.createInventory(new ZeAHHolder(), 45, ColorUtil.color(title));

                // Fill with blue glass
                ItemStack blueGlass = buildItem(Material.LIGHT_BLUE_STAINED_GLASS_PANE, " ", List.of());
                for (int i = 0; i < 45; i++) {
                    inventory.setItem(i, blueGlass);
                }

                // Yes buttons (Green)
                ItemStack yesBtn = buildItem(Material.LIME_STAINED_GLASS_PANE,
                    plugin.getLang().getNoPrefix("gui.confirm-yes"),
                    List.of("&7Cost: &6" + plugin.getCurrencyManager().format((int) listing.getPrice(), listing.getCurrencyId())));
                int[] yesSlots = {10, 11, 19, 20, 28, 29};
                for (int slot : yesSlots) {
                    inventory.setItem(slot, yesBtn);
                }

                // No buttons (Red)
                ItemStack noBtn = buildItem(Material.RED_STAINED_GLASS_PANE,
                    plugin.getLang().getNoPrefix("gui.confirm-no"),
                    List.of("&7Return to auction house"));
                int[] noSlots = {15, 16, 24, 25, 33, 34};
                for (int slot : noSlots) {
                    inventory.setItem(slot, noBtn);
                }

                // Empty / Gray areas around the item
                ItemStack grayGlass = filler();
                int[] graySlots = {12, 13, 14, 21, 23, 30, 31, 32};
                for (int slot : graySlots) {
                    inventory.setItem(slot, grayGlass);
                }

                // Cancel Barrier at bottom
                inventory.setItem(40, buildItem(Material.BARRIER, plugin.getLang().getNoPrefix("gui.confirm-no"), List.of("&7Return to auction house")));

                // Show item preview at center
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
                            .replace("{price}", plugin.getCurrencyManager().format((int) listing.getPrice(), listing.getCurrencyId()))
                            .replace("{seller}", listing.getSellerName())));
                    }
                    meta.lore(lore);
                    preview.setItemMeta(meta);
                    inventory.setItem(22, preview);
                } catch (Exception ignored) {}

                player.openInventory(inventory);
                register();
            });
        });
    }

    private boolean isYesSlot(int slot) {
        return slot == 10 || slot == 11 || slot == 19 || slot == 20 || slot == 28 || slot == 29;
    }

    private boolean isNoSlot(int slot) {
        return slot == 15 || slot == 16 || slot == 24 || slot == 25 || slot == 33 || slot == 34 || slot == 40;
    }

    @Override
    public void handleClick(int slot, ClickType clickType, InventoryClickEvent event) {
        event.setCancelled(true);
        if (isYesSlot(slot)) {
            player.closeInventory();
            plugin.getAuctionManager().purchase(player, listingId);
        } else if (isNoSlot(slot)) {
            player.closeInventory();
            new MainAuctionGUI(plugin, player, 0).open();
        }
    }
}
