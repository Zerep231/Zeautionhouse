package me.zerep.auctionhouse.session;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * P0.3 Fix – Central in-memory store for create-listing sessions.
 * Replaces the three metadata keys (ah_create_item, ah_create_price, ah_create_currency).
 */
public class SessionManager {

    private final Map<UUID, CreateSession> sessions = new ConcurrentHashMap<>();

    /**
     * Start a new session for the player.  The real item is captured here –
     * the caller must have already removed the item from the player's inventory / GUI slot.
     */
    public CreateSession start(UUID playerId, ItemStack item, String defaultCurrency, int defaultPrice) {
        CreateSession session = new CreateSession(item, defaultCurrency, defaultPrice);
        sessions.put(playerId, session);
        return session;
    }

    public CreateSession get(UUID playerId) {
        return sessions.get(playerId);
    }

    /**
     * Remove and return the session.  Returns null if no session existed.
     */
    public CreateSession remove(UUID playerId) {
        return sessions.remove(playerId);
    }

    public boolean has(UUID playerId) {
        return sessions.containsKey(playerId);
    }

    /**
     * Return the session item to the player's inventory (or drop it) and clear the session.
     * Safe to call even if no session exists.
     *
     * P0-1 Fix: if the session is flagged as transitioning (player is moving between
     * CREATE_1 and CREATE_2) this is a no-op — the item must NOT be returned during
     * a deliberate GUI switch.
     */
    public void returnItem(Player player) {
        CreateSession session = sessions.get(player.getUniqueId()); // peek first
        if (session == null || session.isConfirmed() || session.isTransitioning()) return;
        sessions.remove(player.getUniqueId());
        ItemStack item = session.getItem();
        if (item == null || item.getType().isAir()) return;
        var leftovers = player.getInventory().addItem(item);
        leftovers.values().forEach(l -> player.getWorld().dropItemNaturally(player.getLocation(), l));
        player.updateInventory();
    }
}
