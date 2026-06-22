package me.zerep.auctionhouse.listing;

import me.zerep.auctionhouse.database.DatabaseManager;
import me.zerep.auctionhouse.util.ItemSerializer;
import org.bukkit.inventory.ItemStack;

import java.sql.*;
import java.util.*;
import java.util.logging.Logger;

public class ListingRepository {
    private final DatabaseManager db;
    private final Logger logger;

    public ListingRepository(DatabaseManager db, Logger logger) {
        this.db = db;
        this.logger = logger;
    }

    public synchronized int insert(UUID sellerUuid, String sellerName, ItemStack item, int quantity, int price, String currency) {
        String sql = "INSERT INTO listings (seller_uuid,seller_name,item,quantity,price,currency,status,created_at) VALUES (?,?,?,?,? ,?,'ACTIVE',?)";
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, sellerUuid.toString());
            ps.setString(2, sellerName);
            ps.setString(3, ItemSerializer.serialize(item));
            ps.setInt(4, quantity);
            ps.setInt(5, price);
            ps.setString(6, currency);
            ps.setLong(7, System.currentTimeMillis());
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (SQLException e) {
            logger.severe("insert listing: " + e.getMessage());
        }
        return -1;
    }

    public synchronized Listing getById(int id) {
        try (PreparedStatement ps = db.getConnection().prepareStatement("SELECT * FROM listings WHERE id=?")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return map(rs);
            }
        } catch (SQLException e) {
            logger.severe("getById: " + e.getMessage());
        }
        return null;
    }

    public synchronized List<Listing> getActive(int offset, int limit) {
        List<Listing> result = new ArrayList<>();
        try (PreparedStatement ps = db.getConnection().prepareStatement("SELECT * FROM listings WHERE status='ACTIVE' ORDER BY created_at DESC LIMIT ? OFFSET ?")) {
            ps.setInt(1, limit);
            ps.setInt(2, offset);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) result.add(map(rs));
            }
        } catch (SQLException e) {
            logger.severe("getActive: " + e.getMessage());
        }
        return result;
    }

    public synchronized List<Listing> getByPlayer(UUID player, int offset, int limit) {
        List<Listing> result = new ArrayList<>();
        try (PreparedStatement ps = db.getConnection().prepareStatement("SELECT * FROM listings WHERE seller_uuid=? ORDER BY created_at DESC LIMIT ? OFFSET ?")) {
            ps.setString(1, player.toString());
            ps.setInt(2, limit);
            ps.setInt(3, offset);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) result.add(map(rs));
            }
        } catch (SQLException e) {
            logger.severe("getByPlayer: " + e.getMessage());
        }
        return result;
    }

    public synchronized int countActive(UUID seller) {
        try (PreparedStatement ps = db.getConnection().prepareStatement("SELECT COUNT(*) FROM listings WHERE seller_uuid=? AND status='ACTIVE'")) {
            ps.setString(1, seller.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (SQLException e) {
            logger.severe("countActive: " + e.getMessage());
        }
        return 0;
    }

    public synchronized int countActive() {
        try (PreparedStatement ps = db.getConnection().prepareStatement("SELECT COUNT(*) FROM listings WHERE status='ACTIVE'")) {
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (SQLException e) {
            logger.severe("countActive all: " + e.getMessage());
        }
        return 0;
    }

    public synchronized boolean updateStatus(int id, Listing.Status status) {
        try (PreparedStatement ps = db.getConnection().prepareStatement("UPDATE listings SET status=? WHERE id=?")) {
            ps.setString(1, status.name());
            ps.setInt(2, id);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            logger.severe("updateStatus: " + e.getMessage());
        }
        return false;
    }

    public synchronized List<Listing> getExpired(int expireHours) {
        if (expireHours <= 0) return List.of();
        long cutoff = System.currentTimeMillis() - expireHours * 3600_000L;
        List<Listing> result = new ArrayList<>();
        try (PreparedStatement ps = db.getConnection().prepareStatement("SELECT * FROM listings WHERE status='ACTIVE' AND created_at < ?")) {
            ps.setLong(1, cutoff);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) result.add(map(rs));
            }
        } catch (SQLException e) {
            logger.severe("getExpired: " + e.getMessage());
        }
        return result;
    }

    public synchronized boolean delete(int id) {
        try (PreparedStatement ps = db.getConnection().prepareStatement("DELETE FROM listings WHERE id=?")) {
            ps.setInt(1, id);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            logger.severe("delete listing: " + e.getMessage());
        }
        return false;
    }

    private Listing map(ResultSet rs) throws SQLException {
        return new Listing(
                rs.getInt("id"),
                UUID.fromString(rs.getString("seller_uuid")),
                rs.getString("seller_name"),
                ItemSerializer.deserialize(rs.getString("item")),
                rs.getInt("quantity"),
                rs.getInt("price"),
                rs.getString("currency"),
                Listing.Status.valueOf(rs.getString("status")),
                rs.getLong("created_at")
        );
    }
}
