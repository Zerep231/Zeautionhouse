package me.zerep.auctionhouse.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public class AhHolder implements InventoryHolder {
    private final GuiTag tag;
    private final String key;

    public AhHolder(GuiTag tag, String key) {
        this.tag = tag;
        this.key = key;
    }

    public GuiTag tag() { return tag; }
    public String key() { return key; }

    @Override
    public Inventory getInventory() { return null; }
}
