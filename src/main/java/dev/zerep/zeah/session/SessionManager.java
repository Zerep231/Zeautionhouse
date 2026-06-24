package dev.zerep.zeah.session;

import dev.zerep.zeah.ZeAuctionHouse;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class SessionManager {

    private final ZeAuctionHouse plugin;
    private final Map<UUID, CreateSession> sessions = new ConcurrentHashMap<>();
    private BukkitTask timeoutTask;
    private final long timeoutMs;

    public SessionManager(ZeAuctionHouse plugin) {
        this.plugin = plugin;
        this.timeoutMs = plugin.getConfig().getInt("session.timeout-minutes", 5) * 60_000L;
    }

    public void startTimeoutTask() {
        timeoutTask = Bukkit.getScheduler().runTaskTimer(plugin, this::checkTimeouts, 20L * 30, 20L * 30);
    }

    private void checkTimeouts() {
        sessions.forEach((uuid, session) -> {
            if (session.isTimedOut(timeoutMs) && session.getState() != CreateSession.State.COMPLETED
                    && session.getState() != CreateSession.State.ABORTED) {
                abortSession(uuid, true);
            }
        });
    }

    /** Create a new session. Returns false if player already has one. */
    public boolean createSession(Player player, ItemStack item) {
        if (sessions.containsKey(player.getUniqueId())) return false;
        sessions.put(player.getUniqueId(), new CreateSession(player.getUniqueId(), item));
        return true;
    }

    public Optional<CreateSession> getSession(UUID playerUuid) {
        return Optional.ofNullable(sessions.get(playerUuid));
    }

    public boolean hasSession(UUID playerUuid) {
        return sessions.containsKey(playerUuid);
    }

    /** Abort session and return item to player. */
    public void abortSession(UUID playerUuid, boolean returnItem) {
        CreateSession session = sessions.remove(playerUuid);
        if (session == null) return;
        session.abort();
        if (returnItem) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                Player player = Bukkit.getPlayer(playerUuid);
                if (player != null && player.isOnline()) {
                    player.getInventory().addItem(session.getItem());
                    player.sendMessage(plugin.getLang().format("auction.session-timeout"));
                } else {
                    // Store as delivery so item isn't lost
                    plugin.getDb().insertDelivery(
                        playerUuid, "unknown", -1,
                        dev.zerep.zeah.utils.ItemSerializer.serialize(session.getItem()),
                        "session-abort"
                    );
                }
            });
        }
    }

    public void removeSession(UUID playerUuid) {
        sessions.remove(playerUuid);
    }

    /** Called on PlayerQuitEvent to cleanly abort sessions. */
    public void onPlayerQuit(UUID playerUuid) {
        if (sessions.containsKey(playerUuid)) {
            abortSession(playerUuid, true);
        }
    }

    public void shutdown() {
        if (timeoutTask != null) timeoutTask.cancel();
        // Abort all pending sessions on shutdown
        sessions.keySet().forEach(uuid -> abortSession(uuid, true));
    }
}
