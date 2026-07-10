package dev.zerep.zeah.shop;

import org.bukkit.Material;

public class ShopItem {
    private final Material material;
    private final int price;
    private final String currencyId;

    public ShopItem(Material material, int price, String currencyId) {
        this.material = material;
        this.price = price;
        this.currencyId = currencyId;
    }

    public Material getMaterial() { return material; }
    public int getPrice() { return price; }
    public String getCurrencyId() { return currencyId; }
    public int getTotalPriceInt(int amount) { return price * amount; }
}
