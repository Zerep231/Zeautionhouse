package dev.zerep.zeah.listeners;

import dev.zerep.zeah.ZeAuctionHouse;
import org.bukkit.event.*;
import org.bukkit.event.player.*;

public class PlayerListener implements Listener {

    private final ZeAuctionHouse plugin;

    public PlayerListener(ZeAuctionHouse plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        plugin.getDeliveryManager().notifyOnJoin(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.getSessionManager().onPlayerQuit(event.getPlayer().getUniqueId());
        plugin.getGuiListener().unregisterGUI(event.getPlayer().getUniqueId());
    }
}
