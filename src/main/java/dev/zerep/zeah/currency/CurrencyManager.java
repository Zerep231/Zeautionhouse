package dev.zerep.zeah.currency;

import dev.zerep.zeah.ZeAuctionHouse;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;

public class CurrencyManager {
    private final ZeAuctionHouse plugin;
    private final Map<String, Currency> currencies = new LinkedHashMap<>();
    private String defaultCurrency = "diamond";

    public CurrencyManager(ZeAuctionHouse plugin) {
        this.plugin = plugin;
    }

    public void load() {
        currencies.clear();
        File file = new File(plugin.getDataFolder(), "currencies.yml");
        if (!file.exists()) {
            plugin.saveResource("currencies.yml", false);
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection sec = config.getConfigurationSection("currencies");
        if (sec != null) {
            for (String key : sec.getKeys(false)) {
                String matStr = sec.getString(key + ".material", "DIAMOND").toUpperCase();
                String name = sec.getString(key + ".name", key);
                Material mat;
                try {
                    mat = Material.valueOf(matStr);
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("Invalid material " + matStr + " for currency " + key);
                    continue;
                }
                currencies.put(key.toLowerCase(), new Currency(key.toLowerCase(), mat, name));
            }
        }
        if (currencies.isEmpty()) {
            currencies.put("diamond", new Currency("diamond", Material.DIAMOND, "Diamond"));
        }
        defaultCurrency = currencies.keySet().iterator().next();
    }

    public Map<String, Currency> getCurrencies() {
        return currencies;
    }

    public Currency getCurrency(String id) {
        if (id == null) return currencies.get(defaultCurrency);
        return currencies.getOrDefault(id.toLowerCase(), currencies.get(defaultCurrency));
    }

    public Currency getDefaultCurrency() {
        return getCurrency(defaultCurrency);
    }

    public String format(int amount, String currencyId) {
        Currency c = getCurrency(currencyId);
        return String.format("%,dx %s", amount, c.name());
    }

    public int getBalance(Player player, String currencyId) {
        Material currencyMat = getCurrency(currencyId).material();
        int count = 0;
        for (ItemStack is : player.getInventory().getContents()) {
            if (is != null && is.getType() == currencyMat) count += is.getAmount();
        }
        return count;
    }

    public boolean has(Player player, int amount, String currencyId) {
        return getBalance(player, currencyId) >= amount;
    }

    public boolean withdraw(Player player, int amount, String currencyId) {
        if (!has(player, amount, currencyId)) return false;
        Material currencyMat = getCurrency(currencyId).material();
        ItemStack[] contents = player.getInventory().getContents();
        int remaining = amount;
        for (int i = 0; i < contents.length && remaining > 0; i++) {
            ItemStack is = contents[i];
            if (is == null || is.getType() != currencyMat) continue;
            int take = Math.min(is.getAmount(), remaining);
            if (take >= is.getAmount()) contents[i] = null;
            else is.setAmount(is.getAmount() - take);
            remaining -= take;
        }
        player.getInventory().setContents(contents);
        return true;
    }

    public void deposit(Player player, int amount, String currencyId) {
        Material currencyMat = getCurrency(currencyId).material();
        int maxStack = currencyMat.getMaxStackSize();
        int remaining = amount;
        while (remaining > 0) {
            int give = Math.min(remaining, maxStack);
            var overflow = player.getInventory().addItem(new ItemStack(currencyMat, give));
            overflow.values().forEach(is -> player.getWorld().dropItemNaturally(player.getLocation(), is));
            remaining -= give;
        }
    }

    public ItemStack[] createCurrencyStacks(int amount, String currencyId) {
        Material currencyMat = getCurrency(currencyId).material();
        int maxStack = currencyMat.getMaxStackSize();
        int stacks = (int) Math.ceil((double) amount / maxStack);
        ItemStack[] result = new ItemStack[stacks];
        int remaining = amount;
        for (int i = 0; i < stacks; i++) {
            int give = Math.min(remaining, maxStack);
            result[i] = new ItemStack(currencyMat, give);
            remaining -= give;
        }
        return result;
    }

    public record Currency(String id, Material material, String name) {}
}
