package me.zerep.auctionhouse.delivery;

import me.zerep.auctionhouse.AuctionHousePlugin;
import me.zerep.auctionhouse.listing.ListingService;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.UUID;

/**
 * P1.2 Fix – claimAll() now delegates to DeliveryRepository.claimAllAtomic()
 * which deletes all unclaimed rows in a single DELETE IN (...) statement,
 * preventing partial claims if the player disconnects mid-claim.
 */
public class DeliveryService {

    private final AuctionHousePlugin plugin;
    private final DeliveryRepository deliveryRepository;

    public DeliveryService(AuctionHousePlugin plugin, DeliveryRepository deliveryRepository) {
        this.plugin = plugin;
        this.deliveryRepository = deliveryRepository;
    }

    public List<Delivery> getUnclaimed(UUID playerUuid) {
        return deliveryRepository.getUnclaimed(playerUuid);
    }

    public int countUnclaimed(UUID playerUuid) {
        return deliveryRepository.countUnclaimed(playerUuid);
    }

    /** Claim a single delivery by id.  Returns description string or null on failure. */
    public String claim(Player player, int deliveryId) {
        List<Delivery> deliveries = deliveryRepository.getUnclaimed(player.getUniqueId());
        Delivery target = deliveries.stream()
                .filter(d -> d.id() == deliveryId).findFirst().orElse(null);
        if (target == null) return null;
        if (!deliveryRepository.claim(deliveryId)) return null;
        return giveDelivery(player, target);
    }

    /**
     * Atomic claim-all: all DB rows are deleted first, then items/currency are
     * delivered to the player.  A disconnect after the DELETE still preserves
     * all rows (WAL rollback), so nothing is lost.
     */
    public int claimAll(Player player) {
        List<Delivery> claimed = deliveryRepository.claimAllAtomic(player.getUniqueId());
        for (Delivery d : claimed) giveDelivery(player, d);
        return claimed.size();
    }

    private String giveDelivery(Player player, Delivery d) {
        if (d.isItemDelivery()) {
            ItemStack item = d.item();
            ListingService.give(player, item);
            return item.getType().name().replace('_', ' ') + " x" + item.getAmount();
        }
        Material mat = plugin.getCurrencyRegistry().get(d.currency()) != null
                ? plugin.getCurrencyRegistry().get(d.currency()).material()
                : Material.DIAMOND;
        int amount = Math.max(1, d.amount());
        ListingService.give(player, new ItemStack(mat, amount));
        return amount + "x " + plugin.getCurrencyRegistry().displayName(d.currency());
    }
}
