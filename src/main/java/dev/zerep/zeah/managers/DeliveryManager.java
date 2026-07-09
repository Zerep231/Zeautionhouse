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

    private static class ClaimResult {
        public final int id;
        public final byte[] data;
        public ClaimResult(int id, byte[] data) {
            this.id = id;
            this.data = data;
        }
    }

    public void claimAll(Player player) {
        plugin.getDb().getPendingDeliveries(player.getUniqueId()).thenAccept(deliveries -> {
            if (deliveries.isEmpty()) {
                Bukkit.getScheduler().runTask(plugin, () ->
                    player.sendMessage(plugin.getLang().format("delivery.none")));
                return;
            }

            List<CompletableFuture<ClaimResult>> futures = deliveries.stream()
                .map(delivery -> plugin.getDb().claimDelivery(delivery.getId())
                    .thenApply(success -> success ? new ClaimResult(delivery.getId(), delivery.getItemData()) : null))
                .collect(Collectors.toList());

            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenRun(() -> {
                    List<ClaimResult> claimed = futures.stream()
                        .map(f -> f.getNow(null))
                        .filter(d -> d != null)
                        .collect(Collectors.toList());

                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (!player.isOnline()) {
                            for (ClaimResult res : claimed) {
                                plugin.getDb().undoClaim(res.id);
                            }
                            return;
                        }

                        int given = 0, overflowed = 0, corrupted = 0;
                        for (ClaimResult res : claimed) {
                            try {
                                ItemStack item = ItemSerializer.deserialize(res.data);
                                var overflow = player.getInventory().addItem(item);
                                if (!overflow.isEmpty()) {
                                    overflow.values().forEach(is ->
                                        player.getWorld().dropItemNaturally(player.getLocation(), is));
                                    overflowed++;
                                } else {
                                    given++;
                                }
                            } catch (Exception e) {
                                plugin.getLogger().severe("Failed to give item (corrupted): " + e.getMessage());
                                plugin.getDb().undoClaim(res.id); // Save for admin fix
                                corrupted++;
                            }
                        }

                        if (given > 0)
                            player.sendMessage(plugin.getLang().format("delivery.claimed-all", "count", given));
                        if (overflowed > 0)
                            player.sendMessage(plugin.getLang().format("delivery.inventory-full", "remaining", overflowed));
                        if (corrupted > 0)
                            player.sendMessage(plugin.getLang().format("delivery.corrupted", "count", corrupted));
                    });
                });
        });
    }

    public void tryClaimOnline(Player player) {
        plugin.getDb().getPendingDeliveries(player.getUniqueId()).thenAccept(deliveries -> {
            if (deliveries.isEmpty()) return;

            List<CompletableFuture<ClaimResult>> futures = deliveries.stream()
                .map(d -> plugin.getDb().claimDelivery(d.getId())
                    .thenApply(ok -> ok ? new ClaimResult(d.getId(), d.getItemData()) : null))
                .collect(Collectors.toList());

            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenRun(() -> {
                    List<ClaimResult> claimed = futures.stream()
                        .map(f -> f.getNow(null)).filter(d -> d != null)
                        .collect(Collectors.toList());

                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (!player.isOnline()) {
                            for (ClaimResult res : claimed) plugin.getDb().undoClaim(res.id);
                            return;
                        }

                        for (ClaimResult res : claimed) {
                            try {
                                ItemStack item = ItemSerializer.deserialize(res.data);
                                var overflow = player.getInventory().addItem(item);
                                overflow.values().forEach(is ->
                                    player.getWorld().dropItemNaturally(player.getLocation(), is));
                            } catch (Exception e) {
                                plugin.getDb().undoClaim(res.id);
                            }
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

    public void notifyOnJoin(Player player) {
        plugin.getDb().countPendingDeliveries(player.getUniqueId()).thenAccept(count -> {
            if (count > 0) Bukkit.getScheduler().runTask(plugin, () ->
                player.sendMessage(plugin.getLang().format("delivery.claim-prompt", "count", count)));
        });
    }
}
