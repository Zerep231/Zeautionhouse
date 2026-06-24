package dev.zerep.zeah.gui;

import dev.zerep.zeah.ZeAuctionHouse;
import dev.zerep.zeah.shop.ShopCategory;
import dev.zerep.zeah.shop.ShopItem;
import dev.zerep.zeah.utils.ColorUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ShopCategoryGUI extends AuctionGUI {

    private final ShopCategory category;
    private final Map<Integer, ShopItem> slotToItem = new HashMap<>();

    public ShopCategoryGUI(ZeAuctionHouse plugin, Player player, ShopCategory category) {
        super(plugin, player);
        this.category = category;
    }

    @Override
    public void open() {
        List<ShopItem> items = category.getItems();
        int rows = Math.max(3, (int) Math.ceil((items.size() + 9) / 9.0) + 1);
        int size = Math.min(rows * 9, 54);

        String title = plugin.getLang().formatNoPrefix("shop.category-title", "category", category.getName());
        inventory = Bukkit.createInventory(null, size, ColorUtil.color(title));

        for (int i = 0; i < size; i++) inventory.setItem(i, filler());
        slotToItem.clear();

        for (int i = 0; i < Math.min(items.size(), size - 9); i++) {
            ShopItem si = items.get(i);
            inventory.setItem(i, buildItem(si.getMaterial(), "&f" + ColorUtil.formatMaterial(si.getMaterial().name()),
                List.of(
                    "&7Price: &6" + plugin.getEconomy().format(si.getPrice()) + " &7each",
                    "",
                    "&eLeft-click &7— buy &f1",
                    "&eShift-left &7— buy &f16",
                    "&eRight-click &7— buy &f64"
                )));
            slotToItem.put(i, si);
        }

        int navSlot = size - 5;
        inventory.setItem(navSlot - 2, buildItem(Material.ARROW, "&e« Back to Shop", null));
        inventory.setItem(navSlot, buildItem(Material.BARRIER, plugin.getLang().getNoPrefix("gui.close"), null));

        player.openInventory(inventory);
        register();
    }

    @Override
    public void handleClick(int slot, ClickType clickType, InventoryClickEvent event) {
        event.setCancelled(true);
        int navSlot = inventory.getSize() - 5;
        if (slot == navSlot) { player.closeInventory(); return; }
        if (slot == navSlot - 2) { player.closeInventory(); new ShopGUI(plugin, player).open(); return; }

        if (!slotToItem.containsKey(slot)) return;
        ShopItem si = slotToItem.get(slot);

        int amount = switch (clickType) {
            case SHIFT_LEFT -> 16;
            case RIGHT -> 64;
            default -> 1;
        };

        plugin.getShopManager().purchase(player, si, amount);
    }
}
