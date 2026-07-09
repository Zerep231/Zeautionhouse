package dev.zerep.zeah.gui;

import dev.zerep.zeah.ZeAuctionHouse;
import dev.zerep.zeah.utils.ColorUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

/**
 * Base class for all ZeAH GUIs.
 * Handles click protection, filler, and common item builders.
 */
public abstract class AuctionGUI {

    protected final ZeAuctionHouse plugin;
    protected final Player player;
    protected Inventory inventory;

    protected AuctionGUI(ZeAuctionHouse plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
    }

    /** Create an inventory tagged with ZeAHHolder so GUIListener can identify it reliably. */
    protected org.bukkit.inventory.Inventory createInventory(int size, String title) {
        return Bukkit.createInventory(new ZeAHHolder(), size, dev.zerep.zeah.utils.ColorUtil.color(title));
    }
    protected org.bukkit.inventory.Inventory createInventory(int size, net.kyori.adventure.text.Component title) {
        return Bukkit.createInventory(new ZeAHHolder(), size, title);
    }

    public abstract void open();
    public abstract void handleClick(int slot, ClickType clickType, InventoryClickEvent event);

    /** Block all dangerous inventory interactions. */
    public static boolean isDangerous(InventoryClickEvent event) {
        return switch (event.getAction()) {
            case PICKUP_HALF, PICKUP_ALL, PICKUP_ONE, PICKUP_SOME,
                 DROP_ALL_SLOT, DROP_ONE_SLOT,
                 MOVE_TO_OTHER_INVENTORY,
                 HOTBAR_SWAP, HOTBAR_MOVE_AND_READD,
                 COLLECT_TO_CURSOR,
                 CLONE_STACK -> true;
            default -> false;
        };
    }

    /** Black glass pane filler. */
    protected ItemStack filler() {
        ItemStack item = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(ColorUtil.color(" "));
        item.setItemMeta(meta);
        return item;
    }

    protected ItemStack grayFiller() {
        ItemStack item = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(ColorUtil.color(" "));
        item.setItemMeta(meta);
        return item;
    }

    protected ItemStack buildItem(Material mat, String name, List<String> lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(ColorUtil.color(name));
        if (lore != null && !lore.isEmpty()) meta.lore(ColorUtil.colorList(lore));
        item.setItemMeta(meta);
        return item;
    }

    protected ItemStack buildItem(Material mat, String name) {
        return buildItem(mat, name, null);
    }

    protected void fillBorder(int size) {
        for (int i = 0; i < 9; i++) inventory.setItem(i, filler());
        for (int i = size - 9; i < size; i++) inventory.setItem(i, filler());
        for (int i = 9; i < size - 9; i += 9) inventory.setItem(i, filler());
        for (int i = 17; i < size - 9; i += 9) inventory.setItem(i, filler());
    }

    /** Call this after player.openInventory(inventory) to register with the GUI listener. */
    protected void register() {
        plugin.getGuiListener().registerGUI(player.getUniqueId(), this);
    }

    protected void setNavItem(int slot, boolean hasPrev, boolean hasNext) {
        if (hasPrev) {
            inventory.setItem(slot - 2, buildItem(Material.ARROW,
                plugin.getLang().getNoPrefix("gui.prev-page")));
        } else {
            inventory.setItem(slot - 2, grayFiller());
        }
        inventory.setItem(slot, buildItem(Material.BARRIER,
            plugin.getLang().getNoPrefix("gui.close")));
        if (hasNext) {
            inventory.setItem(slot + 2, buildItem(Material.ARROW,
                plugin.getLang().getNoPrefix("gui.next-page")));
        } else {
            inventory.setItem(slot + 2, grayFiller());
        }
    }
}
