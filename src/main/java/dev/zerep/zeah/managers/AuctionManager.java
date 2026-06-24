package dev.zerep.zeah.managers;

import dev.zerep.zeah.ZeAuctionHouse;
import dev.zerep.zeah.gui.MainAuctionGUI;
import dev.zerep.zeah.models.Listing;
import dev.zerep.zeah.session.CreateSession;
import dev.zerep.zeah.utils.ColorUtil;
import dev.zerep.zeah.utils.ItemSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public class AuctionManager {

    private final ZeAuctionHouse plugin;
    private BukkitTask expireTask;

    public AuctionManager(ZeAuctionHouse plugin) {
        this.plugin = plugin;
    }

    public void startExpireTask() {
        int intervalMin = plugin.getConfig().getInt("expire.check-interval-minutes", 10);
        long ticks = intervalMin * 60L * 20L;
        expireTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::runExpiry, ticks, ticks);
    }

    private void runExpiry() {
        plugin.getDb().expireOldListings().thenAccept(count -> {
            if (count > 0) {
                plugin.getCacheManager().invalidate();
                plugin.getLogger().info("[ZeAH] Expired " + count + " listing(s).");
            }
        }).exceptionally(ex -> {
            plugin.getLogger().severe("[ZeAH] Expire task failed: " + ex.getMessage());
            return null;
        });
    }

    /** Complete sell after confirmation GUI. */
    public void completeSell(Player player, CreateSession session) {
        if (session.getState() == CreateSession.State.CONFIRMED || session.getState() == CreateSession.State.COMPLETED) {
            return;
        }
        session.confirm();
        int price = (int) session.getPrice();
        ItemStack item = session.getItem();

        int days = plugin.getConfig().getInt("expire.default-days", 7);
        long expireAt = Instant.now().getEpochSecond() + (days * 86400L);
        byte[] data = ItemSerializer.serialize(item);

        plugin.getDb().insertListing(player.getUniqueId(), player.getName(), data, price, expireAt)
            .thenAccept(id -> {
                plugin.getSessionManager().removeSession(player.getUniqueId());
                plugin.getCacheManager().invalidate();
                session.complete();
                Bukkit.getScheduler().runTask(plugin, () ->
                    player.sendMessage(plugin.getLang().format("auction.item-listed",
                        "item", ColorUtil.formatMaterial(item.getType().name()),
                        "price", plugin.getEconomy().format(price),
                        "days", days)));
                plugin.getAuditLogger().log("SELL", player.getUniqueId(),
                    player.getName() + " listed " + item.getType() + " for " + price
                        + "x " + plugin.getEconomy().getCurrencyName());
            }).exceptionally(ex -> {
                plugin.getLogger().severe("Failed to insert listing: " + ex.getMessage());
                Bukkit.getScheduler().runTask(plugin, () -> player.getInventory().addItem(item));
                plugin.getSessionManager().removeSession(player.getUniqueId());
                return null;
            });
    }

    /**
     * Execute purchase — fully atomic:
     * buyer pays currency → listing SOLD → buyer gets item delivery →
     * seller gets currency delivery — all in ONE DB transaction.
     * No separate paymentToSeller call needed.
     */
    public void purchase(Player player, int listingId) {
        plugin.getDb().getListingById(listingId).thenAccept(listing -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (listing == null || listing.getStatus() != Listing.Status.ACTIVE || listing.isExpired()) {
                    player.sendMessage(plugin.getLang().format("auction.listing-not-found")); return;
                }
                if (listing.getSellerUuid().equals(player.getUniqueId())) {
                    player.sendMessage(plugin.getLang().format("auction.own-listing")); return;
                }
                
                // Set listing status locally to prevent spam clicking
                listing.setStatus(Listing.Status.SOLD);

                int price = (int) listing.getPrice();
                int balance = plugin.getEconomy().getBalance(player);
                if (balance < price) {
                    player.sendMessage(plugin.getLang().format("auction.not-enough-items",
                        "price", plugin.getEconomy().format(price),
                        "balance", plugin.getEconomy().format(balance)));
                    return;
                }

                // Deduct currency from buyer BEFORE DB transaction
                if (!plugin.getEconomy().withdraw(player, price)) {
                    player.sendMessage(plugin.getLang().format("auction.not-enough-items",
                        "price", plugin.getEconomy().format(price),
                        "balance", plugin.getEconomy().format(plugin.getEconomy().getBalance(player))));
                    return;
                }

                // Pre-compute seller currency stacks for inclusion in the atomic transaction
                ItemStack[] currencyStacks = plugin.getEconomy().createCurrencyStacks(price);
                byte[][] sellerCurrencyData = Arrays.stream(currencyStacks)
                    .map(ItemSerializer::serialize)
                    .toArray(byte[][]::new);

                // Atomic DB: SOLD + buyer delivery + seller currency deliveries in one transaction
                plugin.getDb().executePurchase(
                    listingId,
                    player.getUniqueId(), player.getName(), listing.getItemData(),
                    listing.getSellerUuid(), listing.getSellerName(), sellerCurrencyData
                ).thenAccept(success -> {
                    if (!success) {
                        // Listing gone — refund buyer
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            plugin.getEconomy().deposit(player, price);
                            player.sendMessage(plugin.getLang().format("auction.listing-not-found"));
                        });
                        return;
                    }

                    plugin.getCacheManager().invalidate();

                    // If seller is online, trigger their mailbox claim silently
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        Player sellerOnline = Bukkit.getPlayer(listing.getSellerUuid());
                        if (sellerOnline != null && sellerOnline.isOnline()) {
                            plugin.getDeliveryManager().tryClaimOnline(sellerOnline);
                            sellerOnline.sendMessage(plugin.getLang().format("auction.seller-sold",
                                "price", plugin.getEconomy().format(price)));
                        }

                        player.sendMessage(plugin.getLang().format("auction.purchase-success",
                            "item", tryGetName(listing),
                            "price", plugin.getEconomy().format(price)));

                        plugin.getDb().countPendingDeliveries(player.getUniqueId())
                            .thenAccept(count -> Bukkit.getScheduler().runTask(plugin, () ->
                                player.sendMessage(plugin.getLang().format("delivery.claim-prompt", "count", count))));
                    });

                    plugin.getAuditLogger().log("BUY", player.getUniqueId(),
                        player.getName() + " bought listing #" + listingId
                            + " for " + price + "x " + plugin.getEconomy().getCurrencyName());

                }).exceptionally(ex -> {
                    plugin.getLogger().severe("Purchase failed: " + ex.getMessage());
                    Bukkit.getScheduler().runTask(plugin, () -> plugin.getEconomy().deposit(player, price));
                    return null;
                });
            });
        });
    }

    /** Cancel a listing — atomic cancel + delivery back to seller. */
    public void cancelListing(Player player, int listingId) {
        plugin.getDb().getListingById(listingId).thenAccept(listing -> {
            if (listing == null || !listing.getSellerUuid().equals(player.getUniqueId())) {
                Bukkit.getScheduler().runTask(plugin, () ->
                    player.sendMessage(plugin.getLang().format("auction.listing-not-found")));
                return;
            }
            plugin.getDb().executeCancel(listingId, player.getUniqueId(), player.getName(), listing.getItemData())
                .thenAccept(success -> {
                    plugin.getCacheManager().invalidate();
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (success) {
                            player.sendMessage(plugin.getLang().format("auction.cancelled"));
                            plugin.getDeliveryManager().tryClaimOnline(player);
                        } else {
                            player.sendMessage(plugin.getLang().format("auction.listing-not-found"));
                        }
                    });
                });
        });
    }

    private String tryGetName(Listing listing) {
        try {
            ItemStack item = ItemSerializer.deserialize(listing.getItemData());
            if (item.hasItemMeta() && item.getItemMeta().hasDisplayName())
                return ColorUtil.strip(item.getItemMeta().displayName().toString());
            return ColorUtil.formatMaterial(item.getType().name());
        } catch (Exception e) { return "item"; }
    }

    public void shutdown() {
        if (expireTask != null) expireTask.cancel();
    }
}
