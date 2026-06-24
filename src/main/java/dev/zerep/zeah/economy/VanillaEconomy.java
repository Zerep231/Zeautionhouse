package dev.zerep.zeah.economy;

import dev.zerep.zeah.ZeAuctionHouse;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Vanilla item-based economy — no Vault required.
 * The "currency" is a configurable physical item (default: DIAMOND).
 */
public class VanillaEconomy {

    private final ZeAuctionHouse plugin;

    public VanillaEconomy(ZeAuctionHouse plugin) {
        this.plugin = plugin;
    }

    public Material getCurrencyMaterial() {
        String mat = plugin.getConfig().getString("economy.currency-item", "DIAMOND");
        try { return Material.valueOf(mat.toUpperCase()); }
        catch (IllegalArgumentException e) { return Material.DIAMOND; }
    }

    public String getCurrencyName() {
        return plugin.getConfig().getString("economy.currency-name", "Diamond");
    }

    public String format(int amount) {
        return String.format("%,dx %s", amount, getCurrencyName());
    }

    public int getBalance(Player player) {
        Material currency = getCurrencyMaterial();
        int count = 0;
        for (ItemStack is : player.getInventory().getContents()) {
            if (is != null && is.getType() == currency) count += is.getAmount();
        }
        return count;
    }

    public boolean has(Player player, int amount) {
        return getBalance(player) >= amount;
    }

    public boolean withdraw(Player player, int amount) {
        if (!has(player, amount)) return false;
        Material currency = getCurrencyMaterial();
        ItemStack[] contents = player.getInventory().getContents();
        int remaining = amount;
        for (int i = 0; i < contents.length && remaining > 0; i++) {
            ItemStack is = contents[i];
            if (is == null || is.getType() != currency) continue;
            int take = Math.min(is.getAmount(), remaining);
            if (take >= is.getAmount()) contents[i] = null;
            else is.setAmount(is.getAmount() - take);
            remaining -= take;
        }
        player.getInventory().setContents(contents);
        return true;
    }

    public void deposit(Player player, int amount) {
        Material currency = getCurrencyMaterial();
        int maxStack = currency.getMaxStackSize();
        int remaining = amount;
        while (remaining > 0) {
            int give = Math.min(remaining, maxStack);
            var overflow = player.getInventory().addItem(new ItemStack(currency, give));
            overflow.values().forEach(is -> player.getWorld().dropItemNaturally(player.getLocation(), is));
            remaining -= give;
        }
    }

    /** Create multiple stacks representing {@code amount} total currency items. */
    public ItemStack[] createCurrencyStacks(int amount) {
        Material currency = getCurrencyMaterial();
        int maxStack = currency.getMaxStackSize();
        int stacks = (int) Math.ceil((double) amount / maxStack);
        ItemStack[] result = new ItemStack[stacks];
        int remaining = amount;
        for (int i = 0; i < stacks; i++) {
            int give = Math.min(remaining, maxStack);
            result[i] = new ItemStack(currency, give);
            remaining -= give;
        }
        return result;
    }
}
