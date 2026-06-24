package dev.zerep.zeah.gui;

import dev.zerep.zeah.ZeAuctionHouse;
import dev.zerep.zeah.session.CreateSession;
import dev.zerep.zeah.utils.ColorUtil;
import dev.zerep.zeah.utils.ItemSerializer;
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
            inventory = Bukkit.createInventory(null, 27, ColorUtil.color(title));
            for (int i = 0; i < 27; i++) inventory.setItem(i, filler());

            // Preview item
            try {
                ItemStack preview = session.getItem().clone();
                ItemMeta meta = preview.getItemMeta();
                double feePercent = plugin.getConfig().getDouble("limits.listing-fee-percent", 0.0);
                double fee = session.getPrice() * feePercent / 100.0;
                double net = session.getPrice() - fee;
                String itemName = meta.hasDisplayName()
                    ? ColorUtil.strip(meta.displayName().toString())
                    : ColorUtil.formatMaterial(preview.getType().name());

                List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
                for (String line : plugin.getLang().getList("gui.confirm-sell-lore")) {
                    lore.add(ColorUtil.color(line
                        .replace("{item}", itemName)
                        .replace("{price}", ColorUtil.formatPrice(session.getPrice()))
                        .replace("{fee}", ColorUtil.formatPrice(fee))
                        .replace("{net}", ColorUtil.formatPrice(net))));
                }
                meta.lore(lore);
                preview.setItemMeta(meta);
                inventory.setItem(13, preview);
            } catch (Exception ignored) {}

            inventory.setItem(11, buildItem(Material.LIME_STAINED_GLASS_PANE,
                plugin.getLang().getNoPrefix("gui.confirm-yes"),
                List.of("&7List this item for sale")));
            inventory.setItem(15, buildItem(Material.RED_STAINED_GLASS_PANE,
                plugin.getLang().getNoPrefix("gui.confirm-no"),
                List.of("&7Cancel & return item")));

            player.openInventory(inventory);
            register();
        });
    }

    @Override
    public void handleClick(int slot, ClickType clickType, InventoryClickEvent event) {
        event.setCancelled(true);
        if (slot == 11) {
            player.closeInventory();
            plugin.getAuctionManager().completeSell(player, session);
        } else if (slot == 15) {
            player.closeInventory();
            plugin.getSessionManager().abortSession(player.getUniqueId(), true);
        }
    }
}
