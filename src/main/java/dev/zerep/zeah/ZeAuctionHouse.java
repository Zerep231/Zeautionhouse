package dev.zerep.zeah;

import dev.zerep.zeah.commands.AHCommand;
import dev.zerep.zeah.database.DatabaseExecutor;
import dev.zerep.zeah.database.MySQLExecutor;
import dev.zerep.zeah.database.SQLiteExecutor;
import dev.zerep.zeah.economy.VanillaEconomy;
import dev.zerep.zeah.lang.LangManager;
import dev.zerep.zeah.listeners.GUIListener;
import dev.zerep.zeah.listeners.PlayerListener;
import dev.zerep.zeah.managers.AuctionManager;
import dev.zerep.zeah.managers.AuditLogger;
import dev.zerep.zeah.managers.CacheManager;
import dev.zerep.zeah.managers.DeliveryManager;
import dev.zerep.zeah.session.SessionManager;
import dev.zerep.zeah.shop.ShopManager;
import org.bukkit.plugin.java.JavaPlugin;

public final class ZeAuctionHouse extends JavaPlugin {

    private static ZeAuctionHouse instance;

    private DatabaseExecutor db;
    private LangManager lang;
    private VanillaEconomy economy;
    private CacheManager cacheManager;
    private SessionManager sessionManager;
    private AuctionManager auctionManager;
    private DeliveryManager deliveryManager;
    private ShopManager shopManager;
    private AuditLogger auditLogger;
    private GUIListener guiListener;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        lang = new LangManager(this);
        lang.load(getConfig().getString("lang", "en"));

        economy = new VanillaEconomy(this);

        try {
            String type = getConfig().getString("database.type", "sqlite");
            if ("mysql".equalsIgnoreCase(type)) {
                db = new MySQLExecutor(this, getConfig().getInt("database.pool-size", 10));
            } else {
                db = new SQLiteExecutor(this);
            }
            db.initialize();
        } catch (Exception e) {
            getLogger().severe("Failed to initialize database: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        auditLogger = new AuditLogger(this);
        cacheManager = new CacheManager(this);
        sessionManager = new SessionManager(this);
        auctionManager = new AuctionManager(this);
        deliveryManager = new DeliveryManager(this);
        shopManager = new ShopManager(this);
        shopManager.load();

        sessionManager.startTimeoutTask();
        auctionManager.startExpireTask();

        guiListener = new GUIListener(this);
        getServer().getPluginManager().registerEvents(guiListener, this);
        getServer().getPluginManager().registerEvents(new PlayerListener(this), this);

        AHCommand ahCmd = new AHCommand(this);
        var cmd = getCommand("ah");
        if (cmd != null) { cmd.setExecutor(ahCmd); cmd.setTabCompleter(ahCmd); }

        getLogger().info("ZeAuctionHouse v" + getDescription().getVersion() + " enabled! "
            + "Currency: " + economy.getCurrencyName());
    }

    @Override
    public void onDisable() {
        if (sessionManager != null) sessionManager.shutdown();
        if (auctionManager != null) auctionManager.shutdown();
        if (db != null) db.close();
        getLogger().info("ZeAuctionHouse disabled.");
    }

    public void reload() {
        reloadConfig();
        lang.load(getConfig().getString("lang", "en"));
        economy = new VanillaEconomy(this);
        shopManager.load();
        cacheManager.invalidate();
    }

    public static ZeAuctionHouse getInstance() { return instance; }
    public DatabaseExecutor getDb() { return db; }
    public LangManager getLang() { return lang; }
    public VanillaEconomy getEconomy() { return economy; }
    public CacheManager getCacheManager() { return cacheManager; }
    public SessionManager getSessionManager() { return sessionManager; }
    public AuctionManager getAuctionManager() { return auctionManager; }
    public DeliveryManager getDeliveryManager() { return deliveryManager; }
    public ShopManager getShopManager() { return shopManager; }
    public AuditLogger getAuditLogger() { return auditLogger; }
    public GUIListener getGuiListener() { return guiListener; }
}
