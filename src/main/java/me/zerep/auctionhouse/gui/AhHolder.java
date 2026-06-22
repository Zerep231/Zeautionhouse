package me.zerep.auctionhouse.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.HashMap;
import java.util.Map;

/**
 * P1.1 Fix – Stores slot→listingId and slot→deliveryId maps built at GUI-open time.
 * Click handlers look up the real DB id from the map instead of using raw slot
 * position as a stream offset (the old broken approach).
 */
public class AhHolder implements InventoryHolder {

    private final GuiTag tag;
    private final String key;

    /** slot index → listing id */
    private final Map<Integer, Integer> slotToListingId  = new HashMap<>();
    /** slot index → delivery id */
    private final Map<Integer, Integer> slotToDeliveryId = new HashMap<>();
    /** slot index → shop-category id */
    private final Map<Integer, String>  slotToCategoryId = new HashMap<>();
    /** slot index → shop-item id */
    private final Map<Integer, String>  slotToItemId     = new HashMap<>();

    public AhHolder(GuiTag tag, String key) {
        this.tag = tag;
        this.key = key;
    }

    public GuiTag tag() { return tag; }
    public String key() { return key; }

    public void mapListing (int slot, int listingId)   { slotToListingId .put(slot, listingId); }
    public void mapDelivery(int slot, int deliveryId)  { slotToDeliveryId.put(slot, deliveryId); }
    public void mapCategory(int slot, String catId)    { slotToCategoryId.put(slot, catId); }
    public void mapItem    (int slot, String itemId)   { slotToItemId    .put(slot, itemId); }

    public Integer getListing (int slot) { return slotToListingId .get(slot); }
    public Integer getDelivery(int slot) { return slotToDeliveryId.get(slot); }
    public String  getCategory(int slot) { return slotToCategoryId.get(slot); }
    public String  getItemId  (int slot) { return slotToItemId    .get(slot); }

    @Override
    public Inventory getInventory() { return null; }
}
