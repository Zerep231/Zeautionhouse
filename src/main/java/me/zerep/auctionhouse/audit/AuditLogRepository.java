package me.zerep.auctionhouse.audit;

import me.zerep.auctionhouse.database.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * P1.3 Fix – Persists every purchase / cancel / expire to audit_log so admins
 * can investigate dupe reports, missing items, or suspicious transactions.
 */
public class AuditLogRepository {

    private final DatabaseManager db;
    private final Logger logger;

    public AuditLogRepository(DatabaseManager db, Logger logger) {
        this.db = db;
        this.logger = logger;
    }

    public enum Action { PURCHASE, CANCEL, EXPIRE }

    /**
     * Writes one audit record.  Must be called inside an existing transaction
     * (TransactionManager.execute) so the log entry is committed together with
     * the listing status change.
     */
    public void log(Connection conn, Action action, int listingId,
                    UUID buyerUuid, UUID sellerUuid,
                    String itemDesc, int amount, String currency) {
        String sql = """
            INSERT INTO audit_log
              (action, listing_id, buyer_uuid, seller_uuid, item_desc, amount, currency, created_at)
            VALUES (?,?,?,?,?,?,?,?)
            """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, action.name());
            ps.setInt(2, listingId);
            ps.setString(3, buyerUuid != null ? buyerUuid.toString() : null);
            ps.setString(4, sellerUuid != null ? sellerUuid.toString() : null);
            ps.setString(5, itemDesc);
            ps.setInt(6, amount);
            ps.setString(7, currency);
            ps.setLong(8, System.currentTimeMillis());
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.severe("audit_log insert failed: " + e.getMessage());
        }
    }
}
