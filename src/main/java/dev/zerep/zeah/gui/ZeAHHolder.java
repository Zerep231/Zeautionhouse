package dev.zerep.zeah.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/**
 * Marker InventoryHolder so GUIListener can reliably identify
 * ZeAH-owned inventories without fragile title-string matching.
 */
public class ZeAHHolder implements InventoryHolder {
    @Override
    public Inventory getInventory() { return null; }
}
