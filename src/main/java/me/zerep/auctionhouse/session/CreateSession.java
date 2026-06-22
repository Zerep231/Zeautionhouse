package me.zerep.auctionhouse.session;

import org.bukkit.inventory.ItemStack;

/**
 * P0.3 + P0.4 Fix – Replaces per-player metadata keys with a typed session object.
 * The real item is captured into the session the moment the player clicks Next in step 1,
 * so a server crash / GUI desync cannot cause item loss.
 */
public class CreateSession {

    private final ItemStack item;   // real item removed from player at session creation
    private String currency;
    private int price;
    private boolean confirmed;      // set to true just before listing is persisted
    private boolean transitioning;  // true while GUI is switching steps (blocks onClose returnItem)
    private final long created;

    public CreateSession(ItemStack item, String defaultCurrency, int defaultPrice) {
        this.item = item.clone();
        this.currency = defaultCurrency;
        this.price = defaultPrice;
        this.confirmed = false;
        this.created = System.currentTimeMillis();
    }

    public ItemStack getItem() { return item; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public int getPrice() { return price; }
    public void setPrice(int price) { this.price = Math.max(1, price); }

    public boolean isConfirmed()   { return confirmed; }
    public void markConfirmed()    { this.confirmed    = true; }

    public boolean isTransitioning() { return transitioning; }
    public void setTransitioning(boolean v) { this.transitioning = v; }

    /** Session older than 10 minutes is considered stale and its item should be returned. */
    public boolean isStale() {
        return System.currentTimeMillis() - created > 10 * 60_000L;
    }
}
