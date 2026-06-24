package dev.zerep.zeah.session;

import org.bukkit.inventory.ItemStack;

import java.util.UUID;

public class CreateSession {

    public enum State {
        STARTED,       // Session created, waiting for price input
        PRICE_SET,     // Price entered, waiting for confirmation
        CONFIRMED,     // Confirmed, processing
        COMPLETED,     // Done, item removed from inventory
        ABORTED        // Cancelled / timed out
    }

    private final UUID playerUuid;
    private ItemStack item;
    private double price;
    private State state;
    private final long createdAt;
    private long lastActivity;

    public CreateSession(UUID playerUuid, ItemStack item) {
        this.playerUuid = playerUuid;
        this.item = item.clone();
        this.state = State.STARTED;
        this.createdAt = System.currentTimeMillis();
        this.lastActivity = createdAt;
    }

    public void setPrice(double price) {
        this.price = price;
        this.state = State.PRICE_SET;
        touch();
    }

    public void confirm() {
        this.state = State.CONFIRMED;
        touch();
    }

    public void complete() {
        this.state = State.COMPLETED;
        touch();
    }

    public void abort() {
        this.state = State.ABORTED;
        touch();
    }

    public void touch() {
        this.lastActivity = System.currentTimeMillis();
    }

    public boolean isTimedOut(long timeoutMs) {
        return (System.currentTimeMillis() - lastActivity) > timeoutMs;
    }

    public UUID getPlayerUuid() { return playerUuid; }
    public ItemStack getItem() { return item.clone(); }
    public double getPrice() { return price; }
    public State getState() { return state; }
    public long getCreatedAt() { return createdAt; }
    public long getLastActivity() { return lastActivity; }
}
