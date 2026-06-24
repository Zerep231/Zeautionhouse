package dev.zerep.zeah.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import dev.zerep.zeah.models.Delivery;
import dev.zerep.zeah.models.Listing;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * MySQL executor – inherits the same SQL logic but uses a pooled connection.
 * Differences: AUTO_INCREMENT instead of AUTOINCREMENT, proper indexes.
 */
public class MySQLExecutor implements DatabaseExecutor {

    private final JavaPlugin plugin;
    private HikariDataSource dataSource;
    private final ExecutorService pool;

    public MySQLExecutor(JavaPlugin plugin, int poolSize) {
        this.plugin = plugin;
        this.pool = Executors.newFixedThreadPool(Math.max(2, poolSize), r -> {
            Thread t = new Thread(r, "ZeAH-DB-Pool");
            t.setDaemon(true);
            return t;
        });
    }

    @Override
    public void initialize() throws Exception {
        HikariConfig cfg = new HikariConfig();
        cfg.setPoolName("ZeAH-MySQL");
        cfg.setJdbcUrl(String.format("jdbc:mysql://%s:%d/%s?useSSL=false&autoReconnect=true&serverTimezone=UTC",
            plugin.getConfig().getString("database.host", "localhost"),
            plugin.getConfig().getInt("database.port", 3306),
            plugin.getConfig().getString("database.database", "zeauctionhouse")));
        cfg.setUsername(plugin.getConfig().getString("database.user", "root"));
        cfg.setPassword(plugin.getConfig().getString("database.password", ""));
        cfg.setMaximumPoolSize(plugin.getConfig().getInt("database.pool-size", 10));
        cfg.setConnectionTimeout(30_000);
        cfg.setIdleTimeout(600_000);
        cfg.setMaxLifetime(1_800_000);
        dataSource = new HikariDataSource(cfg);
        createTables();
    }

    private void createTables() throws SQLException {
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
            s.execute("""
                CREATE TABLE IF NOT EXISTS listings (
                    id          INT          NOT NULL AUTO_INCREMENT PRIMARY KEY,
                    seller_uuid VARCHAR(36)  NOT NULL,
                    seller_name VARCHAR(16)  NOT NULL,
                    item_data   MEDIUMBLOB   NOT NULL,
                    price       DOUBLE       NOT NULL,
                    status      VARCHAR(12)  NOT NULL DEFAULT 'ACTIVE',
                    created_at  BIGINT       NOT NULL,
                    expire_at   BIGINT       NOT NULL,
                    INDEX idx_seller (seller_uuid, status),
                    INDEX idx_status (status),
                    INDEX idx_expire (expire_at, status)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4""");
            s.execute("""
                CREATE TABLE IF NOT EXISTS deliveries (
                    id          INT          NOT NULL AUTO_INCREMENT PRIMARY KEY,
                    buyer_uuid  VARCHAR(36)  NOT NULL,
                    buyer_name  VARCHAR(16)  NOT NULL,
                    listing_id  INT          NOT NULL,
                    item_data   MEDIUMBLOB   NOT NULL,
                    status      VARCHAR(12)  NOT NULL DEFAULT 'PENDING',
                    reason      VARCHAR(20)  NOT NULL DEFAULT 'purchase',
                    created_at  BIGINT       NOT NULL,
                    INDEX idx_buyer (buyer_uuid, status)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4""");
            s.execute("""
                CREATE TABLE IF NOT EXISTS audit_logs (
                    id          INT          NOT NULL AUTO_INCREMENT PRIMARY KEY,
                    type        VARCHAR(32)  NOT NULL,
                    actor_uuid  VARCHAR(36)  NOT NULL,
                    details     TEXT,
                    created_at  BIGINT       NOT NULL
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4""");
        }
    }

    // Delegate all methods to SQLiteExecutor-equivalent implementations using pool
    // For brevity, the pattern is identical — just using pool instead of writer.

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
            } catch (SQLException e) { throw new RuntimeException(e); }
        }, pool);
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
            } catch (SQLException e) { throw new RuntimeException(e); }
            return list;
        }, pool);
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
            } catch (SQLException e) { throw new RuntimeException(e); }
            return list;
        }, pool);
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
            } catch (SQLException e) { throw new RuntimeException(e); }
        }, pool);
    }

    @Override
    public CompletableFuture<Boolean> updateListingStatus(int id, Listing.Status status) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection c = dataSource.getConnection();
                 PreparedStatement ps = c.prepareStatement("UPDATE listings SET status=? WHERE id=?")) {
                ps.setString(1, status.name());
                ps.setInt(2, id);
                return ps.executeUpdate() > 0;
            } catch (SQLException e) { throw new RuntimeException(e); }
        }, pool);
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
            } catch (SQLException e) { throw new RuntimeException(e); }
        }, pool);
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
            } catch (SQLException e) { throw new RuntimeException(e); }
            return list;
        }, pool);
    }

    @Override
    public CompletableFuture<Boolean> claimDelivery(int deliveryId) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection c = dataSource.getConnection()) {
                c.setAutoCommit(false);
                try {
                    PreparedStatement lock = c.prepareStatement(
                        "UPDATE deliveries SET status='CLAIMING' WHERE id=? AND status='PENDING'");
                    lock.setInt(1, deliveryId);
                    if (lock.executeUpdate() == 0) { c.rollback(); return false; }
                    PreparedStatement claimed = c.prepareStatement(
                        "UPDATE deliveries SET status='CLAIMED' WHERE id=?");
                    claimed.setInt(1, deliveryId);
                    claimed.executeUpdate();
                    c.commit();
                    return true;
                } catch (Exception e) { c.rollback(); throw e; }
                finally { c.setAutoCommit(true); }
            } catch (Exception e) { throw new RuntimeException(e); }
        }, pool);
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
            } catch (SQLException e) { throw new RuntimeException(e); }
        }, pool);
    }

    @Override
    public CompletableFuture<Boolean> executePurchase(int listingId,
        UUID buyerUuid, String buyerName, byte[] buyerItemData,
        UUID sellerUuid, String sellerName, byte[][] sellerCurrencyData) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection c = dataSource.getConnection()) {
                c.setAutoCommit(false);
                try {
                    PreparedStatement sell = c.prepareStatement(
                        "UPDATE listings SET status='SOLD' WHERE id=? AND status='ACTIVE'");
                    sell.setInt(1, listingId);
                    if (sell.executeUpdate() == 0) { c.rollback(); return false; }
                    PreparedStatement del = c.prepareStatement(
                        "INSERT INTO deliveries(buyer_uuid,buyer_name,listing_id,item_data,status,reason,created_at) VALUES(?,?,?,?,'PENDING','purchase',?)");
                    del.setString(1, buyerUuid.toString());
                    del.setString(2, buyerName);
                    del.setInt(3, listingId);
                    del.setBytes(4, buyerItemData);
                    del.setLong(5, Instant.now().getEpochSecond());
                    del.executeUpdate();
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
                } catch (Exception e) { c.rollback(); throw e; }
                finally { c.setAutoCommit(true); }
            } catch (Exception e) { throw new RuntimeException(e); }
        }, pool);
    }

    @Override
    public CompletableFuture<Boolean> executeCancel(int listingId, UUID sellerUuid,
                                                    String sellerName, byte[] itemData) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection c = dataSource.getConnection()) {
                c.setAutoCommit(false);
                try {
                    PreparedStatement cancel = c.prepareStatement(
                        "UPDATE listings SET status='CANCELLED' WHERE id=? AND status='ACTIVE' AND seller_uuid=?");
                    cancel.setInt(1, listingId);
                    cancel.setString(2, sellerUuid.toString());
                    if (cancel.executeUpdate() == 0) { c.rollback(); return false; }
                    PreparedStatement del = c.prepareStatement(
                        "INSERT INTO deliveries(buyer_uuid,buyer_name,listing_id,item_data,status,reason,created_at) VALUES(?,?,?,?,'PENDING','cancelled',?)");
                    del.setString(1, sellerUuid.toString());
                    del.setString(2, sellerName);
                    del.setInt(3, listingId);
                    del.setBytes(4, itemData);
                    del.setLong(5, Instant.now().getEpochSecond());
                    del.executeUpdate();
                    c.commit();
                    return true;
                } catch (Exception e) { c.rollback(); throw e; }
                finally { c.setAutoCommit(true); }
            } catch (Exception e) { throw new RuntimeException(e); }
        }, pool);
    }

    @Override
    public CompletableFuture<Integer> expireOldListings() {
        // Same logic as SQLiteExecutor but using pool
        return CompletableFuture.supplyAsync(() -> {
            int count = 0;
            try (Connection c = dataSource.getConnection()) {
                c.setAutoCommit(false);
                try {
                    long now = Instant.now().getEpochSecond();
                    PreparedStatement sel = c.prepareStatement(
                        "SELECT id,seller_uuid,seller_name,item_data FROM listings WHERE status='ACTIVE' AND expire_at<=?");
                    sel.setLong(1, now);
                    List<Object[]> rows = new ArrayList<>();
                    try (ResultSet rs = sel.executeQuery()) {
                        while (rs.next()) rows.add(new Object[]{
                            rs.getInt("id"), rs.getString("seller_uuid"),
                            rs.getString("seller_name"), rs.getBytes("item_data")
                        });
                    }
                    for (Object[] row : rows) {
                        int id = (int) row[0];
                        PreparedStatement mark = c.prepareStatement("UPDATE listings SET status='EXPIRED' WHERE id=?");
                        mark.setInt(1, id); mark.executeUpdate();
                        PreparedStatement del = c.prepareStatement(
                            "INSERT INTO deliveries(buyer_uuid,buyer_name,listing_id,item_data,status,reason,created_at) VALUES(?,?,?,?,'PENDING','expired',?)");
                        del.setString(1, (String)row[1]); del.setString(2, (String)row[2]);
                        del.setInt(3, id); del.setBytes(4, (byte[])row[3]); del.setLong(5, now);
                        del.executeUpdate(); count++;
                    }
                    c.commit();
                } catch (Exception e) { c.rollback(); throw e; }
                finally { c.setAutoCommit(true); }
            } catch (Exception e) { throw new RuntimeException(e); }
            return count;
        }, pool);
    }

    @Override
    public CompletableFuture<Void> insertAuditLog(String type, UUID actor, String details) {
        return CompletableFuture.runAsync(() -> {
            try (Connection c = dataSource.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO audit_logs(type,actor_uuid,details,created_at) VALUES(?,?,?,?)")) {
                ps.setString(1, type); ps.setString(2, actor.toString());
                ps.setString(3, details); ps.setLong(4, Instant.now().getEpochSecond());
                ps.executeUpdate();
            } catch (SQLException e) { throw new RuntimeException(e); }
        }, pool);
    }

    private Listing rowToListing(ResultSet rs) throws SQLException {
        return new Listing(rs.getInt("id"),
            UUID.fromString(rs.getString("seller_uuid")), rs.getString("seller_name"),
            rs.getBytes("item_data"), rs.getDouble("price"),
            Listing.Status.valueOf(rs.getString("status")),
            rs.getLong("created_at"), rs.getLong("expire_at"));
    }

    private Delivery rowToDelivery(ResultSet rs) throws SQLException {
        return new Delivery(rs.getInt("id"),
            UUID.fromString(rs.getString("buyer_uuid")), rs.getString("buyer_name"),
            rs.getInt("listing_id"), rs.getBytes("item_data"),
            Delivery.Status.valueOf(rs.getString("status")),
            rs.getLong("created_at"), rs.getString("reason"));
    }

    @Override
    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    @Override
    public void close() {
        pool.shutdown();
        if (dataSource != null) dataSource.close();
    }
}
