package me.zerep.auctionhouse.listing;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * P1.4 Fix – Simple in-memory cache for active listings.
 * Reduces DB round-trips on every GUI open; refreshes automatically every 30 s
 * or immediately after a mutating operation (purchase / cancel / expire).
 */
public class ListingCache {

    private static final long TTL_MS = 30_000;

    private final Map<Integer, Listing> byId = new ConcurrentHashMap<>();
    private volatile List<Listing> activeSnapshot = List.of();
    private volatile long lastRefresh = 0L;

    /** Returns active listings slice, refreshing from DB loader if cache is stale. */
    public List<Listing> getActive(int offset, int limit, Supplier<List<Listing>> dbLoader) {
        if (System.currentTimeMillis() - lastRefresh > TTL_MS) {
            refresh(dbLoader);
        }
        List<Listing> snap = activeSnapshot;
        int from = Math.min(offset, snap.size());
        int to   = Math.min(offset + limit, snap.size());
        return snap.subList(from, to);
    }

    /** Returns a single listing by id from cache (no DB fallback). */
    public Listing getById(int id) {
        return byId.get(id);
    }

    /** Force-invalidate a single entry (after purchase / cancel). */
    public void invalidate(int listingId) {
        byId.remove(listingId);
        activeSnapshot = new ArrayList<>(activeSnapshot.stream()
                .filter(l -> l.id() != listingId).toList());
    }

    /** Force-invalidate everything (after expire pass). */
    public void invalidateAll() {
        byId.clear();
        activeSnapshot = List.of();
        lastRefresh = 0L;
    }

    private synchronized void refresh(Supplier<List<Listing>> dbLoader) {
        if (System.currentTimeMillis() - lastRefresh <= TTL_MS) return; // double-check
        List<Listing> fresh = dbLoader.get();
        byId.clear();
        fresh.forEach(l -> byId.put(l.id(), l));
        activeSnapshot = List.copyOf(fresh);
        lastRefresh = System.currentTimeMillis();
    }
}
