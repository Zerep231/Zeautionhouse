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
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

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

    /** Begin sell flow: validate item, session, limits. */
    public void startSell(Player player, int price) {
        if (!player.hasPermission("zeah.sell")) {
            player.sendMessage(plugin.getLang().format("no-permission")); return;
        }
        ItemStack held = player.getInventory().getItemInMainHand();
        if (held.getType().isAir() || held.getType() == Material.AIR) {
            player.sendMessage(plugin.getLang().format("auction.hold-item")); return;
        }
        // Blacklist check
        Set<String> blacklist = plugin.getConfig().getStringList("blacklist")
            .stream().map(String::toUpperCase).collect(Collectors.toSet());
        if (blacklist.contains(held.getType().name()) && !player.hasPermission("zeah.bypass.blacklist")) {
            player.sendMessage(plugin.getLang().format("auction.blacklisted")); return;
        }
        // Price limits
        int minPrice = plugin.getConfig().getInt("limits.min-price", 1);
        int maxPrice = plugin.getConfig().getInt("limits.max-price", 9999);
        if (price < minPrice) {
            player.sendMessage(plugin.getLang().format("auction.price-too-low",
                "min", plugin.getEconomy().format(minPrice))); return;
        }
        if (price > maxPrice) {
            player.sendMessage(plugin.getLang().format("auction.price-too-high",
                "max", plugin.getEconomy().format(maxPrice))); return;
        }
        // Currency item cannot be sold
        if (held.getType() == plugin.getEconomy().getCurrencyMaterial()
                && !player.hasPermission("zeah.bypass.blacklist")) {
            player.sendMessage(plugin.getLang().format("auction.blacklisted")); return;
        }
        // Session check
        if (plugin.getSessionManager().hasSession(player.getUniqueId())) {
            player.sendMessage(plugin.getLang().format("auction.session-active")); return;
        }

        // Listing limit check (async)
        plugin.getDb().getListingsBySeller(player.getUniqueId()).thenAccept(listings -> {
            int max = plugin.getConfig().getInt("limits.max-listings-per-player", 10);
            if (listings.size() >= max && !player.hasPermission("zeah.bypass.limit")) {
                Bukkit.getScheduler().runTask(plugin, () ->
                    player.sendMessage(plugin.getLang().format("auction.max-listings",
                        "current", listings.size(), "max", max)));
                return;
            }
            Bukkit.getScheduler().runTask(plugin, () -> {
                ItemStack item = held.clone();
                if (!plugin.getSessionManager().createSession(player, item)) {
                    player.sendMessage(plugin.getLang().format("auction.session-active")); return;
                }
                plugin.getSessionManager().getSession(player.getUniqueId()).ifPresent(session -> {
                    session.setPrice(price);
                    new dev.zerep.zeah.gui.ConfirmSellGUI(plugin, player, session).open();
                });
            });
        });
    }

    /** Complete sell after confirmation GUI. */
    public void completeSell(Player player, CreateSession session) {
        session.confirm();
        int price = (int) session.getPrice();
        ItemStack item = session.getItem();

        player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));

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

    /** Execute purchase — buyer pays with items, seller receives via delivery. */
    public void purchase(Player player, int listingId) {
        plugin.getDb().getListingById(listingId).thenAccept(listing -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (listing == null || listing.getStatus() != Listing.Status.ACTIVE || listing.isExpired()) {
                    player.sendMessage(plugin.getLang().format("auction.listing-not-found")); return;
                }
                if (listing.getSellerUuid().equals(player.getUniqueId())) {
                    player.sendMessage(plugin.getLang().format("auction.own-listing")); return;
                }

                int price = (int) listing.getPrice();
                int balance = plugin.getEconomy().getBalance(player);

                if (balance < price) {
                    player.sendMessage(plugin.getLang().format("auction.not-enough-items",
                        "price", plugin.getEconomy().format(price),
                        "balance", plugin.getEconomy().format(balance)));
                    return;
                }

                // Deduct currency items from buyer immediately
                if (!plugin.getEconomy().withdraw(player, price)) {
                    player.sendMessage(plugin.getLang().format("auction.not-enough-items",
                        "price", plugin.getEconomy().format(price),
                        "balance", plugin.getEconomy().format(plugin.getEconomy().getBalance(player))));
                    return;
                }

                // Atomic DB: mark listing SOLD + create buyer delivery
                plugin.getDb().executePurchase(listingId, player.getUniqueId(), player.getName(), listing.getItemData())
                    .thenAccept(success -> {
                        if (!success) {
                            // Listing gone — refund items to buyer
                            Bukkit.getScheduler().runTask(plugin, () -> {
                                plugin.getEconomy().deposit(player, price);
                                player.sendMessage(plugin.getLang().format("auction.listing-not-found"));
                            });
                            return;
                        }

                        // Pay seller: directly if online, via delivery if offline
                        UUID sellerUuid = listing.getSellerUuid();
                        paymentToSeller(sellerUuid, listing.getSellerName(), listingId, price);

                        plugin.getCacheManager().invalidate();

                        Bukkit.getScheduler().runTask(plugin, () -> {
                            player.sendMessage(plugin.getLang().format("auction.purchase-success",
                                "item", tryGetName(listing),
                                "price", plugin.getEconomy().format(price)));
                            // Notify to claim purchased item
                            plugin.getDb().countPendingDeliveries(player.getUniqueId()).thenAccept(count ->
                                Bukkit.getScheduler().runTask(plugin, () ->
                                    player.sendMessage(plugin.getLang().format("delivery.claim-prompt", "count", count))));
                        });

                        plugin.getAuditLogger().log("BUY", player.getUniqueId(),
                            player.getName() + " bought listing #" + listingId
                                + " for " + price + "x " + plugin.getEconomy().getCurrencyName());
                    }).exceptionally(ex -> {
                        plugin.getLogger().severe("Purchase failed: " + ex.getMessage());
                        // Refund buyer
                        Bukkit.getScheduler().runTask(plugin, () -> plugin.getEconomy().deposit(player, price));
                        return null;
                    });
            });
        });
    }

    /**
     * Pay the seller: give currency items directly if online,
     * otherwise queue a delivery they can /ah claim later.
     */
    private void paymentToSeller(UUID sellerUuid, String sellerName, int listingId, int amount) {
        Player sellerOnline = Bukkit.getPlayer(sellerUuid);
        if (sellerOnline != null && sellerOnline.isOnline()) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                plugin.getEconomy().deposit(sellerOnline, amount);
                sellerOnline.sendMessage(plugin.getLang().format("auction.seller-sold",
                    "price", plugin.getEconomy().format(amount)));
            });
        } else {
            // Insert currency items as a delivery for the offline seller
            ItemStack[] stacks = plugin.getEconomy().createCurrencyStacks(amount);
            for (ItemStack stack : stacks) {
                byte[] data = dev.zerep.zeah.utils.ItemSerializer.serialize(stack);
                plugin.getDb().insertDelivery(sellerUuid, sellerName, listingId, data, "sale_payment");
            }
        }
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
            if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
                return ColorUtil.strip(item.getItemMeta().displayName().toString());
            }
            return ColorUtil.formatMaterial(item.getType().name());
        } catch (Exception e) { return "item"; }
    }

    public void shutdown() {
        if (expireTask != null) expireTask.cancel();
    }
}
