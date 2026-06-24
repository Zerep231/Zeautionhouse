package dev.zerep.zeah.models;

import java.time.Instant;
import java.util.UUID;

public class Listing {

    public enum Status { ACTIVE, SOLD, CANCELLED, EXPIRED }

    private final int id;
    private final UUID sellerUuid;
    private final String sellerName;
    private final byte[] itemData;
    private final double price;
    private Status status;
    private final long createdAt;
    private final long expireAt;

    public Listing(int id, UUID sellerUuid, String sellerName, byte[] itemData,
                   double price, Status status, long createdAt, long expireAt) {
        this.id = id;
        this.sellerUuid = sellerUuid;
        this.sellerName = sellerName;
        this.itemData = itemData;
        this.price = price;
        this.status = status;
        this.createdAt = createdAt;
        this.expireAt = expireAt;
    }

    public boolean isExpired() {
        return Instant.now().getEpochSecond() > expireAt;
    }

    public String getFormattedExpiry() {
        long remaining = expireAt - Instant.now().getEpochSecond();
        if (remaining <= 0) return "Expired";
        long days = remaining / 86400;
        long hours = (remaining % 86400) / 3600;
        if (days > 0) return days + "d " + hours + "h";
        long mins = (remaining % 3600) / 60;
        if (hours > 0) return hours + "h " + mins + "m";
        return mins + "m";
    }

    public int getId() { return id; }
    public UUID getSellerUuid() { return sellerUuid; }
    public String getSellerName() { return sellerName; }
    public byte[] getItemData() { return itemData; }
    public double getPrice() { return price; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public long getCreatedAt() { return createdAt; }
    public long getExpireAt() { return expireAt; }
}
