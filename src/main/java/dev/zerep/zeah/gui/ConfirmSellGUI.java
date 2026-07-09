package dev.zerep.zeah.gui;

import dev.zerep.zeah.ZeAuctionHouse;
import dev.zerep.zeah.session.CreateSession;
import dev.zerep.zeah.utils.ColorUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public class ConfirmSellGUI extends AuctionGUI {
    private final CreateSession session;

    public ConfirmSellGUI(ZeAuctionHouse plugin, Player player, CreateSession session) {
        super(plugin, player);
        this.session = session;
    }

    @Override
    public void open() {
        Bukkit.getScheduler().runTask(plugin, () -> {
            String title = plugin.getLang().getNoPrefix("gui.sell-confirm-title");
            inventory = Bukkit.createInventory(new ZeAHHolder(), 45, ColorUtil.color(title));

            // Fill with blue glass
            ItemStack blueGlass = buildItem(Material.LIGHT_BLUE_STAINED_GLASS_PANE, " ", List.of());
            for (int i = 0; i < 45; i++) {
                inventory.setItem(i, blueGlass);
            }

            // Yes buttons (Green)
            ItemStack yesBtn = buildItem(Material.LIME_STAINED_GLASS_PANE,
                plugin.getLang().getNoPrefix("gui.confirm-yes"),
                List.of("&7List this item for sale"));
            int[] yesSlots = {10, 11, 19, 20, 28, 29};
            for (int slot : yesSlots) {
                inventory.setItem(slot, yesBtn);
            }

            // No buttons (Red)
            ItemStack noBtn = buildItem(Material.RED_STAINED_GLASS_PANE,
                plugin.getLang().getNoPrefix("gui.confirm-no"),
                List.of("&7Cancel & return item"));
            int[] noSlots = {15, 16, 24, 25, 33, 34};
            for (int slot : noSlots) {
                inventory.setItem(slot, noBtn);
            }

            // Empty / Gray areas around the item
            ItemStack grayGlass = filler();
            int[] graySlots = {12, 13, 14, 21, 23, 30, 31, 32};
            for (int slot : graySlots) {
                inventory.setItem(slot, grayGlass);
            }

            // Cancel Barrier at bottom
            inventory.setItem(40, buildItem(Material.BARRIER, plugin.getLang().getNoPrefix("gui.confirm-no"), List.of("&7Cancel & return item")));

            try {
                ItemStack preview = session.getItem().clone();
                ItemMeta meta = preview.getItemMeta();
                int price = (int) session.getPrice();
                String itemName = meta.hasDisplayName()
                    ? ColorUtil.strip(meta.displayName().toString())
                    : ColorUtil.formatMaterial(preview.getType().name());

                List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
                for (String line : plugin.getLang().getList("gui.confirm-sell-lore")) {
                    lore.add(ColorUtil.color(line
                        .replace("{item}", itemName)
                        .replace("{price}", plugin.getEconomy().format(price))));
                }
                meta.lore(lore);
                preview.setItemMeta(meta);
                inventory.setItem(22, preview);
            } catch (Exception ignored) {}

            player.openInventory(inventory);
            register();
        });
    }

    private boolean isYesSlot(int slot) {
        return slot == 10 || slot == 11 || slot == 19 || slot == 20 || slot == 28 || slot == 29;
    }

    private boolean isNoSlot(int slot) {
        return slot == 15 || slot == 16 || slot == 24 || slot == 25 || slot == 33 || slot == 34 || slot == 40;
    }

    @Override
    public void handleClick(int slot, ClickType clickType, InventoryClickEvent event) {
        event.setCancelled(true);
        if (isYesSlot(slot)) {
            player.closeInventory();
            plugin.getAuctionManager().completeSell(player, session);
        } else if (isNoSlot(slot)) {
            player.closeInventory();
            plugin.getSessionManager().abortSession(player.getUniqueId(), true);
        }
    }
}
