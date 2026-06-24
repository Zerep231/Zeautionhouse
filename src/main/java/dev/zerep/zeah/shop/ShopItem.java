package dev.zerep.zeah.shop;

import org.bukkit.Material;

public class ShopItem {
    private final Material material;
    private final double price;

    public ShopItem(Material material, double price) {
        this.material = material;
        this.price = price;
    }

    public Material getMaterial() { return material; }
    public double getPrice() { return price; }
    public double getTotalPrice(int amount) { return price * amount; }
}
