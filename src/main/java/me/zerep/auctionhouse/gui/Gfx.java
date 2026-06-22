package me.zerep.auctionhouse.gui;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public final class Gfx {
    private Gfx() {}

    public static ItemStack item(Material material, String name, String... lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            if (name != null) meta.setDisplayName(color(name));
            if (lore != null && lore.length > 0) {
                List<String> lines = new ArrayList<>();
                for (String s : lore) lines.add(color(s));
                meta.setLore(lines);
            }
            stack.setItemMeta(meta);
        }
        return stack;
    }

    public static ItemStack filler() { return item(Material.GRAY_STAINED_GLASS_PANE, " "); }
    public static ItemStack green() { return item(Material.GREEN_STAINED_GLASS_PANE, "&aConfirm"); }
    public static ItemStack red() { return item(Material.RED_STAINED_GLASS_PANE, "&cCancel"); }

    public static String color(String s) { return s == null ? "" : s.replace("&", "§"); }
}
