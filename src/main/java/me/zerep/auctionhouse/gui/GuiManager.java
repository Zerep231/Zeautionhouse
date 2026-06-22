package me.zerep.auctionhouse.gui;

import me.zerep.auctionhouse.AuctionHousePlugin;
import me.zerep.auctionhouse.delivery.DeliveryService;
import me.zerep.auctionhouse.listing.ListingService;
import me.zerep.auctionhouse.shop.ShopService;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;

/**
 * P0.1 Fix – The missing GuiManager class.
 *
 * Acts as the central facade for opening all GUIs.  AuctionHousePlugin,
 * AhCommand, and GuiListener all depend on this class.
 *
 * Implements Listener so it can be registered via PluginManager.registerEvents()
 * alongside GuiListener (which handles the actual click / drag / close events).
 *
 * P2.2 – Bedrock detection: if Floodgate is loaded, Bedrock players get a
 * simplified chat-based prompt for the Create flow (they cannot reliably
 * drag items into inventory GUIs) and are given extra guidance messages.
 */
public class GuiManager implements Listener {

    private final AuctionHousePlugin plugin;
    private final ListingService      listingService;
    private final DeliveryService     deliveryService;
    private final ShopService         shopService;

    private final BrowseGui       browseGui;
    private final MineGui         mineGui;
    private final DeliveryGui     deliveryGui;
    private final CreateListingGui createGui;
    private final ShopGui         shopGui;

    private final boolean floodgatePresent;

    public GuiManager(AuctionHousePlugin plugin,
                      ListingService listingService,
                      DeliveryService deliveryService,
                      ShopService shopService) {
        this.plugin          = plugin;
        this.listingService  = listingService;
        this.deliveryService = deliveryService;
        this.shopService     = shopService;

        this.browseGui   = new BrowseGui(plugin);
        this.mineGui     = new MineGui(plugin);
        this.deliveryGui = new DeliveryGui(plugin);
        this.createGui   = new CreateListingGui(plugin);
        this.shopGui     = new ShopGui(plugin);

        this.floodgatePresent =
                plugin.getServer().getPluginManager().getPlugin("floodgate") != null;
    }

    // ── Open methods ─────────────────────────────────────────────────────────

    public void openBrowse(Player player, int page) {
        browseGui.open(player, page);
    }

    public void openMine(Player player, int page) {
        mineGui.open(player, page);
    }

    public void openDelivery(Player player, int page) {
        deliveryGui.open(player, page);
    }

    /**
     * P2.2 – Bedrock players receive a clear text instruction; Java players
     * get the normal Step-1 inventory GUI.
     */
    public void openCreate(Player player) {
        if (isBedrockPlayer(player)) {
            player.sendMessage(plugin.msg("bedrock-sell-hint"));
            return;
        }
        // Clear any stale session before starting fresh
        plugin.getSessionManager().returnItem(player);
        createGui.openStep1(player);
    }

    public void openCreateStep2(Player player) {
        var session = plugin.getSessionManager().get(player.getUniqueId());
        if (session == null) { openBrowse(player, 0); return; }
        createGui.openStep2(player, session);
    }

    public void openShopHome(Player player) {
        shopGui.openHome(player);
    }

    public void openShopCategory(Player player, String catId) {
        shopGui.openCategory(player, catId);
    }

    public void openShopConfirm(Player player, String catId, String itemId,
                                org.bukkit.Material currency) {
        shopGui.openConfirm(player, catId, itemId, currency);
    }

    // ── Bedrock detection ─────────────────────────────────────────────────────

    /**
     * Returns true if Floodgate is loaded AND the player is a Bedrock client.
     * Safe to call when Floodgate is absent (just returns false).
     */
    public boolean isBedrockPlayer(Player player) {
        if (!floodgatePresent) return false;
        try {
            return org.geysermc.floodgate.api.FloodgateApi.getInstance()
                    .isFloodgatePlayer(player.getUniqueId());
        } catch (Throwable t) {
            return false;
        }
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

    public ListingService  getListingService()  { return listingService; }
    public DeliveryService getDeliveryService() { return deliveryService; }
    public ShopService     getShopService()     { return shopService; }
    public CreateListingGui getCreateGui()      { return createGui; }
}
