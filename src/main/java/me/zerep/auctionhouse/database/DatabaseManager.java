package me.zerep.auctionhouse.database;

import me.zerep.auctionhouse.AuctionHousePlugin;

import java.io.File;
import java.sql.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class DatabaseManager {
    private final AuctionHousePlugin plugin;
    private Connection connection;
    private ExecutorService dbExecutor;

    public DatabaseManager(AuctionHousePlugin plugin) {
        this.plugin = plugin;
    }

    public synchronized void initialize() {
        try {
            Class.forName("org.sqlite.JDBC");
            plugin.getDataFolder().mkdirs();
            File dbFile = new File(plugin.getDataFolder(), "auctionhouse.db");
            connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
            try (Statement st = connection.createStatement()) {
                st.execute("PRAGMA journal_mode=WAL");
                st.execute("PRAGMA synchronous=NORMAL");
                st.execute("PRAGMA foreign_keys=ON");
                st.execute("PRAGMA busy_timeout=5000");
            }
            createSchema();
            dbExecutor = Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "AuctionHouse-DB");
                t.setDaemon(true);
                return t;
            });
        } catch (Exception e) {
            throw new RuntimeException("Failed to init database", e);
        }
    }

    private void createSchema() throws SQLException {
        try (Statement st = connection.createStatement()) {
            st.execute("""
                CREATE TABLE IF NOT EXISTS listings (
                  id INTEGER PRIMARY KEY AUTOINCREMENT,
                  seller_uuid TEXT NOT NULL,
                  seller_name TEXT NOT NULL,
                  item TEXT NOT NULL,
                  quantity INTEGER NOT NULL,
                  price INTEGER NOT NULL,
                  currency TEXT NOT NULL,
                  status TEXT NOT NULL DEFAULT 'ACTIVE',
                  created_at INTEGER NOT NULL
                )""");
            st.execute("CREATE INDEX IF NOT EXISTS idx_listings_status ON listings(status)");
            st.execute("CREATE INDEX IF NOT EXISTS idx_listings_seller ON listings(seller_uuid, status)");

            st.execute("""
                CREATE TABLE IF NOT EXISTS deliveries (
                  id INTEGER PRIMARY KEY AUTOINCREMENT,
                  player_uuid TEXT NOT NULL,
                  type TEXT NOT NULL,
                  item TEXT,
                  amount INTEGER NOT NULL DEFAULT 0,
                  currency TEXT,
                  claimed INTEGER NOT NULL DEFAULT 0,
                  created_at INTEGER NOT NULL
                )""");
            st.execute("CREATE INDEX IF NOT EXISTS idx_deliveries_player ON deliveries(player_uuid, claimed)");

            st.execute("""
                CREATE TABLE IF NOT EXISTS transactions (
                  id INTEGER PRIMARY KEY AUTOINCREMENT,
                  listing_id INTEGER NOT NULL,
                  buyer_uuid TEXT NOT NULL,
                  seller_uuid TEXT NOT NULL,
                  price INTEGER NOT NULL,
                  currency TEXT NOT NULL,
                  created_at INTEGER NOT NULL
                )""");

            st.execute("""
                CREATE TABLE IF NOT EXISTS shop_categories (
                  id TEXT PRIMARY KEY,
                  title TEXT NOT NULL,
                  sort_order INTEGER NOT NULL DEFAULT 0
                )""");

            st.execute("""
                CREATE TABLE IF NOT EXISTS shop_items (
                  id TEXT PRIMARY KEY,
                  category_id TEXT NOT NULL,
                  material TEXT NOT NULL,
                  amount INTEGER NOT NULL,
                  price INTEGER NOT NULL,
                  currency_options TEXT NOT NULL
                )""");
        }
    }

    public synchronized Connection getConnection() {
        try {
            if (connection == null || connection.isClosed()) initialize();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return connection;
    }

    public <T> Future<T> submit(java.util.concurrent.Callable<T> task) {
        return dbExecutor.submit(task);
    }

    public Future<?> submit(Runnable task) {
        return dbExecutor.submit(task);
    }

    public synchronized void close() {
        if (dbExecutor != null) dbExecutor.shutdownNow();
        try {
            if (connection != null && !connection.isClosed()) connection.close();
        } catch (SQLException ignored) {}
    }
}
