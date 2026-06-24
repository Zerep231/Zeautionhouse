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
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class MainAuctionGUI extends AuctionGUI {

    private static final int PAGE_SIZE = 45; // 5 rows of 9
    private int page;
    // slot -> listing id for click handling
    private final Map<Integer, Integer> slotToListingId = new ConcurrentHashMap<>();

    public MainAuctionGUI(ZeAuctionHouse plugin, Player player, int page) {
        super(plugin, player);
        this.page = page;
    }

    @Override
    public void open() {
        plugin.getCacheManager().ensureFresh().thenAccept(listings -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                int totalPages = plugin.getCacheManager().getCache().getTotalPages(PAGE_SIZE);
                String title = plugin.getLang().formatNoPrefix("gui.ah-title",
                    "page", page + 1, "total", Math.max(1, totalPages));
                inventory = Bukkit.createInventory(null, 54, ColorUtil.color(title));
                slotToListingId.clear();

                List<Listing> pageListing = plugin.getCacheManager().getCache().getPage(page, PAGE_SIZE);

                for (int i = 0; i < pageListing.size(); i++) {
                    Listing listing = pageListing.get(i);
                    try {
                        ItemStack display = ItemSerializer.deserialize(listing.getItemData()).clone();
                        ItemMeta meta = display.getItemMeta();
                        // Append lore
                        List<Component> lore = meta.hasLore() ? new ArrayList<>(meta.lore()) : new ArrayList<>();
                        lore.add(Component.empty());
                        for (String line : plugin.getLang().getList("gui.listing-lore")) {
                            lore.add(ColorUtil.color(line
                                .replace("{seller}", listing.getSellerName())
                                .replace("{price}", ColorUtil.formatPrice(listing.getPrice()))
                                .replace("{expires}", listing.getFormattedExpiry())));
                        }
                        meta.lore(lore);
                        display.setItemMeta(meta);
                        inventory.setItem(i, display);
                        slotToListingId.put(i, listing.getId());
                    } catch (Exception ignored) {}
                }

                // Fill empty slots in items area
                for (int i = pageListing.size(); i < PAGE_SIZE; i++) {
                    inventory.setItem(i, grayFiller());
                }

                // Bottom navigation row (row 6)
                for (int i = 45; i < 54; i++) inventory.setItem(i, filler());
                setNavItem(49, page > 0, page + 1 < totalPages);

                // Action buttons
                inventory.setItem(45, buildItem(Material.BOOK,
                    plugin.getLang().getNoPrefix("gui.my-listings-button") != null
                        ? plugin.getLang().getNoPrefix("gui.my-listings-button") : "&eMyListings",
                    List.of("&7View your active listings")));
                inventory.setItem(46, buildItem(Material.CHEST,
                    "&eClaim Items",
                    List.of("&7Collect items from your mailbox")));

                player.openInventory(inventory);
                register();
            });
        }).exceptionally(ex -> {
            plugin.getLogger().severe("Failed to load auction house: " + ex.getMessage());
            return null;
        });
    }

    @Override
    public void handleClick(int slot, ClickType clickType, InventoryClickEvent event) {
        event.setCancelled(true);

        // Navigation bar
        if (slot == 47) { player.closeInventory(); new MainAuctionGUI(plugin, player, page - 1).open(); return; }
        if (slot == 49) { player.closeInventory(); return; }
        if (slot == 51) { player.closeInventory(); new MainAuctionGUI(plugin, player, page + 1).open(); return; }
        if (slot == 45) { player.closeInventory(); new MyListingsGUI(plugin, player).open(); return; }
        if (slot == 46) { player.closeInventory(); plugin.getDeliveryManager().claimAll(player); return; }

        // Listing click (left click only for Bedrock-friendly)
        if (slot < PAGE_SIZE && slotToListingId.containsKey(slot)) {
            int listingId = slotToListingId.get(slot);
            player.closeInventory();
            new ConfirmBuyGUI(plugin, player, listingId).open();
        }
    }

    public Map<Integer, Integer> getSlotMap() { return slotToListingId; }
}
