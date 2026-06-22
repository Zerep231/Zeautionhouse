package me.zerep.auctionhouse.listing;

import me.zerep.auctionhouse.AuctionHousePlugin;
import me.zerep.auctionhouse.delivery.Delivery;
import me.zerep.auctionhouse.delivery.DeliveryRepository;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public class ListingService {
    public enum BuyResult { SUCCESS, LISTING_GONE, NOT_ENOUGH_CURRENCY, OWN_LISTING, ERROR }

    private final AuctionHousePlugin plugin;
    private final ListingRepository listingRepository;
    private final DeliveryRepository deliveryRepository;

    public ListingService(AuctionHousePlugin plugin, ListingRepository listingRepository, DeliveryRepository deliveryRepository) {
        this.plugin = plugin;
        this.listingRepository = listingRepository;
        this.deliveryRepository = deliveryRepository;
    }

    public int createListing(Player seller, ItemStack item, int price, String currency) {
        int max = plugin.getConfig().getInt("max-per-player", 10);
        if (!seller.hasPermission("ah.bypass-limit") && listingRepository.countActive(seller.getUniqueId()) >= max) return -1;
        return listingRepository.insert(seller.getUniqueId(), seller.getName(), item, item.getAmount(), price, currency);
    }

    public synchronized BuyResult purchase(Player buyer, int listingId) {
        Listing listing = listingRepository.getById(listingId);
        if (listing == null || listing.status() != Listing.Status.ACTIVE) return BuyResult.LISTING_GONE;
        if (listing.sellerUuid().equals(buyer.getUniqueId())) return BuyResult.OWN_LISTING;

        Material currencyMat = plugin.getCurrencyRegistry().get(listing.currency()) == null ? null : plugin.getCurrencyRegistry().get(listing.currency()).material();
        if (currencyMat == null || countCurrency(buyer, currencyMat) < listing.price()) return BuyResult.NOT_ENOUGH_CURRENCY;

        if (!removeCurrency(buyer, currencyMat, listing.price())) return BuyResult.ERROR;

        if (!listingRepository.updateStatus(listingId, Listing.Status.SOLD)) {
            give(buyer, new ItemStack(currencyMat, listing.price()));
            return BuyResult.LISTING_GONE;
        }

        deliveryRepository.insert(buyer.getUniqueId(), Delivery.Type.ITEM_RETURN, listing.item(), 0, null);
        deliveryRepository.insert(listing.sellerUuid(), Delivery.Type.SALE, null, listing.price(), listing.currency());

        if (plugin.getConfig().getBoolean("broadcast-sales", true)) {
            String msg = plugin.msg("broadcast-sale")
                    .replace("{buyer}", buyer.getName())
                    .replace("{item}", listing.displayName())
                    .replace("{seller}", listing.sellerName())
                    .replace("{amount}", String.valueOf(listing.price()))
                    .replace("{currency}", plugin.getCurrencyRegistry().displayName(listing.currency()));
            plugin.getServer().broadcastMessage(msg);
        }
        return BuyResult.SUCCESS;
    }

    public boolean cancelListing(Player seller, int listingId) {
        Listing listing = listingRepository.getById(listingId);
        if (listing == null || listing.status() != Listing.Status.ACTIVE) return false;
        if (!listing.sellerUuid().equals(seller.getUniqueId())) return false;
        if (!listingRepository.updateStatus(listingId, Listing.Status.CANCELLED)) return false;
        deliveryRepository.insert(seller.getUniqueId(), Delivery.Type.CANCEL_RETURN, listing.item(), 0, null);
        return true;
    }

    public void processExpired() {
        int expireHours = plugin.getConfig().getInt("listing-expire-hours", 72);
        List<Listing> expired = listingRepository.getExpired(expireHours);
        for (Listing listing : expired) {
            if (!listingRepository.updateStatus(listing.id(), Listing.Status.EXPIRED)) continue;
            deliveryRepository.insert(listing.sellerUuid(), Delivery.Type.EXPIRED_RETURN, listing.item(), 0, null);
        }
    }

    public int countActive(Player player) { return listingRepository.countActive(player.getUniqueId()); }

    public static boolean give(Player player, ItemStack item) {
        if (item == null || item.getType().isAir()) return true;
        var leftovers = player.getInventory().addItem(item);
        for (ItemStack left : leftovers.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), left);
        }
        player.updateInventory();
        return true;
    }

    private int countCurrency(Player player, Material currencyMat) {
        int total = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item == null || item.getType().isAir()) continue;
            if (item.getType() == currencyMat) total += item.getAmount();
        }
        return total;
    }

    private boolean removeCurrency(Player player, Material currencyMat, int amount) {
        int remaining = amount;
        var inv = player.getInventory();
        ItemStack[] contents = inv.getContents();
        for (int i = 0; i < contents.length && remaining > 0; i++) {
            ItemStack item = contents[i];
            if (item == null || item.getType().isAir() || item.getType() != currencyMat) continue;
            int take = Math.min(remaining, item.getAmount());
            item.setAmount(item.getAmount() - take);
            remaining -= take;
            if (item.getAmount() <= 0) inv.setItem(i, null);
        }
        player.updateInventory();
        return remaining == 0;
    }
}
