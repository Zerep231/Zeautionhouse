package dev.zerep.zeah.shop;

import dev.zerep.zeah.ZeAuctionHouse;
import dev.zerep.zeah.utils.ColorUtil;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class ShopManager {

    private final ZeAuctionHouse plugin;
    private final List<ShopCategory> categories = new ArrayList<>();

    public ShopManager(ZeAuctionHouse plugin) {
        this.plugin = plugin;
    }

    public void load() {
        categories.clear();
        List<String> categoryIds = plugin.getConfig().getStringList("shop.categories");
        File shopDir = new File(plugin.getDataFolder(), "shop");
        if (!shopDir.exists()) shopDir.mkdirs();

        for (String id : categoryIds) {
            File file = new File(shopDir, id + ".yml");
            if (!file.exists()) {
                // Save from resources
                InputStream in = plugin.getResource("shop/" + id + ".yml");
                if (in != null) plugin.saveResource("shop/" + id + ".yml", false);
                else continue;
                file = new File(shopDir, id + ".yml");
            }
            if (!file.exists()) continue;

            YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
            String name = cfg.getString("name", id);
            String iconStr = cfg.getString("icon", "CHEST");
            Material icon;
            try { icon = Material.valueOf(iconStr.toUpperCase()); }
            catch (IllegalArgumentException e) { icon = Material.CHEST; }

            List<ShopItem> items = new ArrayList<>();
            for (var entry : cfg.getMapList("items")) {
                String mat = String.valueOf(entry.get("material"));
                double price = entry.containsKey("price") ? Double.parseDouble(String.valueOf(entry.get("price"))) : 1.0;
                try {
                    items.add(new ShopItem(Material.valueOf(mat.toUpperCase()), price));
                } catch (IllegalArgumentException ignored) {}
            }
            categories.add(new ShopCategory(id, name, icon, items));
        }
        plugin.getLogger().info("Loaded " + categories.size() + " shop categories.");
    }

    public void purchase(Player player, ShopItem item, int amount) {
        double total = item.getTotalPrice(amount);
        if (!plugin.getEconomy().has(player, total)) {
            player.sendMessage(plugin.getLang().format("shop.not-enough-money",
                "price", ColorUtil.formatPrice(total)));
            return;
        }
        // Give items
        int remaining = amount;
        while (remaining > 0) {
            int batch = Math.min(remaining, item.getMaterial().getMaxStackSize());
            ItemStack give = new ItemStack(item.getMaterial(), batch);
            if (player.getInventory().firstEmpty() == -1) {
                player.getWorld().dropItemNaturally(player.getLocation(), give);
            } else {
                player.getInventory().addItem(give);
            }
            remaining -= batch;
        }
        plugin.getEconomy().withdrawPlayer(player, total);
        player.sendMessage(plugin.getLang().format("shop.purchase-success",
            "amount", amount,
            "item", ColorUtil.formatMaterial(item.getMaterial().name()),
            "price", ColorUtil.formatPrice(total)));
    }

    public List<ShopCategory> getCategories() { return categories; }
}
