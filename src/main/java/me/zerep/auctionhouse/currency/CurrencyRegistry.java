package me.zerep.auctionhouse.currency;

import me.zerep.auctionhouse.AuctionHousePlugin;
import org.bukkit.Material;

import java.util.*;

public class CurrencyRegistry {

    private final AuctionHousePlugin plugin;
    private final Map<String, Currency> currencies = new LinkedHashMap<>();

    public CurrencyRegistry(AuctionHousePlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        currencies.clear();
        for (String raw : plugin.getConfig().getStringList("currencies")) {
            Material mat = Material.matchMaterial(raw);
            if (mat == null) continue;
            String key = raw.toUpperCase(Locale.ROOT);
            String display = plugin.getConfig().getString("currency-display." + raw, raw.replace('_', ' '));
            currencies.put(key, new Currency(key, mat, display));
        }
    }

    public List<Currency> getAll()      { return List.copyOf(currencies.values()); }
    public Currency get(String key)     { return key == null ? null : currencies.get(key.toUpperCase(Locale.ROOT)); }
    public String defaultKey()          { return currencies.isEmpty() ? "DIAMOND" : currencies.keySet().iterator().next(); }

    public String displayName(String key) {
        Currency c = get(key);
        return c != null ? c.displayName() : (key != null ? key.replace('_', ' ') : "?");
    }

    public boolean isAllowed(Material mat) {
        return currencies.values().stream().anyMatch(c -> c.material() == mat);
    }
}
