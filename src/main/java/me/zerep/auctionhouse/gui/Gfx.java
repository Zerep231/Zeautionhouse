package me.zerep.auctionhouse.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * P2.3 Fix – All text goes through Adventure API (Component / LegacyComponentSerializer)
 * instead of the deprecated ChatColor / meta.setDisplayName(String) path.
 */
public final class Gfx {
    private Gfx() {}

    private static final LegacyComponentSerializer LEGACY =
            LegacyComponentSerializer.legacyAmpersand();

    public static ItemStack item(Material material, String name, String... lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            if (name != null) meta.displayName(LEGACY.deserialize(name));
            if (lore != null && lore.length > 0) {
                List<Component> lines = new ArrayList<>();
                for (String s : lore) lines.add(LEGACY.deserialize(s));
                meta.lore(lines);
            }
            stack.setItemMeta(meta);
        }
        return stack;
    }

    public static ItemStack filler() {
        return item(Material.GRAY_STAINED_GLASS_PANE, " ");
    }

    public static ItemStack green(String label) {
        return item(Material.GREEN_STAINED_GLASS_PANE, "&a" + label);
    }

    public static ItemStack red(String label) {
        return item(Material.RED_STAINED_GLASS_PANE, "&c" + label);
    }

    /** Convert & colour codes → plain string (for legacy compat). */
    public static String color(String s) {
        return s == null ? "" : s.replace("&", "§");
    }

    /** Deserialize MiniMessage into a Component (for titles). */
    public static Component mm(String miniMessage) {
        return MiniMessage.miniMessage().deserialize(miniMessage);
    }
}
