package dev.zerep.zeah.gui;

import dev.zerep.zeah.ZeAuctionHouse;
import dev.zerep.zeah.shop.ShopItem;
import dev.zerep.zeah.utils.ColorUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.util.List;

public class ShopConfirmGUI extends AuctionGUI {

    private final ShopItem shopItem;
    private final int quantity;
    private final int totalPrice;

    public ShopConfirmGUI(ZeAuctionHouse plugin, Player player, ShopItem shopItem, int quantity) {
        super(plugin, player);
        this.shopItem = shopItem;
        this.quantity = quantity;
        this.totalPrice = shopItem.getPrice() * quantity;
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

        boolean canAfford = plugin.getEconomy().has(player, totalPrice);

        inventory.setItem(13, buildItem(shopItem.getMaterial(),
            "&e" + quantity + "x " + ColorUtil.formatMaterial(shopItem.getMaterial().name()),
            List.of(
                "&7Unit price:    &6" + plugin.getEconomy().format(shopItem.getPrice()),
                "&7Total:         &a" + plugin.getEconomy().format(totalPrice),
                "&7Your balance:  " + (canAfford ? "&e" : "&c") + plugin.getEconomy().format(plugin.getEconomy().getBalance(player)),
                "",
                canAfford ? "&aYou can afford this!" : "&cNot enough " + plugin.getEconomy().getCurrencyName() + "!"
            )));

        inventory.setItem(11, buildItem(canAfford ? Material.GREEN_CONCRETE : Material.GRAY_CONCRETE,
            canAfford ? "&a&lConfirm Purchase" : "&7&lCannot Afford",
            List.of("&7Pay " + plugin.getEconomy().format(totalPrice))));

        inventory.setItem(15, buildItem(Material.RED_CONCRETE, "&c&lCancel"));
        inventory.setItem(18, buildItem(Material.ARROW, "&cBack"));
    }

    @Override
    public void handleClick(int slot, ClickType click, InventoryClickEvent event) {
        event.setCancelled(true);
        switch (slot) {
            case 11 -> {
                if (!plugin.getEconomy().has(player, totalPrice)) {
                    player.sendMessage(plugin.getLang().format("shop.not-enough-items",
                        "price", plugin.getEconomy().format(totalPrice)));
                    render();
                    return;
                }
                player.closeInventory();
                plugin.getGuiListener().unregisterGUI(player.getUniqueId());
                plugin.getShopManager().purchase(player, shopItem, quantity);
            }
            case 15 -> {
                player.closeInventory();
                plugin.getGuiListener().unregisterGUI(player.getUniqueId());
            }
            case 18 -> {
                player.closeInventory();
                plugin.getGuiListener().unregisterGUI(player.getUniqueId());
                new ShopQuantityGUI(plugin, player, shopItem).open();
            }
        }
    }
}
