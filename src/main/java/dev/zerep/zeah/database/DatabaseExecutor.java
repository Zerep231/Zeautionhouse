package dev.zerep.zeah.database;

import dev.zerep.zeah.models.Delivery;
import dev.zerep.zeah.models.Listing;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface DatabaseExecutor {

    void initialize() throws Exception;
    void close();

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

    // Atomic purchase: mark listing SOLD + create delivery in one transaction
    CompletableFuture<Boolean> executePurchase(int listingId,
                                               UUID buyerUuid, String buyerName,
                                               byte[] itemData);

    // Atomic cancel: mark listing CANCELLED + create delivery back to seller
    CompletableFuture<Boolean> executeCancel(int listingId,
                                             UUID sellerUuid, String sellerName,
                                             byte[] itemData);

    // Atomic expire: mark listing EXPIRED + create delivery back to seller
    CompletableFuture<Integer> expireOldListings();

    // Audit
    CompletableFuture<Void> insertAuditLog(String type, UUID actor, String details);
}
