package me.zerep.auctionhouse.listing;

import me.zerep.auctionhouse.AuctionHousePlugin;
import me.zerep.auctionhouse.audit.AuditLogRepository;
import me.zerep.auctionhouse.delivery.Delivery;
import me.zerep.auctionhouse.delivery.DeliveryRepository;
import me.zerep.auctionhouse.transaction.TransactionManager;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.UUID;

/**
 * Business logic for listings.
 *
 * P0.2 Fix – purchase() is now fully atomic via TransactionManager:
 *   1. Currency is removed from the player BEFORE the transaction.
 *   2. Inside BEGIN IMMEDIATE … COMMIT the listing row is updated from
 *      ACTIVE → SOLD using a WHERE status='ACTIVE' guard.
 *   3. If that guard fails (double-buy race), the transaction is rolled
 *      back and currency is refunded – no item or money is lost.
 *
 * P1.4 Fix – ListingCache sits in front of DB reads.
 */
public class ListingService {

    public enum BuyResult { SUCCESS, LISTING_GONE, NOT_ENOUGH_CURRENCY, OWN_LISTING, ERROR }

    private final AuctionHousePlugin plugin;
    private final ListingRepository listingRepository;
    private final DeliveryRepository deliveryRepository;
    private final TransactionManager txManager;
    private final AuditLogRepository auditLog;
    private final ListingCache cache = new ListingCache();

    public ListingService(AuctionHousePlugin plugin,
                          ListingRepository listingRepository,
                          DeliveryRepository deliveryRepository,
                          TransactionManager txManager,
                          AuditLogRepository auditLog) {
        this.plugin = plugin;
        this.listingRepository = listingRepository;
        this.deliveryRepository = deliveryRepository;
        this.txManager = txManager;
        this.auditLog = auditLog;
    }

    // ── Cache-backed reads ──────────────────────────────────────────────────

    public List<Listing> getActivePage(int offset, int limit) {
        return cache.getActive(offset, limit, listingRepository::getActiveAll);
    }

    // ── Mutations ───────────────────────────────────────────────────────────

    public int createListing(Player seller, ItemStack item, int price, String currency) {
        int max = plugin.getConfig().getInt("max-per-player", 10);
        if (!seller.hasPermission("ah.bypass-limit")
                && listingRepository.countActive(seller.getUniqueId()) >= max) return -1;
        int id = listingRepository.insert(seller.getUniqueId(), seller.getName(),
                item, item.getAmount(), price, currency);
        if (id > 0) cache.invalidateAll(); // force cache refresh
        return id;
    }

    /**
     * P0.2 Atomic purchase.
     * Must be called from the main thread (touches player inventory).
     */
    public BuyResult purchase(Player buyer, int listingId) {
        // 1. Re-fetch from DB (not cache) to get authoritative state
        Listing listing = listingRepository.getById(listingId);
        if (listing == null || listing.status() != Listing.Status.ACTIVE)
            return BuyResult.LISTING_GONE;
        if (listing.sellerUuid().equals(buyer.getUniqueId()))
            return BuyResult.OWN_LISTING;

        var cur = plugin.getCurrencyRegistry().get(listing.currency());
        if (cur == null) return BuyResult.ERROR;
        Material currencyMat = cur.material();

        if (countCurrency(buyer, currencyMat) < listing.price())
            return BuyResult.NOT_ENOUGH_CURRENCY;

        // 2. Remove currency BEFORE transaction (inventory op must be on main thread)
        if (!removeCurrency(buyer, currencyMat, listing.price())) return BuyResult.ERROR;

        // 3. Atomic DB transaction
        try {
            boolean sold = txManager.execute(conn -> {
                // Guard: only update if still ACTIVE (prevents double-sell)
                boolean updated = listingRepository.updateStatusInTx(
                        conn, listingId, Listing.Status.ACTIVE, Listing.Status.SOLD);
                if (!updated) return false;

                deliveryRepository.insertInTx(
                        conn, buyer.getUniqueId(), Delivery.Type.ITEM_RETURN,
                        listing.item(), 0, null);
                deliveryRepository.insertInTx(
                        conn, listing.sellerUuid(), Delivery.Type.SALE,
                        null, listing.price(), listing.currency());
                auditLog.log(conn, AuditLogRepository.Action.PURCHASE,
                        listingId, buyer.getUniqueId(), listing.sellerUuid(),
                        listing.displayName(), listing.price(), listing.currency());
                return true;
            });

            if (!sold) {
                // Listing was grabbed by someone else; refund currency
                give(buyer, new ItemStack(currencyMat, listing.price()));
                return BuyResult.LISTING_GONE;
            }
        } catch (Exception e) {
            // Unexpected DB error; refund currency
            give(buyer, new ItemStack(currencyMat, listing.price()));
            plugin.getLogger().severe("purchase tx failed: " + e.getMessage());
            return BuyResult.ERROR;
        }

        cache.invalidate(listingId);

        if (plugin.getConfig().getBoolean("broadcast-sales", true)) {
            plugin.getServer().broadcastMessage(
                    plugin.msg("broadcast-sale")
                            .replace("{buyer}",    buyer.getName())
                            .replace("{item}",     listing.displayName())
                            .replace("{seller}",   listing.sellerName())
                            .replace("{amount}",   String.valueOf(listing.price()))
                            .replace("{currency}", plugin.getCurrencyRegistry().displayName(listing.currency())));
        }
        return BuyResult.SUCCESS;
    }

    public boolean cancelListing(Player seller, int listingId) {
        Listing listing = listingRepository.getById(listingId);
        if (listing == null || listing.status() != Listing.Status.ACTIVE) return false;
        if (!listing.sellerUuid().equals(seller.getUniqueId())) return false;

        try {
            boolean updated = txManager.execute(conn -> {
                // P0-4 Fix: check the guard; if listing was already cancelled by another
                // concurrent request, abort the transaction — do NOT insert a duplicate delivery
                boolean ok = listingRepository.updateStatusInTx(
                        conn, listingId, Listing.Status.ACTIVE, Listing.Status.CANCELLED);
                if (!ok) return false;
                deliveryRepository.insertInTx(
                        conn, seller.getUniqueId(), Delivery.Type.CANCEL_RETURN,
                        listing.item(), 0, null);
                auditLog.log(conn, AuditLogRepository.Action.CANCEL,
                        listingId, null, seller.getUniqueId(),
                        listing.displayName(), 0, listing.currency());
                return true;
            });
            if (!updated) return false;
        } catch (Exception e) {
            plugin.getLogger().severe("cancelListing tx failed: " + e.getMessage());
            return false;
        }

        cache.invalidate(listingId);
        return true;
    }

    public void processExpired() {
        int expireHours = plugin.getConfig().getInt("listing-expire-hours", 72);
        List<Listing> expired = listingRepository.getExpired(expireHours);
        for (Listing listing : expired) {
            try {
                txManager.execute(conn -> {
                    boolean updated = listingRepository.updateStatusInTx(
                            conn, listing.id(), Listing.Status.ACTIVE, Listing.Status.EXPIRED);
                    if (!updated) return null;
                    deliveryRepository.insertInTx(
                            conn, listing.sellerUuid(), Delivery.Type.EXPIRED_RETURN,
                            listing.item(), 0, null);
                    auditLog.log(conn, AuditLogRepository.Action.EXPIRE,
                            listing.id(), null, listing.sellerUuid(),
                            listing.displayName(), 0, listing.currency());
                    return null;
                });
                cache.invalidate(listing.id());
            } catch (Exception e) {
                plugin.getLogger().severe("expire tx failed for id=" + listing.id() + ": " + e.getMessage());
            }
        }
    }

    public int countActive(Player player) {
        return listingRepository.countActive(player.getUniqueId());
    }

    // ── Static helpers ──────────────────────────────────────────────────────

    public static boolean give(Player player, ItemStack item) {
        if (item == null || item.getType().isAir()) return true;
        var leftovers = player.getInventory().addItem(item);
        leftovers.values().forEach(l -> player.getWorld().dropItemNaturally(player.getLocation(), l));
        player.updateInventory();
        return true;
    }

    private int countCurrency(Player player, Material mat) {
        int total = 0;
        // Scan main inventory
        for (ItemStack it : player.getInventory().getContents())
            if (it != null && !it.getType().isAir() && it.getType() == mat) total += it.getAmount();
        // Also scan off-hand slot (players could hide currency there to bypass checks)
        ItemStack offhand = player.getInventory().getItemInOffHand();
        if (!offhand.getType().isAir() && offhand.getType() == mat) total += offhand.getAmount();
        return total;
    }

    private boolean removeCurrency(Player player, Material mat, int amount) {
        int remaining = amount;
        var inv = player.getInventory();

        // Remove from main inventory first
        ItemStack[] contents = inv.getContents();
        for (int i = 0; i < contents.length && remaining > 0; i++) {
            ItemStack it = contents[i];
            if (it == null || it.getType().isAir() || it.getType() != mat) continue;
            int take = Math.min(remaining, it.getAmount());
            it.setAmount(it.getAmount() - take);
            remaining -= take;
            if (it.getAmount() <= 0) inv.setItem(i, null);
        }

        // Then remove from off-hand if still needed
        if (remaining > 0) {
            ItemStack offhand = inv.getItemInOffHand();
            if (!offhand.getType().isAir() && offhand.getType() == mat) {
                int take = Math.min(remaining, offhand.getAmount());
                offhand.setAmount(offhand.getAmount() - take);
                remaining -= take;
                if (offhand.getAmount() <= 0) inv.setItemInOffHand(null);
            }
        }

        player.updateInventory();
        return remaining == 0;
    }
}
