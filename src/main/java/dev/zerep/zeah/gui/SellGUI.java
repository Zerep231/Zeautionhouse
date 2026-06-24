package dev.zerep.zeah.gui;

import dev.zerep.zeah.ZeAuctionHouse;
import dev.zerep.zeah.utils.ColorUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * GUI-based sell flow — fully Geyser/Bedrock compatible, no Anvil or chat input.
 *
 * Layout (27 slots / 3 rows):
 *  Row 0:  ░  ░  ░  ░ [ITEM] ░  ░  ░  ░
 *  Row 1: [-100][-10][-1][PRICE]+1][+10][+100] ░  ░
 *  Row 2: [BACK] ░  ░  ░  ░  ░  ░  ░ [CONFIRM]
 */
public class SellGUI extends AuctionGUI {

    private static final int SLOT_ITEM  = 4;
    private static final int SLOT_PRICE = 13;

    private final ItemStack item;
    private int price;
    private final boolean valid;

    public SellGUI(ZeAuctionHouse plugin, Player player) {
        super(plugin, player);

        ItemStack held = player.getInventory().getItemInMainHand();
        Material currency = plugin.getEconomy().getCurrencyMaterial();

        // Validation
        Set<String> blacklist = plugin.getConfig().getStringList("blacklist")
            .stream().map(String::toUpperCase).collect(Collectors.toSet());

        boolean ok = !held.getType().isAir()
            && held.getType() != Material.AIR
            && held.getType() != currency
            && !blacklist.contains(held.getType().name());

        if (ok || player.hasPermission("zeah.bypass.blacklist")) {
            this.item = held.clone();
            this.valid = !held.getType().isAir();
        } else {
            this.item = null;
            this.valid = false;
        }

        this.price = plugin.getConfig().getInt("limits.min-price", 1);
    }

    @Override
    public void open() {
        inventory = Bukkit.createInventory(null, 27,
            ColorUtil.color(plugin.getLang().getNoPrefix("gui.sell.title")));
        render();
        player.openInventory(inventory);
        register();
    }

    private void render() {
        for (int i = 0; i < 27; i++) inventory.setItem(i, filler());

        if (!valid || item == null) {
            inventory.setItem(SLOT_ITEM, buildItem(Material.BARRIER,
                "&c&lNo item selected!",
                List.of(
                    "&7Hold the item you want to sell",
                    "&7in your main hand, then",
                    "&7reopen with &e/ah sell"
                )));
            inventory.setItem(22, buildItem(Material.ARROW, "&7« Back", null));
            return;
        }

        // Item preview
        ItemStack preview = item.clone();
        ItemMeta meta = preview.getItemMeta();
        List<net.kyori.adventure.text.Component> lore = meta.hasLore()
            ? new java.util.ArrayList<>(meta.lore()) : new java.util.ArrayList<>();
        lore.add(net.kyori.adventure.text.Component.empty());
        lore.add(ColorUtil.color("&7Amount: &f" + item.getAmount()));
        meta.lore(lore);
        preview.setItemMeta(meta);
        inventory.setItem(SLOT_ITEM, preview);

        // Price adjustment buttons
        inventory.setItem(9,  buildItem(Material.RED_CONCRETE,    "&c-100"));
        inventory.setItem(10, buildItem(Material.ORANGE_CONCRETE, "&c-10"));
        inventory.setItem(11, buildItem(Material.YELLOW_CONCRETE, "&c-1"));

        // Price display
        int minPrice = plugin.getConfig().getInt("limits.min-price", 1);
        int maxPrice = plugin.getConfig().getInt("limits.max-price", 9999);
        inventory.setItem(SLOT_PRICE, buildItem(Material.GOLD_NUGGET,
            "&e&lPrice: &6" + plugin.getEconomy().format(price),
            List.of(
                "&7Min: &f" + plugin.getEconomy().format(minPrice),
                "&7Max: &f" + plugin.getEconomy().format(maxPrice),
                "",
                "&7Adjust with the colored buttons"
            )));

        inventory.setItem(15, buildItem(Material.LIME_CONCRETE,   "&a+1"));
        inventory.setItem(16, buildItem(Material.CYAN_CONCRETE,   "&a+10"));
        inventory.setItem(17, buildItem(Material.BLUE_CONCRETE,   "&a+100"));

        // Bottom row
        inventory.setItem(18, buildItem(Material.ARROW, "&7« Back", null));
        inventory.setItem(26, buildItem(Material.EMERALD, "&a&lList Item",
            List.of(
                "&7Item: &f" + ColorUtil.formatMaterial(item.getType().name()),
                "&7Price: &6" + plugin.getEconomy().format(price),
                "",
                "&aClick to continue"
            )));
    }

    @Override
    public void handleClick(int slot, ClickType click, InventoryClickEvent event) {
        event.setCancelled(true);

        if (!valid) {
            if (slot == 22) { player.closeInventory(); new MainMenuGUI(plugin, player).open(); }
            return;
        }

        int minPrice = plugin.getConfig().getInt("limits.min-price", 1);
        int maxPrice = plugin.getConfig().getInt("limits.max-price", 9999);

        switch (slot) {
            case 9  -> { price = Math.max(minPrice, price - 100); render(); }
            case 10 -> { price = Math.max(minPrice, price - 10);  render(); }
            case 11 -> { price = Math.max(minPrice, price - 1);   render(); }
            case 15 -> { price = Math.min(maxPrice, price + 1);   render(); }
            case 16 -> { price = Math.min(maxPrice, price + 10);  render(); }
            case 17 -> { price = Math.min(maxPrice, price + 100); render(); }
            case 18 -> { player.closeInventory(); new MainMenuGUI(plugin, player).open(); }
            case 26 -> confirmSell(minPrice, maxPrice);
        }
    }

    private void confirmSell(int minPrice, int maxPrice) {
        if (price < minPrice || price > maxPrice) {
            player.sendMessage(plugin.getLang().format("auction.price-too-low",
                "min", plugin.getEconomy().format(minPrice)));
            return;
        }
        if (plugin.getSessionManager().hasSession(player.getUniqueId())) {
            player.sendMessage(plugin.getLang().format("auction.session-active"));
            return;
        }
        // Check blacklist
        Set<String> blacklist = plugin.getConfig().getStringList("blacklist")
            .stream().map(String::toUpperCase).collect(Collectors.toSet());
        if (blacklist.contains(item.getType().name()) && !player.hasPermission("zeah.bypass.blacklist")) {
            player.sendMessage(plugin.getLang().format("auction.blacklisted"));
            return;
        }

        // Create session — take item from hand
        if (!plugin.getSessionManager().createSession(player, item)) {
            player.sendMessage(plugin.getLang().format("auction.session-active"));
            return;
        }
        plugin.getSessionManager().getSession(player.getUniqueId()).ifPresent(session -> {
            session.setPrice(price);
            // Remove item from player hand
            player.getInventory().setItemInMainHand(
                item.getAmount() > 1
                    ? new ItemStack(item.getType(), item.getAmount() - 1)
                    : new ItemStack(Material.AIR)
            );
            // Actually take the whole stack for listing
            player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
            player.closeInventory();
            Bukkit.getScheduler().runTask(plugin, () ->
                new ConfirmSellGUI(plugin, player, session).open());
        });
    }
}
