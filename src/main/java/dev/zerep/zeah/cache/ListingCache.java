package dev.zerep.zeah.cache;

import dev.zerep.zeah.models.Listing;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Thread-safe in-memory cache for active listings.
 * TTL-based expiry with immediate invalidation on writes.
 */
public class ListingCache {

    private final long ttlMs;
    private volatile long lastLoad = 0;
    private final List<Listing> cached = new CopyOnWriteArrayList<>();
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    public ListingCache(int ttlSeconds) {
        this.ttlMs = ttlSeconds * 1000L;
    }

    public boolean isStale() {
        return (System.currentTimeMillis() - lastLoad) > ttlMs;
    }

    /** Load fresh data into the cache. */
    public void load(List<Listing> listings) {
        lock.writeLock().lock();
        try {
            cached.clear();
            cached.addAll(listings);
            lastLoad = System.currentTimeMillis();
        } finally {
            lock.writeLock().unlock();
        }
    }

    /** Force next read to reload from DB. */
    public void invalidate() {
        lock.writeLock().lock();
        try {
            lastLoad = 0;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /** Get a page of listings (0-indexed). Returns empty list if out of range. */
    public List<Listing> getPage(int page, int pageSize) {
        lock.readLock().lock();
        try {
            int from = page * pageSize;
            if (from >= cached.size()) return Collections.emptyList();
            int to = Math.min(from + pageSize, cached.size());
            return new ArrayList<>(cached.subList(from, to));
        } finally {
            lock.readLock().unlock();
        }
    }

    public int getTotalPages(int pageSize) {
        lock.readLock().lock();
        try {
            if (cached.isEmpty()) return 1;
            return (int) Math.ceil((double) cached.size() / pageSize);
        } finally {
            lock.readLock().unlock();
        }
    }

    public int getTotalListings() {
        return cached.size();
    }

    public Optional<Listing> getById(int id) {
        lock.readLock().lock();
        try {
            return cached.stream().filter(l -> l.getId() == id).findFirst();
        } finally {
            lock.readLock().unlock();
        }
    }
}
