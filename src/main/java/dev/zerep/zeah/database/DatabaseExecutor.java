package dev.zerep.zeah.database;

import dev.zerep.zeah.models.Delivery;
import dev.zerep.zeah.models.Listing;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface DatabaseExecutor {

    void initialize() throws Exception;
    void close();

    /** Raw connection from the pool — caller must close() it. */
    Connection getConnection() throws SQLException;

    // Listings
    CompletableFuture<Integer> insertListing(UUID sellerUuid, String sellerName,
                                              byte[] itemData, double price, long expireAt);
    CompletableFuture<List<Listing>> getActiveListings();
    CompletableFuture<List<Listing>> getListingsBySeller(UUID sellerUuid);
    CompletableFuture<Listing> getListingById(int id);
    CompletableFuture<Boolean> updateListingStatus(int id, Listing.Status status);

    // Deliveries
    CompletableFuture<Integer> insertDelivery(UUID buyerUuid, String buyerName,
                                               int listingId, byte[] itemData, String reason);
    CompletableFuture<List<Delivery>> getPendingDeliveries(UUID buyerUuid);
    CompletableFuture<Boolean> claimDelivery(int deliveryId);
    CompletableFuture<Integer> countPendingDeliveries(UUID buyerUuid);

    /**
     * Atomic purchase transaction:
     *   1. Mark listing SOLD
     *   2. Create buyer item delivery
     *   3. Create seller currency deliveries (each stack as one delivery row)
     * All three in one DB transaction — crash-safe.
     */
    CompletableFuture<Boolean> executePurchase(int listingId,
                                               UUID buyerUuid, String buyerName, byte[] buyerItemData,
                                               UUID sellerUuid, String sellerName, byte[][] sellerCurrencyData);

    // Atomic cancel: mark listing CANCELLED + create delivery back to seller
    CompletableFuture<Boolean> executeCancel(int listingId,
                                             UUID sellerUuid, String sellerName,
                                             byte[] itemData);

    // Atomic expire: mark listing EXPIRED + create delivery back to seller
    CompletableFuture<Integer> expireOldListings();

    // Audit
    CompletableFuture<Void> insertAuditLog(String type, UUID actor, String details);
}
