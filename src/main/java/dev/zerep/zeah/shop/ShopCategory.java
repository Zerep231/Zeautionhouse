package dev.zerep.zeah.shop;

import org.bukkit.Material;

import java.util.List;

public class ShopCategory {
    private final String id;
    private final String name;
    private final Material icon;
    private final List<ShopItem> items;

    public ShopCategory(String id, String name, Material icon, List<ShopItem> items) {
        this.id = id;
        this.name = name;
        this.icon = icon;
        this.items = items;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public Material getIcon() { return icon; }
    public List<ShopItem> getItems() { return items; }
}
