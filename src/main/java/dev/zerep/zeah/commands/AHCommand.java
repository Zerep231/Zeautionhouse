package dev.zerep.zeah.commands;

import dev.zerep.zeah.ZeAuctionHouse;
import dev.zerep.zeah.gui.MainAuctionGUI;
import dev.zerep.zeah.gui.MainMenuGUI;
import dev.zerep.zeah.gui.MyListingsGUI;
import dev.zerep.zeah.gui.SellGUI;
import dev.zerep.zeah.gui.ShopGUI;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class AHCommand implements CommandExecutor, TabCompleter {

    private final ZeAuctionHouse plugin;
    private final java.util.Map<java.util.UUID, Long> cooldowns = new java.util.concurrent.ConcurrentHashMap<>();

    public AHCommand(ZeAuctionHouse plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.getLang().format("player-only")); return true;
        }
        
        long now = System.currentTimeMillis();
        long lastCmd = cooldowns.getOrDefault(player.getUniqueId(), 0L);
        if (now - lastCmd < 500 && !player.hasPermission("zeah.admin")) {
            player.sendMessage("§cPlease wait a moment before using commands again.");
            return true;
        }
        cooldowns.put(player.getUniqueId(), now);

        if (!player.hasPermission("zeah.use")) {
            player.sendMessage(plugin.getLang().format("no-permission")); return true;
        }

        if (args.length == 0) {
            new MainMenuGUI(plugin, player).open();
            return true;
        }

        switch (args[0].toLowerCase()) {

            case "browse" -> {
                if (!player.hasPermission("zeah.browse")) {
                    player.sendMessage(plugin.getLang().format("no-permission")); return true;
                }
                int page = 0;
                if (args.length >= 2) {
                    try { page = Math.max(0, Integer.parseInt(args[1]) - 1); }
                    catch (NumberFormatException ignored) {}
                }
                new MainAuctionGUI(plugin, player, page).open();
            }

            case "sell" -> {
                if (!player.hasPermission("zeah.sell")) {
                    player.sendMessage(plugin.getLang().format("no-permission")); return true;
                }
                new SellGUI(plugin, player).open();
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

            case "mylistings", "my", "list" -> {
                if (!player.hasPermission("zeah.mylistings")) {
                    player.sendMessage(plugin.getLang().format("no-permission")); return true;
                }
                new MyListingsGUI(plugin, player).open();
            }

            case "reload" -> {
                if (!player.hasPermission("zeah.admin")) {
                    player.sendMessage(plugin.getLang().format("no-permission")); return true;
                }
                plugin.reload();
                player.sendMessage(plugin.getLang().format("plugin-reload"));
            }

            default -> new MainMenuGUI(plugin, player).open();
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command cmd,
                                      @NotNull String label, @NotNull String[] args) {
        if (args.length == 1)
            return List.of("browse", "sell", "claim", "shop", "mylistings", "reload");
        return List.of();
    }
}
