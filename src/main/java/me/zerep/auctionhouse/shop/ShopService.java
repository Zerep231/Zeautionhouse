package me.zerep.auctionhouse.shop;

import me.zerep.auctionhouse.AuctionHousePlugin;
import me.zerep.auctionhouse.delivery.Delivery;
import me.zerep.auctionhouse.delivery.DeliveryRepository;
import me.zerep.auctionhouse.listing.ListingService;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.*;

/**
 * P2.1 Fix – Provides the full shop purchase flow with canBuy / buy / deliver.
 * Categories and items are loaded from config.yml shop.categories section.
 */
public class ShopService {

    public record ShopCategory(String id, String title, List<ShopEntry> items) {}
    public record ShopEntry(String id, Material material, int amount, int price, List<Material> currencies) {}

    private final AuctionHousePlugin plugin;
    private final Map<String, ShopCategory> categories = new LinkedHashMap<>();

    public ShopService(AuctionHousePlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        categories.clear();
        ConfigurationSection root = plugin.getConfig().getConfigurationSection("shop.categories");
        if (root == null) return;
        for (String catId : root.getKeys(false)) {
            ConfigurationSection catSec = root.getConfigurationSection(catId);
            if (catSec == null) continue;
            String title = catSec.getString("title", catId);
            ConfigurationSection itemsSec = catSec.getConfigurationSection("items");
            List<ShopEntry> items = new ArrayList<>();
            if (itemsSec != null) {
                for (String itemId : itemsSec.getKeys(false)) {
                    ConfigurationSection s = itemsSec.getConfigurationSection(itemId);
                    if (s == null) continue;
                    Material material = Material.matchMaterial(s.getString("material", ""));
                    if (material == null) continue;
                    int amount  = Math.max(1, s.getInt("amount", 1));
                    int price   = Math.max(1, s.getInt("price",  1));
                    List<Material> allowed = new ArrayList<>();
                    for (String c : s.getStringList("currencies")) {
                        Material mat = Material.matchMaterial(c);
                        if (mat != null) allowed.add(mat);
                    }
                    if (allowed.isEmpty()) {
                        allowed.addAll(List.of(Material.IRON_INGOT, Material.GOLD_INGOT,
                                Material.EMERALD, Material.DIAMOND));
                    }
                    items.add(new ShopEntry(itemId, material, amount, price, allowed));
                }
            }
            categories.put(catId, new ShopCategory(catId, title, items));
        }
    }

    public List<ShopCategory> getCategories()     { return List.copyOf(categories.values()); }
    public ShopCategory       getCategory(String id) { return categories.get(id); }

    public ShopEntry getEntry(String catId, String itemId) {
        ShopCategory cat = categories.get(catId);
        if (cat == null) return null;
        return cat.items().stream().filter(e -> e.id().equals(itemId)).findFirst().orElse(null);
    }

    public boolean canBuy(Player player, ShopEntry entry, Material currency, int qty) {
        if (entry == null || currency == null) return false;
        if (!entry.currencies().contains(currency)) return false;
        return countCurrency(player, currency) >= (long) entry.price() * qty;
    }

    /**
     * P2.1 – Full buy flow: verify → remove currency → deliver item.
     * Returns true on success.
     */
    public boolean buy(Player player, ShopEntry entry, Material currency, int qty,
                       DeliveryRepository deliveryRepo) {
        int totalPrice = entry.price() * qty;
        if (!canBuy(player, entry, currency, qty)) return false;
        if (!removeCurrency(player, currency, totalPrice)) return false;
        ItemStack item = new ItemStack(entry.material(), entry.amount() * qty);
        ListingService.give(player, item);
        return true;
    }

    private int countCurrency(Player player, Material mat) {
        int total = 0;
        for (ItemStack it : player.getInventory().getContents())
            if (it != null && !it.getType().isAir() && it.getType() == mat) total += it.getAmount();
        return total;
    }

    private boolean removeCurrency(Player player, Material mat, int amount) {
        int remaining = amount;
        var inv = player.getInventory();
        for (int i = 0; i < inv.getSize() && remaining > 0; i++) {
            ItemStack it = inv.getItem(i);
            if (it == null || it.getType().isAir() || it.getType() != mat) continue;
            int take = Math.min(remaining, it.getAmount());
            it.setAmount(it.getAmount() - take);
            remaining -= take;
            if (it.getAmount() <= 0) inv.setItem(i, null);
        }
        player.updateInventory();
        return remaining == 0;
    }
}
