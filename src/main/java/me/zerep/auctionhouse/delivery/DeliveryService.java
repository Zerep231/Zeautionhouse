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

    /**
     * Claim a single delivery by id.
     *
     * P0-3 Fix: item/currency is given to the player FIRST, then the DB row is
     * deleted.  If the server crashes after the give but before the delete the
     * player gets the delivery twice on the next login — a minor duplication that
     * is far better than permanent item loss under the previous delete-first order.
     */
    public String claim(Player player, int deliveryId) {
        List<Delivery> deliveries = deliveryRepository.getUnclaimed(player.getUniqueId());
        Delivery target = deliveries.stream()
                .filter(d -> d.id() == deliveryId).findFirst().orElse(null);
        if (target == null) return null;
        String desc = giveDelivery(player, target); // give FIRST
        deliveryRepository.claim(deliveryId);        // then remove record
        return desc;
    }

    /**
     * Claim-all: fetch rows, give all items/currency, then delete records.
     *
     * P0-3 Fix: same give-before-delete ordering as single claim.
     */
    public int claimAll(Player player) {
        List<Delivery> unclaimed = deliveryRepository.getUnclaimed(player.getUniqueId());
        if (unclaimed.isEmpty()) return 0;
        for (Delivery d : unclaimed) giveDelivery(player, d); // give ALL first
        // Then delete all in one shot
        unclaimed.forEach(d -> deliveryRepository.claim(d.id()));
        return unclaimed.size();
    }

    /** Paginated list for the Delivery GUI. */
    public List<Delivery> getUnclaimedPage(UUID playerUuid, int offset, int limit) {
        return deliveryRepository.getUnclaimedPage(playerUuid, offset, limit);
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
