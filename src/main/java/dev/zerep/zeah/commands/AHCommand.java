package dev.zerep.zeah.commands;

import dev.zerep.zeah.ZeAuctionHouse;
import dev.zerep.zeah.gui.MainAuctionGUI;
import dev.zerep.zeah.gui.ShopGUI;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;

public class AHCommand implements CommandExecutor, TabCompleter {

    private final ZeAuctionHouse plugin;

    public AHCommand(ZeAuctionHouse plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.getLang().format("player-only")); return true;
        }
        if (!player.hasPermission("zeah.use")) {
            player.sendMessage(plugin.getLang().format("no-permission")); return true;
        }

        if (args.length == 0 || args[0].equalsIgnoreCase("browse")) {
            int page = 0;
            if (args.length >= 2) {
                try { page = Math.max(0, Integer.parseInt(args[1]) - 1); }
                catch (NumberFormatException ignored) {}
            }
            new MainAuctionGUI(plugin, player, page).open();
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "sell" -> {
                if (!player.hasPermission("zeah.sell")) {
                    player.sendMessage(plugin.getLang().format("no-permission")); return true;
                }
                if (args.length < 2) {
                    player.sendMessage(plugin.getLang().format("auction.usage")); return true;
                }
                int price;
                try {
                    price = Integer.parseInt(args[1]);
                    if (price <= 0) throw new NumberFormatException();
                } catch (NumberFormatException e) {
                    player.sendMessage(plugin.getLang().format("invalid-number", "input", args[1])); return true;
                }
                plugin.getAuctionManager().startSell(player, price);
            }
            case "claim" -> {
                if (!player.hasPermission("zeah.claim")) {
                    player.sendMessage(plugin.getLang().format("no-permission")); return true;
                }
                plugin.getDeliveryManager().claimAll(player);
            }
            case "shop" -> {
                if (!plugin.getConfig().getBoolean("shop.enabled", true)) {
                    player.sendMessage(plugin.getLang().format("shop.not-enabled")); return true;
                }
                if (!player.hasPermission("zeah.shop")) {
                    player.sendMessage(plugin.getLang().format("no-permission")); return true;
                }
                new ShopGUI(plugin, player).open();
            }
            case "mylistings", "my", "list" -> new dev.zerep.zeah.gui.MyListingsGUI(plugin, player).open();
            case "reload" -> {
                if (!player.hasPermission("zeah.admin")) {
                    player.sendMessage(plugin.getLang().format("no-permission")); return true;
                }
                plugin.reload();
                player.sendMessage(plugin.getLang().format("plugin-reload"));
            }
            default -> player.sendMessage(plugin.getLang().format("auction.usage"));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command cmd,
                                      @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            return Arrays.asList("browse", "sell", "claim", "shop", "mylistings", "reload");
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("sell")) {
            return List.of("<price>");
        }
        return List.of();
    }
}
