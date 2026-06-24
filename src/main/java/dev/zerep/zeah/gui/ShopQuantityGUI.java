package dev.zerep.zeah.gui;

import dev.zerep.zeah.ZeAuctionHouse;
import dev.zerep.zeah.shop.ShopItem;
import dev.zerep.zeah.utils.ColorUtil;
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
        for (int i = 0; i < 27; i++) inventory.setItem(i, filler());

        inventory.setItem(13, buildItem(shopItem.getMaterial(),
            "&e" + quantity + "x " + ColorUtil.formatMaterial(shopItem.getMaterial().name()),
            List.of(
                "&7Unit price: &6" + plugin.getEconomy().format(shopItem.getPrice()),
                "&7Total:      &a" + plugin.getEconomy().format(shopItem.getPrice() * quantity),
                "",
                "&7Your balance: &e" + plugin.getEconomy().format(plugin.getEconomy().getBalance(player))
            )));

        inventory.setItem(9,  buildItem(Material.RED_CONCRETE,    "&c-64"));
        inventory.setItem(10, buildItem(Material.ORANGE_CONCRETE, "&c-10"));
        inventory.setItem(11, buildItem(Material.YELLOW_CONCRETE, "&c-1"));
        inventory.setItem(15, buildItem(Material.LIME_CONCRETE,   "&a+1"));
        inventory.setItem(16, buildItem(Material.CYAN_CONCRETE,   "&a+10"));
        inventory.setItem(17, buildItem(Material.BLUE_CONCRETE,   "&a+64"));

        inventory.setItem(22, buildItem(Material.EMERALD, "&a&lConfirm",
            List.of("&7Total: &e" + plugin.getEconomy().format(shopItem.getPrice() * quantity))));
        inventory.setItem(18, buildItem(Material.ARROW, "&cBack"));
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
                new ShopCategoryGUI(plugin, player,
                    plugin.getShopManager().getCategoryByItem(shopItem)).open();
            }
        }
    }

    private void changeQty(int delta) {
        quantity = Math.max(1, Math.min(quantity + delta, 2304));
        render();
    }
}
