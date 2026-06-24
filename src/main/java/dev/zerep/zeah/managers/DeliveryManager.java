package dev.zerep.zeah.managers;

import dev.zerep.zeah.ZeAuctionHouse;
import dev.zerep.zeah.models.Delivery;
import dev.zerep.zeah.utils.ItemSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class DeliveryManager {

    private final ZeAuctionHouse plugin;

    public DeliveryManager(ZeAuctionHouse plugin) {
        this.plugin = plugin;
    }

    /**
     * Claim all pending deliveries for a player.
     * Fix: use CompletableFuture.allOf so the summary message fires AFTER
     * all async DB claims resolve — no 10-tick timing guess.
     */
    public void claimAll(Player player) {
        plugin.getDb().getPendingDeliveries(player.getUniqueId()).thenAccept(deliveries -> {
            if (deliveries.isEmpty()) {
                Bukkit.getScheduler().runTask(plugin, () ->
                    player.sendMessage(plugin.getLang().format("delivery.none")));
                return;
            }

            // Fire all claim DB calls concurrently, collect which item-data to give
            List<CompletableFuture<byte[]>> futures = deliveries.stream()
                .map(delivery -> plugin.getDb().claimDelivery(delivery.getId())
                    .thenApply(success -> success ? delivery.getItemData() : null))
                .collect(Collectors.toList());

            // Give items and show summary ONLY after every future resolves
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenRun(() -> {
                    List<byte[]> claimed = futures.stream()
                        .map(f -> f.getNow(null))
                        .filter(d -> d != null)
                        .collect(Collectors.toList());

                    Bukkit.getScheduler().runTask(plugin, () -> {
                        int given = 0, dropped = 0;
                        for (byte[] data : claimed) {
                            try {
                                ItemStack item = ItemSerializer.deserialize(data);
                                var overflow = player.getInventory().addItem(item);
                                overflow.values().forEach(is ->
                                    player.getWorld().dropItemNaturally(player.getLocation(), is));
                                given++;
                            } catch (Exception e) {
                                plugin.getLogger().severe("Failed to give item: " + e.getMessage());
                                dropped++;
                            }
                        }
                        if (given > 0)
                            player.sendMessage(plugin.getLang().format("delivery.claimed-all", "count", given));
                        if (dropped > 0)
                            player.sendMessage(plugin.getLang().format("delivery.inventory-full", "remaining", dropped));
                    });
                });
        });
    }

    /** Silently claim all pending items for an online player. */
    public void tryClaimOnline(Player player) {
        plugin.getDb().getPendingDeliveries(player.getUniqueId()).thenAccept(deliveries -> {
            if (deliveries.isEmpty()) return;

            List<CompletableFuture<byte[]>> futures = deliveries.stream()
                .map(d -> plugin.getDb().claimDelivery(d.getId())
                    .thenApply(ok -> ok ? d.getItemData() : null))
                .collect(Collectors.toList());

            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenRun(() -> {
                    List<byte[]> claimed = futures.stream()
                        .map(f -> f.getNow(null)).filter(d -> d != null)
                        .collect(Collectors.toList());

                    Bukkit.getScheduler().runTask(plugin, () -> {
                        for (byte[] data : claimed) {
                            try {
                                ItemStack item = ItemSerializer.deserialize(data);
                                var overflow = player.getInventory().addItem(item);
                                overflow.values().forEach(is ->
                                    player.getWorld().dropItemNaturally(player.getLocation(), is));
                            } catch (Exception ignored) {}
                        }
                        plugin.getDb().countPendingDeliveries(player.getUniqueId())
                            .thenAccept(count -> {
                                if (count > 0) Bukkit.getScheduler().runTask(plugin, () ->
                                    player.sendMessage(plugin.getLang().format("delivery.claim-prompt", "count", count)));
                            });
                    });
                });
        });
    }

    /** Notify on join if pending items exist. */
    public void notifyOnJoin(Player player) {
        plugin.getDb().countPendingDeliveries(player.getUniqueId()).thenAccept(count -> {
            if (count > 0) Bukkit.getScheduler().runTask(plugin, () ->
                player.sendMessage(plugin.getLang().format("delivery.claim-prompt", "count", count)));
        });
    }
}
