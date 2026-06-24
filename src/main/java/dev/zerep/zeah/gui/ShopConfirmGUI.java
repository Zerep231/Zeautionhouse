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

public class ShopConfirmGUI extends AuctionGUI {

    private final ShopItem shopItem;
    private final int quantity;
    private final int totalPrice;

    public ShopConfirmGUI(ZeAuctionHouse plugin, Player player, ShopItem shopItem, int quantity) {
        super(plugin, player);
        this.shopItem = shopItem;
        this.quantity = quantity;
        this.totalPrice = (int) (shopItem.getPrice() * quantity);
    }

    @Override
    public void open() {
        inventory = Bukkit.createInventory(null, 27,
            net.kyori.adventure.text.Component.text(
                plugin.getLang().getNoPrefix("gui.shop-confirm.title")));
        render();
        player.openInventory(inventory);
        register();
    }

    private void render() {
        inventory.clear();
        for (int i = 0; i < 27; i++) inventory.setItem(i, filler());

        // Item preview
        inventory.setItem(13, buildItem(shopItem.getMaterial(),
            "&e" + quantity + "x " + dev.zerep.zeah.utils.ColorUtil.formatMaterial(shopItem.getMaterial().name()),
            List.of(
                "&7Đơn giá: &e" + plugin.getEconomy().format((int) shopItem.getPrice()),
                "&7Tổng: &a" + plugin.getEconomy().format(totalPrice),
                "",
                "&7Số dư: &e" + plugin.getEconomy().format(plugin.getEconomy().getBalance(player))
            )));

        // Confirm
        inventory.setItem(11, buildItem(Material.GREEN_CONCRETE, "&a&lXÁC NHẬN MUA",
            List.of("&7Nhấn để mua &e" + quantity + "x " +
                dev.zerep.zeah.utils.ColorUtil.formatMaterial(shopItem.getMaterial().name()))));

        // Cancel
        inventory.setItem(15, buildItem(Material.RED_CONCRETE, "&c&lHỦY BỎ"));

        // Back
        inventory.setItem(18, buildItem(Material.ARROW, "&cQuay lại"));
    }

    @Override
    public void handleClick(int slot, ClickType click, InventoryClickEvent event) {
        event.setCancelled(true);
        switch (slot) {
            case 11 -> doPurchase();
            case 15, 18 -> {
                player.closeInventory();
                plugin.getGuiListener().unregisterGUI(player.getUniqueId());
                new ShopQuantityGUI(plugin, player, shopItem).open();
            }
        }
    }

    private void doPurchase() {
        player.closeInventory();
        plugin.getGuiListener().unregisterGUI(player.getUniqueId());
        plugin.getShopManager().purchase(player, shopItem, quantity);
    }
}
