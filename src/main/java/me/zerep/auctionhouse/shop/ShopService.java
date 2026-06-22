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
                    int amount = Math.max(1, s.getInt("amount", 1));
                    int price = Math.max(1, s.getInt("price", 1));
                    List<Material> allowed = new ArrayList<>();
                    for (String c : s.getStringList("currencies")) {
                        Material mat = Material.matchMaterial(c);
                        if (mat != null) allowed.add(mat);
                    }
                    if (allowed.isEmpty()) {
                        allowed.add(Material.IRON_INGOT);
                        allowed.add(Material.GOLD_INGOT);
                        allowed.add(Material.EMERALD);
                        allowed.add(Material.DIAMOND);
                    }
                    items.add(new ShopEntry(itemId, material, amount, price, allowed));
                }
            }
            categories.put(catId, new ShopCategory(catId, title, items));
        }
    }

    public List<ShopCategory> getCategories() { return List.copyOf(categories.values()); }
    public ShopCategory getCategory(String id) { return categories.get(id); }

    public boolean canBuy(Player player, ShopEntry entry, Material currency, int amount) {
        if (entry == null || currency == null) return false;
        if (!entry.currencies().contains(currency)) return false;
        return countCurrency(player, currency) >= entry.price() * amount;
    }

    public boolean buy(Player player, ShopEntry entry, Material currency, int amount, DeliveryRepository deliveryRepo) {
        int totalPrice = entry.price() * amount;
        if (!removeCurrency(player, currency, totalPrice)) return false;
        ItemStack item = new ItemStack(entry.material(), entry.amount() * amount);
        ListingService.give(player, item); // direct delivery for build shop
        return true;
    }

    private int countCurrency(Player player, Material currencyMat) {
        int total = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && !item.getType().isAir() && item.getType() == currencyMat) total += item.getAmount();
        }
        return total;
    }

    private boolean removeCurrency(Player player, Material currencyMat, int amount) {
        int remaining = amount;
        var inv = player.getInventory();
        for (int i = 0; i < inv.getSize() && remaining > 0; i++) {
            ItemStack item = inv.getItem(i);
            if (item == null || item.getType().isAir() || item.getType() != currencyMat) continue;
            int take = Math.min(remaining, item.getAmount());
            item.setAmount(item.getAmount() - take);
            remaining -= take;
            if (item.getAmount() <= 0) inv.setItem(i, null);
        }
        player.updateInventory();
        return remaining == 0;
    }
}
