package dev.zerep.zeah.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import dev.zerep.zeah.models.Delivery;
import dev.zerep.zeah.models.Listing;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SQLiteExecutor implements DatabaseExecutor {

    private final JavaPlugin plugin;
    private HikariDataSource dataSource;
    // Single-writer thread to prevent SQLite lock contention
    private final ExecutorService writer = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "ZeAH-DB-Writer");
        t.setDaemon(true);
        return t;
    });

    public SQLiteExecutor(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void initialize() throws Exception {
        File dbFile = new File(plugin.getDataFolder(), "data.db");
        HikariConfig cfg = new HikariConfig();
        cfg.setPoolName("ZeAH-SQLite");
        cfg.setDriverClassName("org.sqlite.JDBC");
        cfg.setJdbcUrl("jdbc:sqlite:" + dbFile.getAbsolutePath());
        cfg.setMaximumPoolSize(1); // SQLite: 1 connection
        cfg.setConnectionTimeout(30_000);
        cfg.setConnectionInitSql("PRAGMA journal_mode=WAL; PRAGMA synchronous=NORMAL;");
        dataSource = new HikariDataSource(cfg);
        createTables();
    }

    private void createTables() throws SQLException {
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
            s.execute("""
                CREATE TABLE IF NOT EXISTS listings (
                    id         INTEGER PRIMARY KEY AUTOINCREMENT,
                    seller_uuid TEXT NOT NULL,
                    seller_name TEXT NOT NULL,
                    item_data  BLOB NOT NULL,
                    price      REAL NOT NULL,
                    status     TEXT NOT NULL DEFAULT 'ACTIVE',
                    created_at INTEGER NOT NULL,
                    expire_at  INTEGER NOT NULL
                )""");
            s.execute("""
                CREATE TABLE IF NOT EXISTS deliveries (
                    id          INTEGER PRIMARY KEY AUTOINCREMENT,
                    buyer_uuid  TEXT NOT NULL,
                    buyer_name  TEXT NOT NULL,
                    listing_id  INTEGER NOT NULL,
                    item_data   BLOB NOT NULL,
                    status      TEXT NOT NULL DEFAULT 'PENDING',
                    reason      TEXT NOT NULL DEFAULT 'purchase',
                    created_at  INTEGER NOT NULL
                )""");
            s.execute("""
                CREATE TABLE IF NOT EXISTS audit_logs (
                    id         INTEGER PRIMARY KEY AUTOINCREMENT,
                    type       TEXT NOT NULL,
                    actor_uuid TEXT NOT NULL,
                    details    TEXT,
                    created_at INTEGER NOT NULL
                )""");
            // Indexes
            s.execute("CREATE INDEX IF NOT EXISTS idx_listings_status   ON listings(status)");
            s.execute("CREATE INDEX IF NOT EXISTS idx_listings_seller   ON listings(seller_uuid, status)");
            s.execute("CREATE INDEX IF NOT EXISTS idx_listings_expire   ON listings(expire_at, status)");
            s.execute("CREATE INDEX IF NOT EXISTS idx_deliveries_buyer  ON deliveries(buyer_uuid, status)");
        }
    }

    @Override
    public CompletableFuture<Integer> insertListing(UUID sellerUuid, String sellerName,
                                                     byte[] itemData, double price, long expireAt) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection c = dataSource.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO listings(seller_uuid,seller_name,item_data,price,status,created_at,expire_at) VALUES(?,?,?,?,'ACTIVE',?,?)",
                     Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, sellerUuid.toString());
                ps.setString(2, sellerName);
                ps.setBytes(3, itemData);
                ps.setDouble(4, price);
                ps.setLong(5, Instant.now().getEpochSecond());
                ps.setLong(6, expireAt);
                ps.executeUpdate();
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    return rs.next() ? rs.getInt(1) : -1;
                }
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }, writer);
    }

    @Override
    public CompletableFuture<List<Listing>> getActiveListings() {
        return CompletableFuture.supplyAsync(() -> {
            List<Listing> list = new ArrayList<>();
            try (Connection c = dataSource.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                     "SELECT * FROM listings WHERE status='ACTIVE' ORDER BY created_at DESC")) {
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) list.add(rowToListing(rs));
                }
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
            return list;
        }, writer);
    }

    @Override
    public CompletableFuture<List<Listing>> getListingsBySeller(UUID sellerUuid) {
        return CompletableFuture.supplyAsync(() -> {
            List<Listing> list = new ArrayList<>();
            try (Connection c = dataSource.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                     "SELECT * FROM listings WHERE seller_uuid=? AND status='ACTIVE' ORDER BY created_at DESC")) {
                ps.setString(1, sellerUuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) list.add(rowToListing(rs));
                }
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
            return list;
        }, writer);
    }

    @Override
    public CompletableFuture<Listing> getListingById(int id) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection c = dataSource.getConnection();
                 PreparedStatement ps = c.prepareStatement("SELECT * FROM listings WHERE id=?")) {
                ps.setInt(1, id);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? rowToListing(rs) : null;
                }
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }, writer);
    }

    @Override
    public CompletableFuture<Boolean> updateListingStatus(int id, Listing.Status status) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection c = dataSource.getConnection();
                 PreparedStatement ps = c.prepareStatement("UPDATE listings SET status=? WHERE id=?")) {
                ps.setString(1, status.name());
                ps.setInt(2, id);
                return ps.executeUpdate() > 0;
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }, writer);
    }

    @Override
    public CompletableFuture<Integer> insertDelivery(UUID buyerUuid, String buyerName,
                                                      int listingId, byte[] itemData, String reason) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection c = dataSource.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO deliveries(buyer_uuid,buyer_name,listing_id,item_data,status,reason,created_at) VALUES(?,?,?,?,'PENDING',?,?)",
                     Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, buyerUuid.toString());
                ps.setString(2, buyerName);
                ps.setInt(3, listingId);
                ps.setBytes(4, itemData);
                ps.setString(5, reason);
                ps.setLong(6, Instant.now().getEpochSecond());
                ps.executeUpdate();
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    return rs.next() ? rs.getInt(1) : -1;
                }
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }, writer);
    }

    @Override
    public CompletableFuture<List<Delivery>> getPendingDeliveries(UUID buyerUuid) {
        return CompletableFuture.supplyAsync(() -> {
            List<Delivery> list = new ArrayList<>();
            try (Connection c = dataSource.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                     "SELECT * FROM deliveries WHERE buyer_uuid=? AND status='PENDING' ORDER BY created_at ASC")) {
                ps.setString(1, buyerUuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) list.add(rowToDelivery(rs));
                }
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
            return list;
        }, writer);
    }

    @Override
    public CompletableFuture<Boolean> claimDelivery(int deliveryId) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection c = dataSource.getConnection()) {
                c.setAutoCommit(false);
                try {
                    // Lock row via status transition PENDING -> CLAIMING
                    PreparedStatement lock = c.prepareStatement(
                        "UPDATE deliveries SET status='CLAIMING' WHERE id=? AND status='PENDING'");
                    lock.setInt(1, deliveryId);
                    int rows = lock.executeUpdate();
                    if (rows == 0) { c.rollback(); return false; }
                    // Mark CLAIMED
                    PreparedStatement claim = c.prepareStatement(
                        "UPDATE deliveries SET status='CLAIMED' WHERE id=?");
                    claim.setInt(1, deliveryId);
                    claim.executeUpdate();
                    c.commit();
                    return true;
                } catch (Exception e) {
                    c.rollback();
                    throw e;
                } finally {
                    c.setAutoCommit(true);
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }, writer);
    }

    @Override
    public CompletableFuture<Void> undoClaim(int deliveryId) {
        return CompletableFuture.runAsync(() -> {
            try (Connection c = dataSource.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                     "UPDATE deliveries SET status='PENDING' WHERE id=?")) {
                ps.setInt(1, deliveryId);
                ps.executeUpdate();
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }, writer);
    }

    @Override
    public CompletableFuture<Integer> countPendingDeliveries(UUID buyerUuid) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection c = dataSource.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                     "SELECT COUNT(*) FROM deliveries WHERE buyer_uuid=? AND status='PENDING'")) {
                ps.setString(1, buyerUuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? rs.getInt(1) : 0;
                }
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }, writer);
    }

    @Override
    public CompletableFuture<Boolean> executePurchase(int listingId,
        UUID buyerUuid, String buyerName, byte[] buyerItemData,
        UUID sellerUuid, String sellerName, byte[][] sellerCurrencyData) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection c = dataSource.getConnection()) {
                c.setAutoCommit(false);
                try {
                    // Verify listing is still ACTIVE
                    PreparedStatement check = c.prepareStatement(
                        "SELECT id FROM listings WHERE id=? AND status='ACTIVE'");
                    check.setInt(1, listingId);
                    ResultSet rs = check.executeQuery();
                    if (!rs.next()) { c.rollback(); return false; }

                    // Mark listing SOLD
                    PreparedStatement sell = c.prepareStatement(
                        "UPDATE listings SET status='SOLD' WHERE id=? AND status='ACTIVE'");
                    sell.setInt(1, listingId);
                    if (sell.executeUpdate() == 0) { c.rollback(); return false; }

                    // Create delivery for buyer
                    PreparedStatement delivery = c.prepareStatement(
                        "INSERT INTO deliveries(buyer_uuid,buyer_name,listing_id,item_data,status,reason,created_at) VALUES(?,?,?,?,'PENDING','purchase',?)");
                    delivery.setString(1, buyerUuid.toString());
                    delivery.setString(2, buyerName);
                    delivery.setInt(3, listingId);
                    delivery.setBytes(4, buyerItemData);
                    delivery.setLong(5, Instant.now().getEpochSecond());
                    delivery.executeUpdate();

                    // Seller currency deliveries — in same transaction (atomic, crash-safe)
                    long sellerNow = Instant.now().getEpochSecond();
                    PreparedStatement selDel = c.prepareStatement(
                        "INSERT INTO deliveries(buyer_uuid,buyer_name,listing_id,item_data,status,reason,created_at) VALUES(?,?,?,?,'PENDING','sale_payment',?)");
                    for (byte[] currencyStack : sellerCurrencyData) {
                        selDel.setString(1, sellerUuid.toString());
                        selDel.setString(2, sellerName);
                        selDel.setInt(3, listingId);
                        selDel.setBytes(4, currencyStack);
                        selDel.setLong(5, sellerNow);
                        selDel.addBatch();
                    }
                    selDel.executeBatch();
                    c.commit();
                    return true;
                } catch (Exception e) {
                    c.rollback();
                    throw e;
                } finally {
                    c.setAutoCommit(true);
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }, writer);
    }

    @Override
    public CompletableFuture<Boolean> executeCancel(int listingId,
                                                    UUID sellerUuid, String sellerName,
                                                    byte[] itemData) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection c = dataSource.getConnection()) {
                c.setAutoCommit(false);
                try {
                    PreparedStatement cancel = c.prepareStatement(
                        "UPDATE listings SET status='CANCELLED' WHERE id=? AND status='ACTIVE' AND seller_uuid=?");
                    cancel.setInt(1, listingId);
                    cancel.setString(2, sellerUuid.toString());
                    if (cancel.executeUpdate() == 0) { c.rollback(); return false; }

                    PreparedStatement delivery = c.prepareStatement(
                        "INSERT INTO deliveries(buyer_uuid,buyer_name,listing_id,item_data,status,reason,created_at) VALUES(?,?,?,?,'PENDING','cancelled',?)");
                    delivery.setString(1, sellerUuid.toString());
                    delivery.setString(2, sellerName);
                    delivery.setInt(3, listingId);
                    delivery.setBytes(4, itemData);
                    delivery.setLong(5, Instant.now().getEpochSecond());
                    delivery.executeUpdate();

                    c.commit();
                    return true;
                } catch (Exception e) {
                    c.rollback();
                    throw e;
                } finally {
                    c.setAutoCommit(true);
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }, writer);
    }

    @Override
    public CompletableFuture<Integer> expireOldListings() {
        return CompletableFuture.supplyAsync(() -> {
            int count = 0;
            try (Connection c = dataSource.getConnection()) {
                c.setAutoCommit(false);
                try {
                    long now = Instant.now().getEpochSecond();
                    PreparedStatement expired = c.prepareStatement(
                        "SELECT id, seller_uuid, seller_name, item_data FROM listings WHERE status='ACTIVE' AND expire_at <= ?");
                    expired.setLong(1, now);
                    List<Object[]> rows = new ArrayList<>();
                    try (ResultSet rs = expired.executeQuery()) {
                        while (rs.next()) {
                            rows.add(new Object[]{
                                rs.getInt("id"),
                                rs.getString("seller_uuid"),
                                rs.getString("seller_name"),
                                rs.getBytes("item_data")
                            });
                        }
                    }
                    for (Object[] row : rows) {
                        int id = (int) row[0];
                        PreparedStatement mark = c.prepareStatement(
                            "UPDATE listings SET status='EXPIRED' WHERE id=?");
                        mark.setInt(1, id);
                        mark.executeUpdate();

                        PreparedStatement delivery = c.prepareStatement(
                            "INSERT INTO deliveries(buyer_uuid,buyer_name,listing_id,item_data,status,reason,created_at) VALUES(?,?,?,?,'PENDING','expired',?)");
                        delivery.setString(1, (String) row[1]);
                        delivery.setString(2, (String) row[2]);
                        delivery.setInt(3, id);
                        delivery.setBytes(4, (byte[]) row[3]);
                        delivery.setLong(5, now);
                        delivery.executeUpdate();
                        count++;
                    }
                    c.commit();
                } catch (Exception e) {
                    c.rollback();
                    throw e;
                } finally {
                    c.setAutoCommit(true);
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            return count;
        }, writer);
    }

    @Override
    public CompletableFuture<Void> insertAuditLog(String type, UUID actor, String details) {
        return CompletableFuture.runAsync(() -> {
            try (Connection c = dataSource.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO audit_logs(type,actor_uuid,details,created_at) VALUES(?,?,?,?)")) {
                ps.setString(1, type);
                ps.setString(2, actor.toString());
                ps.setString(3, details);
                ps.setLong(4, Instant.now().getEpochSecond());
                ps.executeUpdate();
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }, writer);
    }

    private Listing rowToListing(ResultSet rs) throws SQLException {
        return new Listing(
            rs.getInt("id"),
            UUID.fromString(rs.getString("seller_uuid")),
            rs.getString("seller_name"),
            rs.getBytes("item_data"),
            rs.getDouble("price"),
            Listing.Status.valueOf(rs.getString("status")),
            rs.getLong("created_at"),
            rs.getLong("expire_at")
        );
    }

    private Delivery rowToDelivery(ResultSet rs) throws SQLException {
        return new Delivery(
            rs.getInt("id"),
            UUID.fromString(rs.getString("buyer_uuid")),
            rs.getString("buyer_name"),
            rs.getInt("listing_id"),
            rs.getBytes("item_data"),
            Delivery.Status.valueOf(rs.getString("status")),
            rs.getLong("created_at"),
            rs.getString("reason")
        );
    }

    @Override
    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    @Override
    public void close() {
        writer.shutdown();
        if (dataSource != null) dataSource.close();
    }
}
