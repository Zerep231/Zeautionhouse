package dev.zerep.zeah.shop;

import org.bukkit.Material;

public class ShopItem {
    private final Material material;
    private final int price;

    public ShopItem(Material material, int price) {
        this.material = material;
        this.price = price;
    }

    public Material getMaterial() { return material; }
    public int getPrice() { return price; }
    public int getTotalPriceInt(int amount) { return price * amount; }
}
