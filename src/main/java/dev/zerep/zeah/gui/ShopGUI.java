package dev.zerep.zeah.gui;

import dev.zerep.zeah.ZeAuctionHouse;
import dev.zerep.zeah.shop.ShopCategory;
import dev.zerep.zeah.utils.ColorUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ShopGUI extends AuctionGUI {

    private final Map<Integer, ShopCategory> slotToCategory = new HashMap<>();

    public ShopGUI(ZeAuctionHouse plugin, Player player) {
        super(plugin, player);
    }

    @Override
    public void open() {
        List<ShopCategory> categories = plugin.getShopManager().getCategories();
        int rows = Math.max(3, (int) Math.ceil((categories.size() + 9) / 9.0));
        int size = Math.min(rows * 9, 54);

        String title = plugin.getLang().getNoPrefix("shop.title");
        inventory = Bukkit.createInventory(new ZeAHHolder(), size, ColorUtil.color(title));

        for (int i = 0; i < size; i++) inventory.setItem(i, filler());
        slotToCategory.clear();

        // Display categories starting at slot 10 for border effect
        int[] slots = computeSlots(categories.size(), size);
        for (int i = 0; i < categories.size() && i < slots.length; i++) {
            ShopCategory cat = categories.get(i);
            inventory.setItem(slots[i], buildItem(cat.getIcon(), "&6&l" + cat.getName(),
                List.of("&7Click to browse", "&e" + cat.getItems().size() + " items available")));
            slotToCategory.put(slots[i], cat);
        }

        int closeSlot = size - 5;
        inventory.setItem(closeSlot, buildItem(Material.BARRIER, plugin.getLang().getNoPrefix("gui.close")));

        player.openInventory(inventory);
        register();
    }

    private int[] computeSlots(int count, int size) {
        // Center items in rows
        List<Integer> slots = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            if (i % 9 != 0 && i % 9 != 8 && i >= 9 && i < size - 9) slots.add(i);
        }
        return slots.stream().mapToInt(Integer::intValue).toArray();
    }

    @Override
    public void handleClick(int slot, ClickType clickType, InventoryClickEvent event) {
        event.setCancelled(true);
        int closeSlot = inventory.getSize() - 5;
        if (slot == closeSlot) { player.closeInventory(); return; }

        if (slotToCategory.containsKey(slot)) {
            ShopCategory cat = slotToCategory.get(slot);
            player.closeInventory();
            new ShopCategoryGUI(plugin, player, cat).open();
        }
    }
}
