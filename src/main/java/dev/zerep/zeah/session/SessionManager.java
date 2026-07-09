package dev.zerep.zeah.session;

import dev.zerep.zeah.ZeAuctionHouse;
import dev.zerep.zeah.utils.ItemSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.sql.*;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class SessionManager {

    private final ZeAuctionHouse plugin;
    private final Map<UUID, CreateSession> sessions = new ConcurrentHashMap<>();
    private BukkitTask timeoutTask;
    private final long timeoutMs;
    private final boolean mysql;

    public SessionManager(ZeAuctionHouse plugin) {
        this.plugin = plugin;
        this.timeoutMs = plugin.getConfig().getInt("session.timeout-minutes", 5) * 60_000L;
        this.mysql = "mysql".equalsIgnoreCase(plugin.getConfig().getString("database.type", "sqlite"));
        // Run DB init async — do not block the main thread on startup
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            createDraftTable();
            loadDraftListings();
        });
    }

    // ── Draft persistence ──────────────────────────────────────────────────

    private void createDraftTable() {
        String sql = mysql
            ? "CREATE TABLE IF NOT EXISTS draft_listings (" +
              "player_uuid VARCHAR(36) PRIMARY KEY," +
              "item_data MEDIUMBLOB NOT NULL," +
              "price DOUBLE NOT NULL," +
              "created_at BIGINT NOT NULL" +
              ") ENGINE=InnoDB"
            : "CREATE TABLE IF NOT EXISTS draft_listings (" +
              "player_uuid VARCHAR(36) PRIMARY KEY," +
              "item_data BLOB NOT NULL," +
              "price REAL NOT NULL," +
              "created_at INTEGER NOT NULL" +
              ")";
        try (Connection c = plugin.getDb().getConnection();
             Statement s = c.createStatement()) {
            s.execute(sql);
        } catch (SQLException e) {
            plugin.getLogger().warning("draft_listings table error: " + e.getMessage());
        }
    }

    private void loadDraftListings() {
        try (Connection c = plugin.getDb().getConnection();
             Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("SELECT * FROM draft_listings")) {
            while (rs.next()) {
                UUID uuid = UUID.fromString(rs.getString("player_uuid"));
                byte[] data = rs.getBytes("item_data");
                double price = rs.getDouble("price");
                ItemStack item = ItemSerializer.deserialize(data);
                if (item == null) continue;
                CreateSession session = new CreateSession(uuid, item);
                if (price > 0) session.setPrice(price);
                sessions.put(uuid, session);
            }
            if (!sessions.isEmpty())
                plugin.getLogger().info("Restored " + sessions.size() + " draft session(s) from DB.");
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to load draft listings: " + e.getMessage());
        }
    }

    /** Async draft save — never blocks main thread. */
    private void saveDraft(UUID uuid, CreateSession session) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            String sql = mysql
                ? "INSERT INTO draft_listings (player_uuid,item_data,price,created_at) VALUES (?,?,?,?) " +
                  "ON DUPLICATE KEY UPDATE item_data=VALUES(item_data),price=VALUES(price)"
                : "INSERT OR REPLACE INTO draft_listings (player_uuid,item_data,price,created_at) VALUES (?,?,?,?)";
            try (Connection c = plugin.getDb().getConnection();
                 PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setString(1, uuid.toString());
                ps.setBytes(2, ItemSerializer.serialize(session.getItem()));
                ps.setDouble(3, session.getPrice());
                ps.setLong(4, session.getCreatedAt());
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().warning("Failed to save draft: " + e.getMessage());
            }
        });
    }

    /** Async draft delete — never blocks main thread. */
    private void deleteDraft(UUID uuid) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection c = plugin.getDb().getConnection();
                 PreparedStatement ps = c.prepareStatement(
                     "DELETE FROM draft_listings WHERE player_uuid = ?")) {
                ps.setString(1, uuid.toString());
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().warning("Failed to delete draft: " + e.getMessage());
            }
        });
    }

    private void saveAll() {
        sessions.forEach(this::saveDraft);
    }

    // ── Session lifecycle ──────────────────────────────────────────────────

    public void startTimeoutTask() {
        timeoutTask = Bukkit.getScheduler().runTaskTimer(plugin, this::checkTimeouts, 20L * 30, 20L * 30);
    }

    private void checkTimeouts() {
        sessions.forEach((uuid, session) -> {
            if (session.isTimedOut(timeoutMs)
                    && session.getState() != CreateSession.State.COMPLETED
                    && session.getState() != CreateSession.State.ABORTED) {
                abortSession(uuid, true);
            }
        });
    }

    public boolean createSession(Player player, ItemStack item) {
        if (sessions.containsKey(player.getUniqueId())) return false;
        CreateSession session = new CreateSession(player.getUniqueId(), item);
        sessions.put(player.getUniqueId(), session);
        saveDraft(player.getUniqueId(), session);
        return true;
    }

    public Optional<CreateSession> getSession(UUID playerUuid) {
        return Optional.ofNullable(sessions.get(playerUuid));
    }

    public boolean hasSession(UUID playerUuid) {
        return sessions.containsKey(playerUuid);
    }

    public void abortSession(UUID playerUuid, boolean returnItem) {
        CreateSession session = sessions.remove(playerUuid);
        if (session == null) return;
        session.abort();
        deleteDraft(playerUuid);
        if (returnItem) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                Player player = Bukkit.getPlayer(playerUuid);
                if (player != null && player.isOnline()) {
                    player.getInventory().addItem(session.getItem());
                    player.sendMessage(plugin.getLang().format("auction.session-timeout"));
                } else {
                    plugin.getDb().insertDelivery(playerUuid, "unknown", -1,
                        ItemSerializer.serialize(session.getItem()), "session-abort");
                }
            });
        }
    }

    public void removeSession(UUID playerUuid) {
        sessions.remove(playerUuid);
        deleteDraft(playerUuid);
    }

    public void onPlayerQuit(UUID playerUuid) {
        if (sessions.containsKey(playerUuid)) abortSession(playerUuid, true);
    }

    public void shutdown() {
        if (timeoutTask != null) timeoutTask.cancel();
        saveAll();
    }
}
