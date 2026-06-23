package me.zerep.auctionhouse;

import me.zerep.auctionhouse.audit.AuditLogRepository;
import me.zerep.auctionhouse.command.AhCommand;
import me.zerep.auctionhouse.currency.CurrencyRegistry;
import me.zerep.auctionhouse.database.DatabaseManager;
import me.zerep.auctionhouse.delivery.DeliveryRepository;
import me.zerep.auctionhouse.delivery.DeliveryService;
import me.zerep.auctionhouse.gui.GuiListener;
import me.zerep.auctionhouse.gui.GuiManager;
import me.zerep.auctionhouse.listing.ListingRepository;
import me.zerep.auctionhouse.listing.ListingService;
import me.zerep.auctionhouse.session.SessionManager;
import me.zerep.auctionhouse.shop.ShopService;
import me.zerep.auctionhouse.transaction.TransactionManager;
import org.bukkit.ChatColor;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Plugin entry point – wires up all components.
 *
 * P0.1 Fix – GuiManager is now created and registered.
 * P0.3 Fix – SessionManager replaces per-player metadata.
 * P0.2 Fix – TransactionManager injected into ListingService.
 * P1.3 Fix – AuditLogRepository injected into ListingService.
 */
public class AuctionHousePlugin extends JavaPlugin {

    private DatabaseManager    db;
    private CurrencyRegistry   currencyRegistry;
    private SessionManager     sessionManager;
    private ListingRepository  listingRepository;
    private DeliveryRepository deliveryRepository;
    private TransactionManager transactionManager;
    private AuditLogRepository auditLogRepository;
    private ListingService     listingService;
    private DeliveryService    deliveryService;
    private ShopService        shopService;
    private GuiManager         guiManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        db = new DatabaseManager(this);
        db.initialize();

        currencyRegistry   = new CurrencyRegistry(this);
        sessionManager     = new SessionManager();
        transactionManager = new TransactionManager(db, getLogger());
        auditLogRepository = new AuditLogRepository(db, getLogger());
        listingRepository  = new ListingRepository(db, getLogger());
        deliveryRepository = new DeliveryRepository(db, getLogger());
        deliveryService    = new DeliveryService(this, deliveryRepository);
        listingService     = new ListingService(this, listingRepository, deliveryRepository,
                                                transactionManager, auditLogRepository);
        shopService        = new ShopService(this);
        guiManager         = new GuiManager(this, listingService, deliveryService, shopService);

        var pm = getServer().getPluginManager();
        pm.registerEvents(guiManager,                       this);
        pm.registerEvents(new GuiListener(this, guiManager), this);

        AhCommand cmd = new AhCommand(this);
        var ahCmd = getCommand("ah");
        if (ahCmd != null) {
            ahCmd.setExecutor(cmd);
            ahCmd.setTabCompleter(cmd);
        }

        scheduleExpireTask();
        getLogger().info("ZeAuctionHouse v3.2 enabled (Paper 1.21.1+, Geyser-ready).");
    }

    @Override
    public void onDisable() {
        // Return all open create-session items before shutdown
        getServer().getOnlinePlayers().forEach(p -> sessionManager.returnItem(p));
        if (db != null) db.close();
        getLogger().info("ZeAuctionHouse disabled.");
    }

    private void scheduleExpireTask() {
        int minutes = Math.max(1, getConfig().getInt("expire-check-interval-minutes", 10));
        long period = minutes * 20L * 60L;
        getServer().getScheduler().runTaskTimerAsynchronously(this, () -> {
            try { listingService.processExpired(); }
            catch (Exception e) { getLogger().severe("Expire task error: " + e.getMessage()); }
        }, period, period);
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

    public DatabaseManager    getDb()                 { return db; }
    public CurrencyRegistry   getCurrencyRegistry()   { return currencyRegistry; }
    public SessionManager     getSessionManager()     { return sessionManager; }
    public ListingRepository  getListingRepository()  { return listingRepository; }
    public DeliveryRepository getDeliveryRepository() { return deliveryRepository; }
    public ListingService     getListingService()     { return listingService; }
    public DeliveryService    getDeliveryService()    { return deliveryService; }
    public ShopService        getShopService()        { return shopService; }
    public GuiManager         getGuiManager()         { return guiManager; }

    /** Translate &-codes → §-codes for p.sendMessage(String) on Paper 1.21+. */
    @SuppressWarnings("deprecation")
    public String msg(String key) {
        String raw = getConfig().getString("messages." + key, "&cMissing: " + key);
        return ChatColor.translateAlternateColorCodes('&', raw);
    }
}
