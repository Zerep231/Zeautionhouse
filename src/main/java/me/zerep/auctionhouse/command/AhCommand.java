package me.zerep.auctionhouse.command;

import me.zerep.auctionhouse.AuctionHousePlugin;
import me.zerep.auctionhouse.gui.ShopGui;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;

public class AhCommand implements CommandExecutor, TabCompleter {
    private final AuctionHousePlugin plugin;

    public AhCommand(AuctionHousePlugin plugin) {
        this.plugin = plugin;
    }

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
            case "sell", "create" -> plugin.getGuiManager().openCreate(player);
            case "mine", "my" -> plugin.getGuiManager().openMine(player, 0);
            case "claim", "deliveries", "box" -> plugin.getGuiManager().openDelivery(player, 0);
            case "shop" -> new ShopGui(plugin).openHome(player);
            case "reload" -> {
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

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return List.of("sell", "mine", "deliveries", "shop", "reload");
        return List.of();
    }
}
