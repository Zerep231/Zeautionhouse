package dev.zerep.zeah.gui;

import dev.zerep.zeah.ZeAuctionHouse;
import dev.zerep.zeah.currency.CurrencyManager;
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
import java.util.Map;

public class PriceSelectionGUI extends AuctionGUI {
    private final CreateSession session;
    private int price = 1;
    private String currencyId;
    private final int minPrice;
    private final int maxPrice;

    public PriceSelectionGUI(ZeAuctionHouse plugin, Player player, CreateSession session) {
        super(plugin, player);
        this.session = session;
        this.minPrice = plugin.getConfig().getInt("limits.min-price", 1);
        this.maxPrice = plugin.getConfig().getInt("limits.max-price", 99999);
        this.currencyId = plugin.getCurrencyManager().getDefaultCurrency().id();
        this.price = minPrice;
        session.setPrice(price);
        session.setCurrencyId(currencyId);
    }

    @Override
    public void open() {
        inventory = Bukkit.createInventory(new ZeAHHolder(), 54, ColorUtil.color("&8Select Price & Currency"));
        render();
        player.openInventory(inventory);
        register();
    }

    private void render() {
        for (int i = 0; i < 54; i++) {
            inventory.setItem(i, filler());
        }

        // The item being sold
        inventory.setItem(13, session.getItem());

        // Currencies (Row 3: 18-26)
        Map<String, CurrencyManager.Currency> currencies = plugin.getCurrencyManager().getCurrencies();
        int curSlot = 18;
        int totalCurrencies = currencies.size();
        curSlot = 22 - (totalCurrencies / 2); // Center them

        for (CurrencyManager.Currency c : currencies.values()) {
            ItemStack cItem = new ItemStack(c.material());
            ItemMeta meta = cItem.getItemMeta();
            meta.displayName(ColorUtil.color("&e&l" + c.name()));
            List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
            if (c.id().equals(currencyId)) {
                lore.add(ColorUtil.color("&a✔ Selected"));
                meta.setEnchantmentGlintOverride(true);
            } else {
                lore.add(ColorUtil.color("&7Click to select this currency"));
            }
            meta.lore(lore);
            cItem.setItemMeta(meta);
            inventory.setItem(curSlot++, cItem);
        }

        // Price adjustments (Row 4: 27-35)
        inventory.setItem(27, buildItem(Material.RED_CONCRETE, "&c-128"));
        inventory.setItem(28, buildItem(Material.ORANGE_CONCRETE, "&c-64"));
        inventory.setItem(29, buildItem(Material.YELLOW_CONCRETE, "&c-10"));
        inventory.setItem(30, buildItem(Material.PINK_CONCRETE, "&c-1"));

        inventory.setItem(31, buildItem(Material.GOLD_NUGGET, 
            "&e&lPrice: &6" + plugin.getCurrencyManager().format(price, currencyId),
            List.of("&7Min: " + minPrice, "&7Max: " + maxPrice)));

        inventory.setItem(32, buildItem(Material.LIME_CONCRETE, "&a+1"));
        inventory.setItem(33, buildItem(Material.GREEN_CONCRETE, "&a+10"));
        inventory.setItem(34, buildItem(Material.CYAN_CONCRETE, "&a+64"));
        inventory.setItem(35, buildItem(Material.BLUE_CONCRETE, "&a+128"));

        // Confirm & Cancel (Row 6: 45-53)
        inventory.setItem(48, buildItem(Material.RED_STAINED_GLASS_PANE, "&c&lCancel", List.of("&7Return item and cancel")));
        inventory.setItem(50, buildItem(Material.LIME_STAINED_GLASS_PANE, "&a&lConfirm", List.of("&7List item for auction")));
    }

    @Override
    public void handleClick(int slot, ClickType clickType, InventoryClickEvent event) {
        event.setCancelled(true);

        // Handle Currency Click
        if (slot >= 18 && slot <= 26) {
            ItemStack clicked = inventory.getItem(slot);
            if (clicked != null && clicked.getType() != filler().getType()) {
                for (CurrencyManager.Currency c : plugin.getCurrencyManager().getCurrencies().values()) {
                    if (c.material() == clicked.getType()) {
                        currencyId = c.id();
                        session.setCurrencyId(currencyId);
                        render();
                        return;
                    }
                }
            }
        }

        switch (slot) {
            case 27 -> { price = Math.max(minPrice, price - 128); session.setPrice(price); render(); }
            case 28 -> { price = Math.max(minPrice, price - 64); session.setPrice(price); render(); }
            case 29 -> { price = Math.max(minPrice, price - 10); session.setPrice(price); render(); }
            case 30 -> { price = Math.max(minPrice, price - 1); session.setPrice(price); render(); }
            case 32 -> { price = Math.min(maxPrice, price + 1); session.setPrice(price); render(); }
            case 33 -> { price = Math.min(maxPrice, price + 10); session.setPrice(price); render(); }
            case 34 -> { price = Math.min(maxPrice, price + 64); session.setPrice(price); render(); }
            case 35 -> { price = Math.min(maxPrice, price + 128); session.setPrice(price); render(); }
            case 48 -> {
                // Cancel
                plugin.getSessionManager().removeSession(player.getUniqueId());
                var overflow = player.getInventory().addItem(session.getItem());
                overflow.values().forEach(is -> player.getWorld().dropItemNaturally(player.getLocation(), is));
                player.closeInventory();
                player.sendMessage(ColorUtil.color("&cListing cancelled."));
            }
            case 50 -> {
                // Confirm
                player.closeInventory();
                plugin.getAuctionManager().completeSell(player, session);
            }
        }
    }
}
