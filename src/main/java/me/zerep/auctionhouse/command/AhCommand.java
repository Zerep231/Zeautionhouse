package me.zerep.auctionhouse.command;

import me.zerep.auctionhouse.AuctionHousePlugin;
import me.zerep.auctionhouse.currency.Currency;
import me.zerep.auctionhouse.listing.ListingService;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.stream.Collectors;

/**
 * P2.2 Fix – Added `/ah sell <price> [currency]` for Bedrock players who
 * cannot reliably use the drag-and-drop GUI.  The item in their main hand
 * is listed immediately without opening any inventory.
 */
public class AhCommand implements CommandExecutor, TabCompleter {

    private final AuctionHousePlugin plugin;

    public AhCommand(AuctionHousePlugin plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.msg("player-only"));
            return true;
        }
        if (!player.hasPermission("ah.use")) {
            player.sendMessage(plugin.msg("no-permission"));
            return true;
        }

        String sub = args.length == 0 ? "" : args[0].toLowerCase();
        switch (sub) {
            case "sell", "create" -> {
                // Bedrock quick-sell: /ah sell <price> [currency]
                if (args.length >= 2) {
                    handleQuickSell(player, args);
                } else {
                    plugin.getGuiManager().openCreate(player);
                }
            }
            case "mine", "my"              -> plugin.getGuiManager().openMine(player, 0);
            case "claim", "deliveries","box"-> plugin.getGuiManager().openDelivery(player, 0);
            case "shop"                    -> plugin.getGuiManager().openShopHome(player);
            case "reload"                  -> {
                if (!player.hasPermission("ah.admin")) {
                    player.sendMessage(plugin.msg("no-permission"));
                    return true;
                }
                plugin.reloadConfig();
                plugin.getCurrencyRegistry().reload();
                plugin.getShopService().reload();
                player.sendMessage(plugin.msg("admin-reload"));
            }
            default -> plugin.getGuiManager().openBrowse(player, 0);
        }
        return true;
    }

    /** Bedrock-friendly quick-sell: lists the item in hand without GUI. */
    private void handleQuickSell(Player player, String[] args) {
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand.getType().isAir()) {
            player.sendMessage(plugin.msg("sell-no-item"));
            return;
        }

        int price;
        try { price = Integer.parseInt(args[1]); }
        catch (NumberFormatException e) {
            player.sendMessage(plugin.msg("sell-invalid-price"));
            return;
        }
        if (price <= 0) { player.sendMessage(plugin.msg("sell-invalid-price")); return; }

        int maxPrice = plugin.getConfig().getInt("max-price", 999999);
        if (price > maxPrice) { player.sendMessage(plugin.msg("sell-price-too-high")); return; }

        String currency = plugin.getCurrencyRegistry().defaultKey();
        if (args.length >= 3) {
            String requested = args[2].toUpperCase();
            if (plugin.getCurrencyRegistry().get(requested) != null) currency = requested;
            else { player.sendMessage(plugin.msg("sell-invalid-currency")); return; }
        }

        ItemStack item = hand.clone();
        player.getInventory().setItemInMainHand(null);
        player.updateInventory();

        int id;
        try {
            id = plugin.getListingService().createListing(player, item, price, currency);
        } catch (Exception ex) {
            // DB or other unexpected error – always return the item
            ListingService.give(player, item);
            player.sendMessage(plugin.msg("sell-error"));
            plugin.getLogger().severe("handleQuickSell createListing failed: " + ex.getMessage());
            return;
        }

        if (id > 0) {
            player.sendMessage(plugin.msg("sell-success"));
        } else {
            // Listing rejected (limit reached, etc.) – return item
            ListingService.give(player, item);
            player.sendMessage(plugin.msg("sell-limit"));
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player p)) return List.of();
        if (args.length == 1)
            return List.of("sell", "mine", "deliveries", "shop", "reload").stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        if (args.length == 3 && args[0].equalsIgnoreCase("sell"))
            return plugin.getCurrencyRegistry().getAll().stream()
                    .map(Currency::key)
                    .filter(k -> k.startsWith(args[2].toUpperCase()))
                    .collect(Collectors.toList());
        return List.of();
    }
}
