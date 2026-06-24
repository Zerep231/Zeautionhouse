package dev.zerep.zeah.listeners;

import dev.zerep.zeah.ZeAuctionHouse;
import dev.zerep.zeah.gui.*;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class GUIListener implements Listener {

    private final ZeAuctionHouse plugin;
    // Track open GUIs by player UUID
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

        AuctionGUI gui = openGUIs.get(player.getUniqueId());
        if (gui == null) {
            // Check if it's one of our named GUIs by title
            if (!isZeAHInventory(event)) return;
        }

        // Block ALL dangerous actions regardless
        event.setCancelled(true);

        if (event.getClickedInventory() == null) return;
        if (event.getClickedInventory() != event.getView().getTopInventory()) return;

        // Block shift, double click, number key, drag in our inventories
        if (AuctionGUI.isDangerous(event)) { event.setCancelled(true); return; }

        if (gui != null) {
            gui.handleClick(event.getSlot(), event.getClick(), event);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (openGUIs.containsKey(player.getUniqueId()) || isZeAHInventoryTitle(event.getView().title())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player player) {
            openGUIs.remove(player.getUniqueId());
        }
    }

    private boolean isZeAHInventory(InventoryClickEvent event) {
        return isZeAHInventoryTitle(event.getView().title());
    }

    private boolean isZeAHInventoryTitle(net.kyori.adventure.text.Component title) {
        String plain = PlainTextComponentSerializer.plainText().serialize(title);
        return plain.contains("Auction House") || plain.contains("My Listings")
            || plain.contains("Confirm") || plain.contains("Mailbox")
            || plain.contains("Builder Shop") || plain.contains("Nhà Đấu Giá")
            || plain.contains("Hộp Thư") || plain.contains("Cửa Hàng");
    }
}
