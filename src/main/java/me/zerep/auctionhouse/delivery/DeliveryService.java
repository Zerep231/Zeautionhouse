package me.zerep.auctionhouse.delivery;

import me.zerep.auctionhouse.AuctionHousePlugin;
import me.zerep.auctionhouse.listing.ListingService;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.UUID;

public class DeliveryService {
    private final AuctionHousePlugin plugin;
    private final DeliveryRepository deliveryRepository;

    public DeliveryService(AuctionHousePlugin plugin, DeliveryRepository deliveryRepository) {
        this.plugin = plugin;
        this.deliveryRepository = deliveryRepository;
    }

    public List<Delivery> getUnclaimed(UUID playerUuid) { return deliveryRepository.getUnclaimed(playerUuid); }
    public int countUnclaimed(UUID playerUuid) { return deliveryRepository.countUnclaimed(playerUuid); }

    public String claim(Player player, int deliveryId) {
        List<Delivery> deliveries = deliveryRepository.getUnclaimed(player.getUniqueId());
        Delivery target = deliveries.stream().filter(d -> d.id() == deliveryId).findFirst().orElse(null);
        if (target == null) return null;
        if (!deliveryRepository.claim(deliveryId)) return null;

        if (target.isItemDelivery()) {
            ItemStack item = target.item();
            ListingService.give(player, item);
            return item.getType().name().replace('_', ' ') + " x" + item.getAmount();
        }

        Material mat = plugin.getCurrencyRegistry().get(target.currency()) != null ? plugin.getCurrencyRegistry().get(target.currency()).material() : Material.DIAMOND;
        int amount = Math.max(1, target.amount());
        ListingService.give(player, new ItemStack(mat, amount));
        return amount + "x " + plugin.getCurrencyRegistry().displayName(target.currency());
    }

    public int claimAll(Player player) {
        int claimed = 0;
        for (Delivery d : getUnclaimed(player.getUniqueId())) {
            if (claim(player, d.id()) != null) claimed++;
        }
        return claimed;
    }
}
