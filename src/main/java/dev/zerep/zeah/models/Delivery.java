package dev.zerep.zeah.models;

import java.util.UUID;

public class Delivery {

    public enum Status { PENDING, CLAIMING, CLAIMED, FAILED }

    private final int id;
    private final UUID buyerUuid;
    private final String buyerName;
    private final int listingId;
    private final byte[] itemData;
    private Status status;
    private final long createdAt;
    private final String reason;

    public Delivery(int id, UUID buyerUuid, String buyerName, int listingId,
                    byte[] itemData, Status status, long createdAt, String reason) {
        this.id = id;
        this.buyerUuid = buyerUuid;
        this.buyerName = buyerName;
        this.listingId = listingId;
        this.itemData = itemData;
        this.status = status;
        this.createdAt = createdAt;
        this.reason = reason;
    }

    public int getId() { return id; }
    public UUID getBuyerUuid() { return buyerUuid; }
    public String getBuyerName() { return buyerName; }
    public int getListingId() { return listingId; }
    public byte[] getItemData() { return itemData; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public long getCreatedAt() { return createdAt; }
    public String getReason() { return reason; }
}
