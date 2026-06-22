package me.zerep.auctionhouse;

import me.zerep.auctionhouse.command.AhCommand;
import me.zerep.auctionhouse.currency.CurrencyRegistry;
import me.zerep.auctionhouse.database.DatabaseManager;
import me.zerep.auctionhouse.delivery.DeliveryRepository;
import me.zerep.auctionhouse.delivery.DeliveryService;
import me.zerep.auctionhouse.gui.GuiListener;
import me.zerep.auctionhouse.gui.GuiManager;
import me.zerep.auctionhouse.listing.ListingRepository;
import me.zerep.auctionhouse.listing.ListingService;
import me.zerep.auctionhouse.shop.ShopService;
import org.bukkit.ChatColor;
import org.bukkit.plugin.java.JavaPlugin;

public class AuctionHousePlugin extends JavaPlugin {
    private DatabaseManager db;
    private CurrencyRegistry currencyRegistry;
    private ListingRepository listingRepository;
    private DeliveryRepository deliveryRepository;
    private ListingService listingService;
    private DeliveryService deliveryService;
    private ShopService shopService;
    private GuiManager guiManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        db = new DatabaseManager(this);
        db.initialize();

        currencyRegistry = new CurrencyRegistry(this);
        listingRepository = new ListingRepository(db, getLogger());
        deliveryRepository = new DeliveryRepository(db, getLogger());
        deliveryService = new DeliveryService(this, deliveryRepository);
        listingService = new ListingService(this, listingRepository, deliveryRepository);
        shopService = new ShopService(this);
        guiManager = new GuiManager(this, listingService, deliveryService, shopService);

        var pm = getServer().getPluginManager();
        pm.registerEvents(guiManager, this);
        pm.registerEvents(new GuiListener(this, guiManager), this);

        AhCommand cmd = new AhCommand(this);
        if (getCommand("ah") != null) {
            getCommand("ah").setExecutor(cmd);
            getCommand("ah").setTabCompleter(cmd);
        }

        scheduleExpireTask();
        getLogger().info("AuctionHouse v3.1.1 enabled on Paper 1.21.11+.");
    }

    @Override
    public void onDisable() {
        if (db != null) db.close();
    }

    private void scheduleExpireTask() {
        int minutes = Math.max(1, getConfig().getInt("expire-check-interval-minutes", 10));
        long period = minutes * 20L * 60L;
        getServer().getScheduler().runTaskTimerAsynchronously(this, () -> {
            try { listingService.processExpired(); } catch (Exception e) { getLogger().severe("Expire task failed: " + e.getMessage()); }
        }, period, period);
    }

    public DatabaseManager getDb() { return db; }
    public CurrencyRegistry getCurrencyRegistry() { return currencyRegistry; }
    public ListingRepository getListingRepository() { return listingRepository; }
    public DeliveryRepository getDeliveryRepository() { return deliveryRepository; }
    public ListingService getListingService() { return listingService; }
    public DeliveryService getDeliveryService() { return deliveryService; }
    public ShopService getShopService() { return shopService; }
    public GuiManager getGuiManager() { return guiManager; }

    public String msg(String key) {
        return ChatColor.translateAlternateColorCodes('&', getConfig().getString("messages." + key, "&cMissing message: " + key));
    }
}
