package me.zerep.auctionhouse.transaction;

import me.zerep.auctionhouse.database.DatabaseManager;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.logging.Logger;

/**
 * P0.2 Fix – Wraps SQLite operations in BEGIN IMMEDIATE / COMMIT / ROLLBACK
 * so purchase, cancel, and expire actions are fully atomic.
 */
public class TransactionManager {

    private final DatabaseManager db;
    private final Logger logger;

    public TransactionManager(DatabaseManager db, Logger logger) {
        this.db = db;
        this.logger = logger;
    }

    @FunctionalInterface
    public interface TransactionWork<T> {
        T execute(Connection conn) throws SQLException;
    }

    /**
     * Executes work inside a single BEGIN IMMEDIATE … COMMIT block.
     * Rolls back automatically on any exception and rethrows as RuntimeException.
     */
    public <T> T execute(TransactionWork<T> work) {
        Connection conn = db.getConnection();
        try {
            conn.setAutoCommit(false);
            try (var st = conn.createStatement()) {
                st.execute("BEGIN IMMEDIATE");
            }
            T result = work.execute(conn);
            conn.commit();
            return result;
        } catch (Exception e) {
            try { conn.rollback(); } catch (SQLException ignored) {}
            throw new RuntimeException("Transaction failed: " + e.getMessage(), e);
        } finally {
            try { conn.setAutoCommit(true); } catch (SQLException ignored) {}
        }
    }
}
