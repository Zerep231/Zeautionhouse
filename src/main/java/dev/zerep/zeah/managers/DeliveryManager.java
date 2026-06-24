package dev.zerep.zeah.managers;

import dev.zerep.zeah.ZeAuctionHouse;
import dev.zerep.zeah.models.Delivery;
import dev.zerep.zeah.utils.ItemSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class DeliveryManager {

    private final ZeAuctionHouse plugin;

    public DeliveryManager(ZeAuctionHouse plugin) {
        this.plugin = plugin;
    }

    /** Claim all pending deliveries for a player. */
    public void claimAll(Player player) {
        plugin.getDb().getPendingDeliveries(player.getUniqueId()).thenAccept(deliveries -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (deliveries.isEmpty()) {
                    player.sendMessage(plugin.getLang().format("delivery.none")); return;
                }
                AtomicInteger claimed = new AtomicInteger(0);
                AtomicInteger remaining = new AtomicInteger(0);

                for (Delivery delivery : deliveries) {
                    // Atomic claim (PENDING → CLAIMING → CLAIMED)
                    plugin.getDb().claimDelivery(delivery.getId()).thenAccept(success -> {
                        if (!success) return;
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            try {
                                ItemStack item = ItemSerializer.deserialize(delivery.getItemData());
                                if (player.getInventory().firstEmpty() == -1) {
                                    // Drop on ground as last resort
                                    player.getWorld().dropItemNaturally(player.getLocation(), item);
                                } else {
                                    player.getInventory().addItem(item);
                                }
                                claimed.incrementAndGet();
                            } catch (Exception e) {
                                plugin.getLogger().severe("Failed to give item: " + e.getMessage());
                                remaining.incrementAndGet();
                            }
                        });
                    });
                }

                // Summary message after slight delay to let all async ops complete
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (claimed.get() > 0)
                        player.sendMessage(plugin.getLang().format("delivery.claimed-all", "count", claimed.get()));
                    if (remaining.get() > 0)
                        player.sendMessage(plugin.getLang().format("delivery.inventory-full", "remaining", remaining.get()));
                }, 10L);
            });
        });
    }

    /** Silently try to claim if player is online after a purchase/cancel. */
    public void tryClaimOnline(Player player) {
        plugin.getDb().getPendingDeliveries(player.getUniqueId()).thenAccept(deliveries -> {
            if (deliveries.isEmpty()) return;
            Bukkit.getScheduler().runTask(plugin, () -> {
                for (Delivery delivery : deliveries) {
                    if (player.getInventory().firstEmpty() == -1) break;
                    plugin.getDb().claimDelivery(delivery.getId()).thenAccept(success -> {
                        if (!success) return;
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            try {
                                ItemStack item = ItemSerializer.deserialize(delivery.getItemData());
                                if (player.getInventory().firstEmpty() != -1) {
                                    player.getInventory().addItem(item);
                                }
                            } catch (Exception ignored) {}
                        });
                    });
                }
                // Notify remaining
                plugin.getDb().countPendingDeliveries(player.getUniqueId()).thenAccept(count -> {
                    if (count > 0) Bukkit.getScheduler().runTask(plugin, () ->
                        player.sendMessage(plugin.getLang().format("delivery.claim-prompt", "count", count)));
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
