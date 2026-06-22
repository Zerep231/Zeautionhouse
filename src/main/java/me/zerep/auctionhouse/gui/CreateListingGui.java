package me.zerep.auctionhouse.gui;

import me.zerep.auctionhouse.AuctionHousePlugin;
import me.zerep.auctionhouse.currency.Currency;
import me.zerep.auctionhouse.session.CreateSession;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * P0.3 + P0.4 Fix – Step 1 only shows an empty input slot; the real item is
 * captured into SessionManager the moment the player clicks Next (done in
 * GuiListener).  Step 2 reads from the session and shows only a clone of the
 * item so the GUI itself never holds the real item.
 */
public class CreateListingGui {

    public static final int INPUT_SLOT  = 13;   // step 1 – where player places item
    public static final int NEXT_SLOT   = 24;   // step 1 – proceed button
    public static final int CANCEL_SLOT = 20;   // step 1 – cancel button

    // Step 2 slots
    public static final int CONFIRM_SLOT = 24;
    public static final int BACK_SLOT    = 20;
    public static final int PRICE_SLOT   = 32;
    public static final int PRICE_MINUS10 = 30;
    public static final int PRICE_MINUS1  = 31;
    public static final int PRICE_PLUS1   = 33;
    public static final int PRICE_PLUS10  = 34;
    // Currency selectors start at slot 19
    public static final int CURRENCY_START = 19;

    private final AuctionHousePlugin plugin;

    public CreateListingGui(AuctionHousePlugin plugin) { this.plugin = plugin; }

    /** Step 1 – player places item in the center slot. */
    public void openStep1(Player player) {
        Inventory inv = Bukkit.createInventory(
                new AhHolder(GuiTag.CREATE_1, "create1"), 27,
                Gfx.color("&6Create Listing &7– Place Item"));

        for (int i = 0; i < 27; i++) inv.setItem(i, Gfx.filler());
        inv.setItem(4, Gfx.item(Material.PAPER,
                "&fPlace item in the &ycenter slot",
                "&7Then click &aNext \u2192"));
        inv.setItem(INPUT_SLOT, null);          // keep empty for player to fill
        inv.setItem(CANCEL_SLOT, Gfx.red("Cancel"));
        inv.setItem(NEXT_SLOT,   Gfx.green("Next →"));
        player.openInventory(inv);
    }

    /**
     * Step 2 – price & currency selection.
     * Shows only a CLONE of the real item (which is already in the session).
     */
    public void openStep2(Player player, CreateSession session) {
        ItemStack display = session.getItem().clone();

        Inventory inv = Bukkit.createInventory(
                new AhHolder(GuiTag.CREATE_2, "create2"), 54,
                Gfx.color("&6Set Price & Currency"));

        for (int i = 0; i < 54; i++) inv.setItem(i, Gfx.filler());

        // Item preview (clone – safe)
        inv.setItem(4, display);

        // Currency selector row
        List<Currency> currencies = plugin.getCurrencyRegistry().getAll();
        int slot = CURRENCY_START;
        for (Currency c : currencies) {
            if (slot > 25) break;
            boolean selected = c.key().equals(session.getCurrency());
            inv.setItem(slot++, Gfx.item(c.material(),
                    (selected ? "&a&l" : "&7") + c.displayName(),
                    selected ? "&aSelected" : "&7Click to select"));
        }

        // Price controls
        inv.setItem(PRICE_MINUS10, Gfx.item(Material.RED_DYE,   "&c- 10",   "&7Decrease price by 10"));
        inv.setItem(PRICE_MINUS1,  Gfx.item(Material.RED_DYE,   "&c- 1",    "&7Decrease price by 1"));
        inv.setItem(PRICE_SLOT,    Gfx.item(Material.PAPER,
                "&fPrice: &e" + session.getPrice(),
                "&7Currency: &b" + plugin.getCurrencyRegistry().displayName(session.getCurrency())));
        inv.setItem(PRICE_PLUS1,   Gfx.item(Material.LIME_DYE,  "&a+ 1",    "&7Increase price by 1"));
        inv.setItem(PRICE_PLUS10,  Gfx.item(Material.LIME_DYE,  "&a+ 10",   "&7Increase price by 10"));

        inv.setItem(BACK_SLOT,    Gfx.red("← Back"));
        inv.setItem(CONFIRM_SLOT, Gfx.green("✔ Confirm & List"));

        player.openInventory(inv);
    }
}
