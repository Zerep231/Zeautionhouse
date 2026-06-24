package dev.zerep.zeah.listeners;

import dev.zerep.zeah.ZeAuctionHouse;
import dev.zerep.zeah.gui.AuctionGUI;
import dev.zerep.zeah.gui.ZeAHHolder;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.InventoryHolder;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class GUIListener implements Listener {

    private final ZeAuctionHouse plugin;
    private final Map<UUID, AuctionGUI> openGUIs = new ConcurrentHashMap<>();

    public GUIListener(ZeAuctionHouse plugin) {
        this.plugin = plugin;
    }

    public void registerGUI(UUID uuid, AuctionGUI gui) {
        openGUIs.put(uuid, gui);
    }

    public void unregisterGUI(UUID uuid) {
        openGUIs.remove(uuid);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        // Reliable detection via InventoryHolder — no fragile title matching
        InventoryHolder holder = event.getView().getTopInventory().getHolder();
        if (!(holder instanceof ZeAHHolder)) return;

        event.setCancelled(true);

        if (event.getClickedInventory() == null) return;
        if (event.getClickedInventory() != event.getView().getTopInventory()) return;
        if (AuctionGUI.isDangerous(event)) return;

        AuctionGUI gui = openGUIs.get(player.getUniqueId());
        if (gui != null) gui.handleClick(event.getSlot(), event.getClick(), event);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        if (event.getView().getTopInventory().getHolder() instanceof ZeAHHolder)
            event.setCancelled(true);
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player player)
            openGUIs.remove(player.getUniqueId());
    }
}
