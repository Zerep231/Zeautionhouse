package dev.zerep.zeah.gui;

import dev.zerep.zeah.ZeAuctionHouse;
import dev.zerep.zeah.shop.ShopItem;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public class ShopQuantityGUI extends AuctionGUI {

    private final ShopItem shopItem;
    private int quantity;

    public ShopQuantityGUI(ZeAuctionHouse plugin, Player player, ShopItem shopItem) {
        super(plugin, player);
        this.shopItem = shopItem;
        this.quantity = 1;
    }

    @Override
    public void open() {
        inventory = Bukkit.createInventory(null, 27,
            net.kyori.adventure.text.Component.text(
                plugin.getLang().getNoPrefix("gui.shop-quantity.title")));
        render();
        player.openInventory(inventory);
        register();
    }

    private void render() {
        inventory.clear();

        // Borders
        for (int i = 0; i < 27; i++) inventory.setItem(i, filler());

        // Item display in centre
        ItemStack display = new ItemStack(shopItem.getMaterial(),
            Math.max(1, Math.min(quantity, shopItem.getMaterial().getMaxStackSize())));
        inventory.setItem(13, buildItem(display.getType(),
            "&e" + quantity + "x " + dev.zerep.zeah.utils.ColorUtil.formatMaterial(shopItem.getMaterial().name()),
            List.of(
                "&7Đơn giá: &e" + plugin.getEconomy().format((int) shopItem.getPrice()),
                "&7Tổng: &a" + plugin.getEconomy().format((int) (shopItem.getPrice() * quantity))
            )));

        // Decrease buttons
        inventory.setItem(9,  buildItem(Material.RED_CONCRETE,    "&c-64"));
        inventory.setItem(10, buildItem(Material.ORANGE_CONCRETE, "&c-10"));
        inventory.setItem(11, buildItem(Material.YELLOW_CONCRETE, "&c-1"));

        // Increase buttons
        inventory.setItem(15, buildItem(Material.LIME_CONCRETE,  "&a+1"));
        inventory.setItem(16, buildItem(Material.CYAN_CONCRETE,  "&a+10"));
        inventory.setItem(17, buildItem(Material.BLUE_CONCRETE,  "&a+64"));

        // Confirm
        inventory.setItem(22, buildItem(Material.EMERALD, "&a&lXÁC NHẬN",
            List.of("&7Tổng: &e" + plugin.getEconomy().format((int) (shopItem.getPrice() * quantity)))));

        // Back
        inventory.setItem(18, buildItem(Material.ARROW, "&cQuay lại"));
    }

    @Override
    public void handleClick(int slot, ClickType click, InventoryClickEvent event) {
        event.setCancelled(true);
        switch (slot) {
            case 9  -> changeQty(-64);
            case 10 -> changeQty(-10);
            case 11 -> changeQty(-1);
            case 15 -> changeQty(1);
            case 16 -> changeQty(10);
            case 17 -> changeQty(64);
            case 22 -> {
                player.closeInventory();
                plugin.getGuiListener().unregisterGUI(player.getUniqueId());
                new ShopConfirmGUI(plugin, player, shopItem, quantity).open();
            }
            case 18 -> {
                player.closeInventory();
                plugin.getGuiListener().unregisterGUI(player.getUniqueId());
                new ShopCategoryGUI(plugin, player).open();
            }
        }
    }

    private void changeQty(int delta) {
        quantity = Math.max(1, Math.min(quantity + delta, 2304));
        render();
    }
}
