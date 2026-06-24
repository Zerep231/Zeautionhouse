package dev.zerep.zeah.gui;

import dev.zerep.zeah.ZeAuctionHouse;
import dev.zerep.zeah.models.Listing;
import dev.zerep.zeah.utils.ColorUtil;
import dev.zerep.zeah.utils.ItemSerializer;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MyListingsGUI extends AuctionGUI {

    private final Map<Integer, Integer> slotToId = new HashMap<>();

    public MyListingsGUI(ZeAuctionHouse plugin, Player player) {
        super(plugin, player);
    }

    @Override
    public void open() {
        plugin.getDb().getListingsBySeller(player.getUniqueId()).thenAccept(listings -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                String title = plugin.getLang().getNoPrefix("gui.my-listings-title");
                int size = Math.max(27, (int)(Math.ceil((listings.size() + 9) / 9.0)) * 9);
                size = Math.min(size, 54);
                inventory = Bukkit.createInventory(null, size, ColorUtil.color(title));

                for (int i = 0; i < size; i++) inventory.setItem(i, filler());
                slotToId.clear();

                for (int i = 0; i < Math.min(listings.size(), size - 9); i++) {
                    Listing listing = listings.get(i);
                    try {
                        ItemStack display = ItemSerializer.deserialize(listing.getItemData()).clone();
                        ItemMeta meta = display.getItemMeta();
                        List<Component> lore = meta.hasLore() ? new ArrayList<>(meta.lore()) : new ArrayList<>();
                        lore.add(Component.empty());
                        for (String line : plugin.getLang().getList("gui.my-listing-lore")) {
                            lore.add(ColorUtil.color(line
                                .replace("{price}", ColorUtil.formatPrice(listing.getPrice()))
                                .replace("{expires}", listing.getFormattedExpiry())));
                        }
                        meta.lore(lore);
                        display.setItemMeta(meta);
                        inventory.setItem(i, display);
                        slotToId.put(i, listing.getId());
                    } catch (Exception ignored) {}
                }

                int navRow = size - 9;
                inventory.setItem(navRow + 4, buildItem(Material.ARROW,
                    "&e« Back to Auction House", List.of("&7Return to main listing")));

                player.openInventory(inventory);
                register();
            });
        });
    }

    @Override
    public void handleClick(int slot, ClickType clickType, InventoryClickEvent event) {
        event.setCancelled(true);
        int navSlot = inventory.getSize() - 5;
        if (slot == navSlot) { player.closeInventory(); new MainAuctionGUI(plugin, player, 0).open(); return; }

        if (slotToId.containsKey(slot)) {
            int listingId = slotToId.get(slot);
            player.closeInventory();
            plugin.getAuctionManager().cancelListing(player, listingId);
        }
    }
}
