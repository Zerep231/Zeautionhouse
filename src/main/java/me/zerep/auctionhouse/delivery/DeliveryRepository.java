package me.zerep.auctionhouse.delivery;

import me.zerep.auctionhouse.database.DatabaseManager;
import me.zerep.auctionhouse.util.ItemSerializer;
import org.bukkit.inventory.ItemStack;

import java.sql.*;
import java.util.*;
import java.util.logging.Logger;

public class DeliveryRepository {

    private final DatabaseManager db;
    private final Logger logger;

    public DeliveryRepository(DatabaseManager db, Logger logger) {
        this.db = db;
        this.logger = logger;
    }

    public synchronized int insert(UUID playerUuid, Delivery.Type type,
                                   ItemStack item, int amount, String currency) {
        try (PreparedStatement ps = db.getConnection().prepareStatement(
                "INSERT INTO deliveries (player_uuid,type,item,amount,currency,claimed,created_at) VALUES (?,?,?,?,?,0,?)",
                Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, playerUuid.toString());
            ps.setString(2, type.name());
            ps.setString(3, item != null ? ItemSerializer.serialize(item) : null);
            ps.setInt(4, amount);
            ps.setString(5, currency);
            ps.setLong(6, System.currentTimeMillis());
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (SQLException e) {
            logger.severe("insert delivery: " + e.getMessage());
        }
        return -1;
    }

    /** Insert inside an existing transaction connection (used by TransactionManager). */
    public void insertInTx(Connection conn, UUID playerUuid, Delivery.Type type,
                           ItemStack item, int amount, String currency) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO deliveries (player_uuid,type,item,amount,currency,claimed,created_at) VALUES (?,?,?,?,?,0,?)")) {
            ps.setString(1, playerUuid.toString());
            ps.setString(2, type.name());
            ps.setString(3, item != null ? ItemSerializer.serialize(item) : null);
            ps.setInt(4, amount);
            ps.setString(5, currency);
            ps.setLong(6, System.currentTimeMillis());
            ps.executeUpdate();
        }
    }

    public synchronized List<Delivery> getUnclaimed(UUID playerUuid) {
        List<Delivery> result = new ArrayList<>();
        try (PreparedStatement ps = db.getConnection().prepareStatement(
                "SELECT * FROM deliveries WHERE player_uuid=? AND claimed=0 ORDER BY created_at ASC")) {
            ps.setString(1, playerUuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) result.add(map(rs));
            }
        } catch (SQLException e) {
            logger.severe("getUnclaimed: " + e.getMessage());
        }
        return result;
    }

    public synchronized int countUnclaimed(UUID playerUuid) {
        try (PreparedStatement ps = db.getConnection().prepareStatement(
                "SELECT COUNT(*) FROM deliveries WHERE player_uuid=? AND claimed=0")) {
            ps.setString(1, playerUuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (SQLException e) {
            logger.severe("countUnclaimed: " + e.getMessage());
        }
        return 0;
    }

    /** Single claim – deletes the row. */
    public synchronized boolean claim(int id) {
        try (PreparedStatement ps = db.getConnection().prepareStatement(
                "DELETE FROM deliveries WHERE id=?")) {
            ps.setInt(1, id);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            logger.severe("claim delivery: " + e.getMessage());
        }
        return false;
    }

    /**
     * P1.2 Fix – Atomic claim-all: deletes every unclaimed row in one statement.
     * Returns the deleted rows to hand items/currency back on the main thread.
     */
    public synchronized List<Delivery> claimAllAtomic(UUID playerUuid) {
        List<Delivery> unclaimed = getUnclaimed(playerUuid);
        if (unclaimed.isEmpty()) return List.of();
        // Build id list for DELETE IN (...)
        StringJoiner ids = new StringJoiner(",");
        unclaimed.forEach(d -> ids.add(String.valueOf(d.id())));
        try (Statement st = db.getConnection().createStatement()) {
            st.execute("DELETE FROM deliveries WHERE id IN (" + ids + ")");
        } catch (SQLException e) {
            logger.severe("claimAll delete: " + e.getMessage());
            return List.of();
        }
        return unclaimed;
    }

    private Delivery map(ResultSet rs) throws SQLException {
        String raw = rs.getString("item");
        ItemStack item = raw == null ? null : ItemSerializer.deserialize(raw);
        return new Delivery(
                rs.getInt("id"),
                UUID.fromString(rs.getString("player_uuid")),
                Delivery.Type.valueOf(rs.getString("type")),
                item,
                rs.getInt("amount"),
                rs.getString("currency"),
                rs.getLong("created_at")
        );
    }
}
