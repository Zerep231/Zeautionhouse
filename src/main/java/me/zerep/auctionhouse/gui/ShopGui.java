package me.zerep.auctionhouse.gui;

import me.zerep.auctionhouse.AuctionHousePlugin;
import me.zerep.auctionhouse.shop.ShopService;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.util.List;

/**
 * P2.1 Fix – Full Builder Shop flow: Home → Category → Item → Confirm purchase.
 * Each GUI uses AhHolder slot maps so click handlers always identify the correct
 * category / item regardless of position.
 */
public class ShopGui {

    private final AuctionHousePlugin plugin;

    public ShopGui(AuctionHousePlugin plugin) { this.plugin = plugin; }

    // ─── Home ────────────────────────────────────────────────────────────────

    public void openHome(Player player) {
        AhHolder holder = new AhHolder(GuiTag.SHOP_HOME, "shop-home");
        Inventory inv = Bukkit.createInventory(holder, 27,
                Gfx.color("&aBuild Shop &7– Categories"));
        for (int i = 0; i < 27; i++) inv.setItem(i, Gfx.filler());

        List<ShopService.ShopCategory> cats = plugin.getShopService().getCategories();
        int slot = 10;
        for (ShopService.ShopCategory cat : cats) {
            if (slot > 16) break;
            inv.setItem(slot, Gfx.item(Material.CHEST,
                    "&a" + Gfx.color(cat.title()),
                    "&7" + cat.items().size() + " items available",
                    "&eClick to browse"));
            holder.mapCategory(slot, cat.id());
            slot++;
        }
        inv.setItem(22, Gfx.item(Material.ARROW, "&cBack", "&7Return to AH"));
        player.openInventory(inv);
    }

    // ─── Category ────────────────────────────────────────────────────────────

    public void openCategory(Player player, String catId) {
        ShopService.ShopCategory cat = plugin.getShopService().getCategory(catId);
        if (cat == null) { openHome(player); return; }

        AhHolder holder = new AhHolder(GuiTag.SHOP_CAT, "shop-cat:" + catId);
        Inventory inv = Bukkit.createInventory(holder, 54,
                Gfx.color("&a" + Gfx.color(cat.title())));
        for (int i = 0; i < 54; i++) inv.setItem(i, Gfx.filler());

        int slot = 0;
        for (ShopService.ShopEntry entry : cat.items()) {
            if (slot >= 45) break;
            // Show cheapest currency
            String cheapCur = entry.currencies().isEmpty() ? "?"
                    : entry.currencies().get(0).name().replace('_', ' ');
            inv.setItem(slot, Gfx.item(entry.material(),
                    "&f" + entry.material().name().replace('_', ' ') + " x" + entry.amount(),
                    "&7Price: &e" + entry.price() + " &7" + cheapCur,
                    "&7Accepts: " + currencyList(entry),
                    "&aClick to buy"));
            holder.mapItem(slot, catId + ":" + entry.id());
            slot++;
        }
        inv.setItem(49, Gfx.item(Material.ARROW, "&cBack", "&7Return to categories"));
        player.openInventory(inv);
    }

    // ─── Confirm (quantity = 1) ───────────────────────────────────────────────

    public void openConfirm(Player player, String catId, String itemId, Material currency) {
        ShopService.ShopEntry entry = plugin.getShopService().getEntry(catId, itemId);
        if (entry == null) { openHome(player); return; }

        AhHolder holder = new AhHolder(GuiTag.SHOP_CONFIRM, "shop-confirm:" + catId + ":" + itemId);
        Inventory inv = Bukkit.createInventory(holder, 27,
                Gfx.color("&6Confirm Purchase"));
        for (int i = 0; i < 27; i++) inv.setItem(i, Gfx.filler());

        inv.setItem(13, Gfx.item(entry.material(),
                "&f" + entry.material().name().replace('_', ' ') + " x" + entry.amount(),
                "&7Price: &e" + entry.price() + " &7" + currency.name().replace('_', ' ')));

        boolean canAfford = plugin.getShopService().canBuy(player, entry, currency, 1);
        inv.setItem(11, Gfx.item(Material.LIME_DYE,
                canAfford ? "&aBuy x1" : "&cNot enough currency",
                "&7Cost: " + entry.price() + "x " + currency.name().replace('_', ' ')));
        inv.setItem(15, Gfx.item(Material.ARROW, "&cCancel", "&7Go back"));

        // Encode needed info in the holder key so the listener can act
        // The listener reads catId:itemId from holder.key() when confirm is clicked
        player.openInventory(inv);
    }

    private String currencyList(ShopService.ShopEntry entry) {
        StringBuilder sb = new StringBuilder();
        for (Material m : entry.currencies()) {
            if (!sb.isEmpty()) sb.append(", ");
            sb.append(m.name().replace('_', ' '));
        }
        return sb.toString();
    }
}
