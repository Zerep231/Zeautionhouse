package dev.zerep.zeah.managers;

import dev.zerep.zeah.ZeAuctionHouse;
import dev.zerep.zeah.cache.ListingCache;
import dev.zerep.zeah.models.Listing;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public class CacheManager {

    private final ZeAuctionHouse plugin;
    private final ListingCache cache;

    public CacheManager(ZeAuctionHouse plugin) {
        this.plugin = plugin;
        int ttl = plugin.getConfig().getInt("cache.ttl-seconds", 10);
        this.cache = new ListingCache(ttl);
    }

    /** Ensures cache is fresh, loading from DB if stale. */
    public CompletableFuture<List<Listing>> ensureFresh() {
        if (!cache.isStale()) {
            return CompletableFuture.completedFuture(null);
        }
        return plugin.getDb().getActiveListings().thenApply(listings -> {
            cache.load(listings);
            return listings;
        });
    }

    /** Force reload (e.g., after a write operation). */
    public CompletableFuture<Void> refresh() {
        cache.invalidate();
        return plugin.getDb().getActiveListings().thenAccept(cache::load);
    }

    public void invalidate() {
        cache.invalidate();
    }

    public ListingCache getCache() { return cache; }
}
